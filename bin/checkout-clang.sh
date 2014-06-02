#!/bin/bash

# Stop on any unhandled error
set -e

#Define common variables
PROJECT_ROOT="$PWD/$(dirname $0)/.."
. "$PROJECT_ROOT/bin/config.sh"

# Make a directory to hold clang source code
mkdir -p "$CLANG_ROOT"

# Checkout llvm
cd "$CLANG_ROOT"
svn co https://llvm.org/svn/llvm-project/llvm/trunk@$CLANG_REVISION llvm

# Checkout clang
cd "$CLANG_ROOT/llvm/tools"
svn co https://llvm.org/svn/llvm-project/cfe/trunk@$CLANG_REVISION clang

# Checkout clang-tools
cd "$CLANG_ROOT/llvm/tools/clang/tools"
svn co https://llvm.org/svn/llvm-project/clang-tools-extra/trunk@$CLANG_REVISION extra

# Checkout Compiler-RT
cd "$CLANG_ROOT/llvm/projects"
svn co https://llvm.org/svn/llvm-project/compiler-rt/trunk@$CLANG_REVISION compiler-rt

# Make build dir and configure
mkdir -p "$CLANG_BUILD"
cd "$CLANG_BUILD"
../llvm/configure --enable-optimized

