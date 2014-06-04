MACHINE:=$(shell uname)
include $(MACHINE)-defines.mk
CC=g++
SOURCES=Driver.cpp NativeModule.cpp
CLANG_ROOT=../clang-all
CLANG_BUILD=$(CLANG_ROOT)/build
CLANG_LIB=$(CLANG_BUILD)/Release+Asserts/lib
CLANG_BIN=$(CLANG_BUILD)/Release+Asserts/bin
CFLAGS=$(MACHINE_CFLAGS) -c -std=c++11 -D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS -D_GNU_SOURCE -D__STDC_FORMAT_MACROS -O3
LDFLAGS=$(MACHINE_LDFLAGS)
INCLUDE=$(MACHINE_INCLUDE) -I$(JAVA_HOME)/include/ -I$(CLANG_ROOT)/llvm/tools/clang/include -I$(CLANG_ROOT)/llvm/include -I$(CLANG_BUILD)/include -I$(CLANG_BUILD)/tools/clang/include
LIBS=-lz -lcurses $(shell $(CLANG_BIN)/llvm-config --ldflags --libs) -lclang -lclangARCMigrate -lclangAST -lclangASTMatchers -lclangAnalysis -lclangApplyReplacements -lclangBasic -lclangCodeGen -lclangDriver -lclangDynamicASTMatchers -lclangEdit -lclangFormat -lclangFrontend -lclangFrontendTool -lclangIndex -lclangLex -lclangParse -lclangQuery -lclangRewriteCore -lclangRewriteFrontend -lclangSema -lclangSerialization -lclangStaticAnalyzerCheckers -lclangStaticAnalyzerCore -lclangStaticAnalyzerFrontend -lclangTidy -lclangTidyGoogleModule -lclangTidyLLVMModule -lclangTidyMiscModule -lclangTooling
OBJDIR=.
OBJECTS=$(addprefix $(OBJDIR)/,$(SOURCES:.cpp=.o))

all: $(SOURCES) $(MACHINE_EXECUTABLE)

$(OBJDIR)/%.o : %.cpp
	$(CC) $(CFLAGS) $(INCLUDE) $< -o $@

$(MACHINE_EXECUTABLE): $(OBJECTS)
	$(CC) $(LDFLAGS) $(LIBS) $(OBJECTS) -o $@

clean:
	rm -f $(OBJECTS) $(MACHINE_EXECUTABLE)