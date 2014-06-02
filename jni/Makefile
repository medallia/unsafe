MACHINE:=$(shell uname)
include $(MACHINE)-defines.mk
CC=g++
SOURCES=Driver.cpp NativeModule.cpp
CLANG_ROOT=../clang-all
CLANG_BUILD=$(CLANG_ROOT)/build
CFLAGS=-c -std=c++11 -stdlib=libc++ -D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS -D_GNU_SOURCE -D__STDC_FORMAT_MACROS -O3
LDFLAGS=$(MACHINE_LDFLAGS) -dynamiclib -L$(CLANG_BUILD)/Release+Asserts/lib
INCLUDE=$(MACHINE_INCLUDE) -I$(JAVA_HOME)/include/ -I$(CLANG_ROOT)/llvm/tools/clang/include -I$(CLANG_ROOT)/llvm/include -I$(CLANG_BUILD)/include -I$(CLANG_BUILD)/tools/clang/include
LIBS=-lz -lcurses -lLLVMAArch64AsmParser -lLLVMAArch64AsmPrinter -lLLVMAArch64CodeGen -lLLVMAArch64Desc -lLLVMAArch64Disassembler -lLLVMAArch64Info -lLLVMAArch64Utils -lLLVMARM64AsmParser -lLLVMARM64AsmPrinter -lLLVMARM64CodeGen -lLLVMARM64Desc -lLLVMARM64Disassembler -lLLVMARM64Info -lLLVMARM64Utils -lLLVMARMAsmParser -lLLVMARMAsmPrinter -lLLVMARMCodeGen -lLLVMARMDesc -lLLVMARMDisassembler -lLLVMARMInfo -lLLVMAnalysis -lLLVMAsmParser -lLLVMAsmPrinter -lLLVMBitReader -lLLVMBitWriter -lLLVMCodeGen -lLLVMCore -lLLVMCppBackendCodeGen -lLLVMCppBackendInfo -lLLVMDebugInfo -lLLVMExecutionEngine -lLLVMHexagonAsmPrinter -lLLVMHexagonCodeGen -lLLVMHexagonDesc -lLLVMHexagonInfo -lLLVMIRReader -lLLVMInstCombine -lLLVMInstrumentation -lLLVMInterpreter -lLLVMJIT -lLLVMLTO -lLLVMLineEditor -lLLVMLinker -lLLVMMC -lLLVMMCDisassembler -lLLVMMCJIT -lLLVMMCParser -lLLVMMSP430AsmPrinter -lLLVMMSP430CodeGen -lLLVMMSP430Desc -lLLVMMSP430Info -lLLVMMipsAsmParser -lLLVMMipsAsmPrinter -lLLVMMipsCodeGen -lLLVMMipsDesc -lLLVMMipsDisassembler -lLLVMMipsInfo -lLLVMNVPTXAsmPrinter -lLLVMNVPTXCodeGen -lLLVMNVPTXDesc -lLLVMNVPTXInfo -lLLVMObjCARCOpts -lLLVMObject -lLLVMOption -lLLVMPowerPCAsmParser -lLLVMPowerPCAsmPrinter -lLLVMPowerPCCodeGen -lLLVMPowerPCDesc -lLLVMPowerPCDisassembler -lLLVMPowerPCInfo -lLLVMProfileData -lLLVMR600AsmPrinter -lLLVMR600CodeGen -lLLVMR600Desc -lLLVMR600Info -lLLVMRuntimeDyld -lLLVMScalarOpts -lLLVMSelectionDAG -lLLVMSparcAsmParser -lLLVMSparcAsmPrinter -lLLVMSparcCodeGen -lLLVMSparcDesc -lLLVMSparcDisassembler -lLLVMSparcInfo -lLLVMSupport -lLLVMSystemZAsmParser -lLLVMSystemZAsmPrinter -lLLVMSystemZCodeGen -lLLVMSystemZDesc -lLLVMSystemZDisassembler -lLLVMSystemZInfo -lLLVMTableGen -lLLVMTarget -lLLVMTransformUtils -lLLVMVectorize -lLLVMX86AsmParser -lLLVMX86AsmPrinter -lLLVMX86CodeGen -lLLVMX86Desc -lLLVMX86Disassembler -lLLVMX86Info -lLLVMX86Utils -lLLVMXCoreAsmPrinter -lLLVMXCoreCodeGen -lLLVMXCoreDesc -lLLVMXCoreDisassembler -lLLVMXCoreInfo -lLLVMipa -lLLVMipo -lLTO -lclang -lclangARCMigrate -lclangAST -lclangASTMatchers -lclangAnalysis -lclangApplyReplacements -lclangBasic -lclangCodeGen -lclangDriver -lclangDynamicASTMatchers -lclangEdit -lclangFormat -lclangFrontend -lclangFrontendTool -lclangIndex -lclangLex -lclangParse -lclangQuery -lclangRewriteCore -lclangRewriteFrontend -lclangSema -lclangSerialization -lclangStaticAnalyzerCheckers -lclangStaticAnalyzerCore -lclangStaticAnalyzerFrontend -lclangTidy -lclangTidyGoogleModule -lclangTidyLLVMModule -lclangTidyMiscModule -lclangTooling -lgtest -lgtest_main -lmodernizeCore
OBJDIR=.
OBJECTS=$(addprefix $(OBJDIR)/,$(SOURCES:.cpp=.o))

all: $(SOURCES) $(MACHINE_EXECUTABLE)

$(OBJDIR)/%.o : %.cpp
	$(CC) $(CFLAGS) $(INCLUDE) $< -o $@

$(MACHINE_EXECUTABLE): $(OBJECTS)
	$(CC) $(LDFLAGS) $(LIBS) $(OBJECTS) -o $@

clean:
	rm -f $(OBJECTS) $(MACHINE_EXECUTABLE)