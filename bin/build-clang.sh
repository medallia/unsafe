#!/bin/bash

# Stop on any unhandled error
set -e

#Define common variables
PROJECT_ROOT="$PWD/$(dirname $0)/.."
. "$PROJECT_ROOT/bin/config.sh"

# Enter build dir
mkdir "$CLANG_BUILD" || echo "$CLANG_BUILD exists"
cd "$CLANG_BUILD"

# Configure
cmake "$CLANG_ROOT/llvm"

# Make it
cmake --build .