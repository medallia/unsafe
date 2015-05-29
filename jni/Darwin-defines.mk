MACHINE_LDFLAGS=-dynamiclib -rpath @loader_path/../clang-all/build/Release+Asserts/lib
MACHINE_CFLAGS=-stdlib=libc++ 
MACHINE_INCLUDE=-I$(JAVA_HOME)/include/darwin
MACHINE_LIBS=-lz -lcurses
MACHINE_EXECUTABLE=$(OBJDIR)/libUnsafeDriver.dylib
