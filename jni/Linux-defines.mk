MACHINE_LDFLAGS=-shared -L/lib/x86_64-linux-gnu/ -pthread -Wl,--no-as-needed -Wl,-rpath='$$ORIGIN/../clang-all/build/Release+Asserts/lib'
MACHINE_CFLAGS=-fPIC
MACHINE_INCLUDE=-I$(JAVA_HOME)/include/linux/
ACHINE_LIBS=
MACHINE_EXECUTABLE=$(OBJDIR)/libUnsafeDriver.so
