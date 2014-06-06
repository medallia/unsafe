#include <stdio.h>
#include <stdlib.h>
#include <immintrin.h>
#include <vector>
#include <functional>
#include <cstdlib>
#include <cstdio>
#include <iostream>
#include <chrono>
#include <ctime>
#include <stdint.h>
#include <random>
#include <cassert>

using namespace std;

static const int WARMUP = 20000;

static const int TIMES = 500000;

int countNormal(float * data, int len) {
	int count = 0;
	for(int i = 0; i < len; ++i)
		if (data[i] >= 0.0)
			count++;
	return count;
}

int countSSE(float *data, int len) {
	assert(len % 4 == 0);

	int loops = len / 4;

	const __m128 zero = _mm_setzero_ps(); // set to [0,0,0,0]
	__m128i ones = _mm_set_epi32(1,1,1,1); // set to [1,1,1,1]
	__m128i counts = _mm_setzero_si128(); // accumulating counts
	for(int i = 0; i < loops; i++) {
		__m128 vec = _mm_loadu_ps(&data[i*4]); // load data from memory
		__m128i mask = (__m128i) _mm_cmpgt_ps(vec, zero); // create mask
		__m128i anded = _mm_and_si128(mask, ones); // and mask with [1,1,1,1]
		counts = _mm_add_epi32(counts, anded); // add result to counts
	}

	// Horizontal add counts and return
	__m128i out;
	_mm_store_si128(&out, counts);
	int *ptr = (int *)&out;
	return ptr[0] + ptr[1] + ptr[2] + ptr[3];
}


// Not faster -- delay for cmp to finish.
int countSSEinv(float *data, int len) {
	assert(len % 8 == 0);

	int loops = len / 4;

	__m128i counts = _mm_setzero_si128();
	for(int i = 0; i < loops; i+=2) {
		{
			__m128 vec = _mm_loadu_ps(&data[i*4]);
			__m128i mask = (__m128i) _mm_cmpgt_ps(vec, _mm_setzero_ps());
			counts = _mm_add_epi32(counts, mask);
		}

		{
			__m128 vec = _mm_loadu_ps(&data[(i+1)*4]);
			__m128i mask = (__m128i) _mm_cmpgt_ps(vec, _mm_setzero_ps());
			counts = _mm_add_epi32(counts, mask);
		}
	}
	__m128i out;
	_mm_store_si128(&out, counts);
	int *ptr = (int *)&out;
	return -(ptr[0] + ptr[1] + ptr[2] + ptr[3]);
}

int countSSEp(float *data, int len) {
	assert(len % 8 == 0);

	int loops = len / 4;

	__m128i countsA = _mm_setzero_si128();
	__m128i countsB = _mm_setzero_si128();
	for(int i = 0; i < loops; i+=2) {
			__m128i maskA = (__m128i) _mm_cmpgt_ps(_mm_loadu_ps(&data[i*4]), _mm_setzero_ps());
			__m128i maskB = (__m128i) _mm_cmpgt_ps(_mm_loadu_ps(&data[(i+1)*4]), _mm_setzero_ps());
			countsA = _mm_add_epi32(countsA, maskA);
			countsB = _mm_add_epi32(countsB, maskB);
	}
	__m128i out;
	_mm_store_si128(&out, _mm_add_epi32(countsA, countsB));
	int *ptr = (int *)&out;
	return -(ptr[0] + ptr[1] + ptr[2] + ptr[3]);
}

#ifdef __AVX2__
int countAVX(float *data, int len) {
	assert(len % 8 == 0);

	int loops = len / 8;

	__m256i countsA = _mm256_setzero_si256();
	__m256i countsB = _mm256_setzero_si256();
	for(int i = 0; i < loops; i+=2) {
			__m256i maskA = (__m256i) _mm256_cmp_ps(_mm256_loadu_ps(&data[i*8]), _mm256_setzero_ps(), _CMP_GE_OS);
			__m256i maskB = (__m256i) _mm256_cmp_ps(_mm256_loadu_ps(&data[(i+1)*8]), _mm256_setzero_ps(), _CMP_GE_OS);
			countsA = _mm256_add_epi32(countsA, maskA);
			countsB = _mm256_add_epi32(countsB, maskB);
	}
	__m256i out;
	_mm256_store_si256(&out, _mm256_add_epi32(countsA, countsB));
	int *ptr = (int *)&out;
	return -(ptr[0] + ptr[1] + ptr[2] + ptr[3] + ptr[4] + ptr[5] + ptr[6] + ptr[7]);
}
#endif

void benchmark(const std::string &str, function<int(float*, int)> call, vector<float> data) {
        long long int count = 0;
        for (int i = 0; i < WARMUP; ++i)
                count += call(data.data(), data.size());

        auto start = std::chrono::system_clock::now();

        for (int i = 0; i < TIMES; ++i)
                count += call(data.data(), data.size());
        auto end = std::chrono::system_clock::now();
        std::chrono::duration<double> elapsed_seconds = end - start;

        cout << str << ": " << count << " " << elapsed_seconds.count() << endl;
}

int main(void) {
	vector<float> data;

	std::default_random_engine generator;
	std::uniform_real_distribution<float> distribution(-1.0, 1.0);

	for(int i = 0; i < 2000; ++i)
		data.push_back(distribution(generator));

	benchmark("normal", [](float *input, int len) { return countNormal(input, len); }, data);

	benchmark("sse", [](float *input, int len) { return countSSE(input, len); }, data);
	benchmark("ssei", [](float *input, int len) { return countSSEinv(input, len); }, data);
	benchmark("ssep", [](float *input, int len) { return countSSEp(input, len); }, data);

#ifdef __AVX2__
	benchmark("avx", [](float *input, int len) { return countAVX(input, len); }, data);
#endif

	return EXIT_SUCCESS;
}
