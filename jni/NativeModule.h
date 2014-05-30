#include <clang/CodeGen/CodeGenAction.h>
#include <llvm/IR/Module.h>
#include <llvm/ExecutionEngine/ExecutionEngine.h>
#include <llvm/IR/LLVMContext.h>
#include <llvm/ExecutionEngine/GenericValue.h>
#include <llvm/IR/DerivedTypes.h>

#ifndef _Included_NativeModule
#define _Included_NativeModule

class NativeModule {
    llvm::LLVMContext context;
    llvm::Module * module; // This is owned by the execution engine
    std::unique_ptr<llvm::ExecutionEngine> executionEngine;
    std::vector<llvm::Function*> functions;
    std::string fileName;
    std::string sourceCode;
    std::vector<std::string> compilerArgs;
public:
    NativeModule(std::string fileName, std::string sourceCode, std::vector<std::string> compilerArgs);
    std::vector<llvm::Function*> getFunctions() const;
    llvm::GenericValue runFunction(llvm::Function *F, const std::vector<llvm::GenericValue> &ArgValues);
};

#endif
