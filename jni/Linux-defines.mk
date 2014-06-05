MACHINE_LDFLAGS=-shared -L/lib/x86_64-linux-gnu/ -pthread -Wl,--no-as-needed
MACHINE_CFLAGS=-fPIC
MACHINE_INCLUDE=
MACHINE_EXECUTABLE=$(OBJDIR)/libUnsafeDriver.so
