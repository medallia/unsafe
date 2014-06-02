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

/** 
 This is essentially the same than PassManagerBuilder::populateLTOPassManager()
 with the exception that it disables the GlobalDCE and internalization passes, which remove functions
 very aggressively, the problem is that it will over-remove functions.
 The work around would be to reference the function from main() so it is reachable.
 */
void populateLTOPassManager(llvm::PassManagerBase &PM) {
    // Add TypeBasedAliasAnalysis before BasicAliasAnalysis so that
    // BasicAliasAnalysis wins if they disagree. This is intended to help
    // support "obvious" type-punning idioms.
    PM.add(llvm::createTypeBasedAliasAnalysisPass());
    PM.add(llvm::createBasicAliasAnalysisPass());
    
    // Propagate constants at call sites into the functions they call.  This
    // opens opportunities for globalopt (and inlining) by substituting function
    // pointers passed as arguments to direct uses of functions.
    PM.add(llvm::createIPSCCPPass());
    
    // Now that we internalized some globals, see if we can hack on them!
    PM.add(llvm::createGlobalOptimizerPass());
    
    // Linking modules together can lead to duplicated global constants, only
    // keep one copy of each constant.
    PM.add(llvm::createConstantMergePass());
    
    // Remove unused arguments from functions.
    PM.add(llvm::createDeadArgEliminationPass());
    
    // Reduce the code after globalopt and ipsccp.  Both can open up significant
    // simplification opportunities, and both can propagate functions through
    // function pointers.  When this happens, we often have to resolve varargs
    // calls, etc, so let instcombine do this.
    PM.add(llvm::createInstructionCombiningPass());
    
    // Inline small functions
    PM.add(llvm::createFunctionInliningPass());
    
    PM.add(llvm::createPruneEHPass());   // Remove dead EH info.
    
    // Optimize globals again if we ran the inliner.
    PM.add(llvm::createGlobalOptimizerPass());
    
    // If we didn't decide to inline a function, check to see if we can
    // transform it to pass arguments by value instead of by reference.
    PM.add(llvm::createArgumentPromotionPass());
    
    // The IPO passes may leave cruft around.  Clean up after them.
    PM.add(llvm::createInstructionCombiningPass());
    PM.add(llvm::createJumpThreadingPass());
    
    // Break up allocas
    PM.add(llvm::createScalarReplAggregatesPass());
    
    // Run a few AA driven optimizations here and now, to cleanup the code.
    PM.add(llvm::createFunctionAttrsPass()); // Add nocapture.
    PM.add(llvm::createGlobalsModRefPass()); // IP alias analysis.
    
    PM.add(llvm::createLICMPass());                 // Hoist loop invariants.
    PM.add(llvm::createGVNPass(false));             // Remove redundancies.
    PM.add(llvm::createMemCpyOptPass());            // Remove dead memcpys.
    
    // Nuke dead stores.
    PM.add(llvm::createDeadStoreEliminationPass());
    
    // More loops are countable; try to optimize them.
    PM.add(llvm::createIndVarSimplifyPass());
    PM.add(llvm::createLoopDeletionPass());
    PM.add(llvm::createLoopVectorizePass(true, true));
    
    // More scalar chains could be vectorized due to more alias information
    PM.add(llvm::createSLPVectorizerPass()); // Vectorize parallel scalar chains.
    
    // Cleanup and simplify the code after the scalar optimizations.
    PM.add(llvm::createInstructionCombiningPass());
    
    PM.add(llvm::createJumpThreadingPass());
    
    // Delete basic blocks, which optimization passes may have killed.
    PM.add(llvm::createCFGSimplificationPass());
}

// Quick & dirty 'const char *' vector that frees on destruction
// assumes that the strings were malloc'd
class arg_vector : public std::vector<const char *> {
public:
    using std::vector<const char *>::vector;
    ~arg_vector() { for (const char * elem : *this) std::free((void*)elem); }
};

NativeModule::NativeModule(std::string _fileName, std::string _sourceCode, std::vector<std::string> _compilerArgs) :
fileName(_fileName),
sourceCode(_sourceCode),
compilerArgs(_compilerArgs) {
    
	// Arguments to pass to the clang frontend
    arg_vector args;
    for (std::string arg : compilerArgs) {
        args.push_back(strdup(arg.c_str()));
    }
    
    // We'll fake the contents of this file later
	args.push_back(strdup(fileName.c_str()));
    
	// The compiler invocation needs a DiagnosticsEngine so it can report problems
    llvm::raw_string_ostream errs(errors);
    llvm::IntrusiveRefCntPtr<clang::DiagnosticOptions> DiagOpts(new clang::DiagnosticOptions());
	clang::TextDiagnosticPrinter *DiagClient = new clang::TextDiagnosticPrinter(errs, DiagOpts.getPtr()); //This is owned by Diags
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
    
    std::string ErrStr;
    executionEngine.reset(llvm::EngineBuilder(module)
    .setErrorStr(&ErrStr)
    .setUseMCJIT(true)
    .setOptLevel(llvm::CodeGenOpt::Aggressive)
    .create());
    if (!executionEngine) {
        errs <<  "Cannot create execution engine: " <<  ErrStr << "\n";
        return;
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
    
    // Setup C/C++ standard optimizations
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
    populateLTOPassManager(Passes);

    // Add target specific passes
    executionEngine->getTargetMachine()->addAnalysisPasses(FPM);
    executionEngine->getTargetMachine()->addAnalysisPasses(Passes);

    
    FPM.doInitialization();
    // Run function-level optimizations for each function in the module
    for (llvm::Module::iterator it = module->begin(), E = module->end(); it != E; ++it) {
        FPM.run(*it);
    }
    FPM.doFinalization();

    // Run module level passes
    Passes.run(*module);
    
    // Tell the ExecutionEngine we're done
    executionEngine->generateCodeForModule(module);
    executionEngine->finalizeObject();
    
    // Save all functions that survived optimization
    for (llvm::Module::iterator it = module->begin(), E = module->end(); it != E; ++it) {
        functions.push_back(it);
    }
}

std::vector<llvm::Function*> NativeModule::getFunctions() const {
    return functions;
}

llvm::GenericValue NativeModule::runFunction(llvm::Function *function, const std::vector<llvm::GenericValue> &argValues) {
    llvm::GenericValue result;
    if (executionEngine) {
        result = executionEngine->runFunction(function, argValues);
    }
    return result;
}
