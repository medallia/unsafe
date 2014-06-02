#!/bin/bash

# Stop on any unhandled error
set -e

#Define common variables
PROJECT_ROOT="$PWD/$(dirname $0)/.."
. "$PROJECT_ROOT/bin/config.sh"

# Go to build dir
cd "$CLANG_BUILD"
make
