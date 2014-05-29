#include "NativeModule.h"
#include <clang/Driver/Driver.h>
#include <clang/Driver/Job.h>
#include <clang/Driver/Tool.h>
#include <clang/Driver/Compilation.h>

#include <clang/Frontend/VerifyDiagnosticConsumer.h>

#include <clang/Basic/DiagnosticOptions.h>
#include <clang/Frontend/TextDiagnosticPrinter.h>
#include <llvm/ADT/IntrusiveRefCntPtr.h>

#include <clang/Frontend/CompilerInstance.h>
#include <clang/Frontend/CompilerInvocation.h>

#include <llvm/ADT/OwningPtr.h>

#include <llvm/Config/config.h>
#include <llvm/Support/TargetSelect.h>
#include <clang/Basic/TargetInfo.h>
#include <llvm/Support/Host.h>
#include <llvm/Option/ArgList.h>

#include <clang/AST/ASTContext.h>

#include <llvm/Analysis/Passes.h>
#include <llvm/IR/DataLayout.h>
#include <llvm/IR/DerivedTypes.h>
#include <llvm/IR/IRBuilder.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/IR/Verifier.h>
#include <llvm/PassManager.h>
#include <llvm/LinkAllPasses.h>
#include <llvm/ExecutionEngine/JIT.h>
#include <llvm/Transforms/IPO/PassManagerBuilder.h>
#include <llvm/Support/TargetRegistry.h>
#include <llvm/Target/TargetLibraryInfo.h>
#include <llvm/ExecutionEngine/GenericValue.h>

#include <chrono>

using namespace clang;

NativeModule::NativeModule(std::string _fileName, std::string _sourceCode, std::vector<std::string> _compilerArgs) :
fileName(_fileName),
sourceCode(_sourceCode),
compilerArgs(_compilerArgs){
    const std::chrono::time_point<std::chrono::steady_clock> t0 = std::chrono::high_resolution_clock::now();
    
    llvm::InitializeNativeTarget();
    
	// Arguments to pass to the clang frontend
    std::vector<const char *> args;
    for (std::string arg : compilerArgs) {
        args.push_back(arg.c_str());
    }
    
    // We'll fake the contents of this file later
	args.push_back(fileName.c_str());
    
	// The compiler invocation needs a DiagnosticsEngine so it can report problems
    llvm::IntrusiveRefCntPtr<clang::DiagnosticOptions> DiagOpts(new clang::DiagnosticOptions());
	clang::TextDiagnosticPrinter *DiagClient = new clang::TextDiagnosticPrinter(llvm::errs(), DiagOpts.getPtr());
    llvm::IntrusiveRefCntPtr<clang::DiagnosticIDs> DiagIDs(new DiagnosticIDs());
    llvm::IntrusiveRefCntPtr<DiagnosticsEngine>	Diags(new DiagnosticsEngine(DiagIDs, DiagOpts.getPtr(), DiagClient, /*Owns it*/ true));
    
	// Create the compiler invocation
	llvm::IntrusiveRefCntPtr<clang::CompilerInvocation> CI(new clang::CompilerInvocation);
	clang::CompilerInvocation::CreateFromArgs(*CI, &args[0], &args[0] + args.size(), *Diags);
    
	// Create the compiler instance
	clang::CompilerInstance Clang;
	Clang.setInvocation(CI.getPtr());
    
	// Get ready to report problems
	Clang.createDiagnostics(DiagClient, false);
	if (!Clang.hasDiagnostics())
		return;
    
    // Set up source and file managers
    Clang.createFileManager();
    llvm::IntrusiveRefCntPtr<SourceManager> SM(new SourceManager(Clang.getDiagnostics(),
                                          Clang.getFileManager(),
                                          /*UserFilesAreVolatile*/ true));
    Clang.setSourceManager(SM.getPtr());

    // Create the file manager
    FileManager& FM = SM->getFileManager();
    
    // Build the virtual file
    
    const FileEntry* FE = FM.getVirtualFile(fileName, fileName.length(), time(0));
    FileID MainFileID = SM->createMainFileID(FE, SrcMgr::C_User);
    
    const SrcMgr::SLocEntry& MainFileSLocE = SM->getSLocEntry(MainFileID);
    const SrcMgr::ContentCache* MainFileCC = MainFileSLocE.getFile().getContentCache();
    
    llvm::MemoryBuffer * buffer = llvm::MemoryBuffer::getMemBuffer(sourceCode);
    const_cast<SrcMgr::ContentCache*>(MainFileCC)->setBuffer(buffer);
    
	// Create an action and make the compiler instance carry it out
	llvm::OwningPtr<clang::CodeGenAction> codeGenAction(new clang::EmitLLVMOnlyAction(&context));
	if (!Clang.ExecuteAction(*codeGenAction))
		return;
    
	// Grab the module built by the EmitLLVMOnlyAction (will be owned by the execution engine)
	module = codeGenAction->takeModule();
    
    const std::chrono::time_point<std::chrono::steady_clock> t1 = std::chrono::high_resolution_clock::now();
    fprintf(stderr, "- compile %fs\n", (t1 - t0).count()/1e9);
    
    std::string ErrStr;
    executionEngine.reset(llvm::EngineBuilder(module)
    .setErrorStr(&ErrStr)
    .setUseMCJIT(true)
    .setOptLevel(llvm::CodeGenOpt::Aggressive)
    .create());
    if (!executionEngine) {
        fprintf(stderr, "Cannot create execution engine: %s.\n", ErrStr.c_str());
        return; //FIXME: should throw an exception probably
    }
    
    // Create a PassManager to hold and optimize the collection of passes we are
    // about to build.
    //
    llvm::PassManager Passes;
    
    // Do module-level data layout
    Passes.add(new llvm::DataLayoutPass(module));
    
    // Create a function pass manager for this engine
    llvm::FunctionPassManager FPM(module);
    
    // Set up the optimizer pipeline.  Start with registering info about how the
    // target lays out data structures.
    FPM.add(new llvm::DataLayoutPass(module));
    
    // Setup C/C++ stadard optimizations
    llvm::PassManagerBuilder Builder;
    
    Builder.OptLevel = 3;
    Builder.SizeLevel = 0;
    Builder.RerollLoops = true;
    Builder.LoopVectorize = true;
    Builder.BBVectorize = true;
    Builder.SLPVectorize = true;
    Builder.Inliner = llvm::createFunctionInliningPass(Builder.OptLevel, Builder.SizeLevel);
    Builder.LibraryInfo = new llvm::TargetLibraryInfo(llvm::Triple(module->getTargetTriple()));
    
    Builder.populateFunctionPassManager(FPM);
    Builder.populateModulePassManager(Passes);
    
    // Add target specific passes
    executionEngine->getTargetMachine()->addAnalysisPasses(FPM);
    executionEngine->getTargetMachine()->addAnalysisPasses(Passes);
    
    const std::chrono::time_point<std::chrono::steady_clock> t2 = std::chrono::high_resolution_clock::now();
    fprintf(stderr, "- build passes %fs\n", (t2 - t1).count()/1e9);
    
    FPM.doInitialization();
    // For each function in the module
    for (llvm::Module::iterator it = module->begin(), E = module->end(); it != E; ++it) {
        fprintf(stderr, "    . optimzing: %s\n", (*it).getName().str().c_str());
        FPM.run(*it);
        functions.push_back(it);
    }
    
    
    FPM.doFinalization();
    
    const std::chrono::time_point<std::chrono::steady_clock> t3 = std::chrono::high_resolution_clock::now();
    fprintf(stderr, "- run function passes  %fs\n", (t3 - t2).count()/1e9);

    // Run module level passes
    Passes.run(*module);
    
    const std::chrono::time_point<std::chrono::steady_clock> t4 = std::chrono::high_resolution_clock::now();
    fprintf(stderr, "- run module passes  %fs\n", (t4 - t3).count()/1e9);
    
    executionEngine->generateCodeForModule(module);
    executionEngine->finalizeObject();
    
    const std::chrono::time_point<std::chrono::steady_clock> t5 = std::chrono::high_resolution_clock::now();
    fprintf(stderr, "- full build %fs\n", (t5 - t0).count()/1e9);
    
    // Call it
    fprintf(stderr, "Done!\n");
}


std::vector<llvm::Function*> NativeModule::getFunctions() const {
    return functions;
}

