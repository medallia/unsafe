MACHINE:=$(shell uname)
include $(MACHINE)-defines.mk
CC=g++
SOURCES=Driver.cpp NativeModule.cpp
CLANG_ROOT=../clang-all
CLANG_BUILD=$(CLANG_ROOT)/build
CLANG_LIB=$(CLANG_BUILD)/Release+Asserts/lib
CLANG_BIN=$(CLANG_BUILD)/Release+Asserts/bin
CFLAGS=$(MACHINE_CFLAGS) -c -std=c++11 -D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS -D_GNU_SOURCE -D__STDC_FORMAT_MACROS -O3
LDFLAGS=$(MACHINE_LDFLAGS) -L$(CLANG_LIB)
INCLUDE=$(MACHINE_INCLUDE) -I$(JAVA_HOME)/include/ -I$(CLANG_ROOT)/llvm/tools/clang/include -I$(CLANG_ROOT)/llvm/include -I$(CLANG_BUILD)/include -I$(CLANG_BUILD)/tools/clang/include
LIBS=$(MACHINE_LIBS) -ldl -lLLVMDebugInfo -lLLVMInterpreter -lLLVMJIT -lLLVMLineEditor -lLLVMMCDisassembler -lLLVMMCJIT -lLLVMTableGen -lLTO -lclang -lclangApplyReplacements -lclangFrontendTool -lclangQuery -lclangTidyGoogleModule -lclangTidyLLVMModule -lclangTidyMiscModule -lgtest_main -lmodernizeCore -lLLVMExecutionEngine -lLLVMRuntimeDyld -lLLVMLTO -lLLVMXCoreInfo -lLLVMXCoreDisassembler -lLLVMXCoreCodeGen -lLLVMX86Info -lLLVMX86Disassembler -lLLVMX86CodeGen -lLLVMX86AsmParser -lLLVMSystemZInfo -lLLVMSystemZDisassembler -lLLVMSystemZCodeGen -lLLVMSystemZAsmParser -lLLVMSparcInfo -lLLVMSparcDisassembler -lLLVMSparcCodeGen -lLLVMSparcAsmParser -lLLVMR600Info -lLLVMR600CodeGen -lLLVMPowerPCInfo -lLLVMPowerPCDisassembler -lLLVMPowerPCCodeGen -lLLVMPowerPCAsmParser -lLLVMNVPTXInfo -lLLVMNVPTXCodeGen -lLLVMMipsInfo -lLLVMMipsDisassembler -lLLVMMipsCodeGen -lLLVMMipsAsmParser -lLLVMMSP430Info -lLLVMMSP430CodeGen -lLLVMHexagonInfo -lLLVMHexagonCodeGen -lLLVMCppBackendInfo -lLLVMCppBackendCodeGen -lLLVMARMInfo -lLLVMARMDisassembler -lLLVMARMCodeGen -lLLVMARMAsmParser -lLLVMARM64Info -lLLVMARM64Disassembler -lLLVMARM64CodeGen -lLLVMARM64AsmParser -lLLVMAArch64Info -lLLVMAArch64Disassembler -lLLVMAArch64CodeGen -lLLVMAArch64AsmParser -lclangIndex -lclangARCMigrate -lclangCodeGen -lclangRewriteFrontend -lclangDynamicASTMatchers -lclangTidy -lgtest -lLLVMXCoreAsmPrinter -lLLVMXCoreDesc -lLLVMX86AsmPrinter -lLLVMX86Desc -lLLVMSystemZAsmPrinter -lLLVMSystemZDesc -lLLVMSparcDesc -lLLVMR600AsmPrinter -lLLVMR600Desc -lLLVMPowerPCAsmPrinter -lLLVMPowerPCDesc -lLLVMNVPTXDesc -lLLVMMipsDesc -lLLVMMSP430AsmPrinter -lLLVMMSP430Desc -lLLVMHexagonAsmPrinter -lLLVMARMDesc -lLLVMARM64Desc -lLLVMSelectionDAG -lLLVMAsmPrinter -lLLVMAArch64Desc -lclangFormat -lLLVMIRReader -lLLVMLinker -lLLVMBitWriter -lLLVMInstrumentation -lLLVMProfileData -lLLVMObjCARCOpts -lLLVMipo -lclangStaticAnalyzerFrontend -lLLVMX86Utils -lLLVMSparcAsmPrinter -lLLVMNVPTXAsmPrinter -lLLVMMipsAsmPrinter -lLLVMHexagonDesc -lLLVMARMAsmPrinter -lLLVMARM64AsmPrinter -lLLVMCodeGen -lLLVMAArch64AsmPrinter -lclangTooling -lLLVMAsmParser -lLLVMVectorize -lclangStaticAnalyzerCheckers -lLLVMARM64Utils -lLLVMScalarOpts -lLLVMAArch64Utils -lclangFrontend -lclangASTMatchers -lclangStaticAnalyzerCore -lLLVMInstCombine -lclangParse -lclangDriver -lclangSerialization -lclangRewriteCore -lLLVMMCParser -lLLVMOption -lLLVMTransformUtils -lclangSema -lLLVMipa -lclangEdit -lclangAnalysis -lLLVMAnalysis -lclangAST -lLLVMTarget -lclangLex -lclangBasic -lLLVMMC -lLLVMObject -lLLVMBitReader -lLLVMCore -lLLVMSupport -lLLVMX86AsmPrinter
#LIBS=-lz -lcurses $(shell $(CLANG_BIN)/llvm-config --ldflags --libs) -lclang -lclangARCMigrate -lclangAST -lclangASTMatchers -lclangAnalysis -lclangApplyReplacements -lclangBasic -lclangCodeGen -lclangDriver -lclangDynamicASTMatchers -lclangEdit -lclangFormat -lclangFrontend -lclangFrontendTool -lclangIndex -lclangLex -lclangParse -lclangQuery -lclangRewriteCore -lclangRewriteFrontend -lclangSema -lclangSerialization -lclangStaticAnalyzerCheckers -lclangStaticAnalyzerCore -lclangStaticAnalyzerFrontend -lclangTidy -lclangTidyGoogleModule -lclangTidyLLVMModule -lclangTidyMiscModule -lclangTooling
OBJDIR=.
OBJECTS=$(addprefix $(OBJDIR)/,$(SOURCES:.cpp=.o))

all: $(SOURCES) $(MACHINE_EXECUTABLE)

$(OBJDIR)/%.o : %.cpp
	$(CC) $(CFLAGS) $(INCLUDE) $< -o $@

$(MACHINE_EXECUTABLE): $(OBJECTS)
	$(CC) $(LDFLAGS) $(OBJECTS) $(LIBS) -o $@

clean:
	rm -f $(OBJECTS) $(MACHINE_EXECUTABLE)
