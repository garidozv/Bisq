package rs.ac.bg.etf.pp1.codeGeneration;

import java.util.ArrayList;

import rs.ac.bg.etf.pp1.ast.ClassDecl;
import rs.ac.bg.etf.pp1.ast.MethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodDeclList_GenericMethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodDeclList_MethodDecl;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_ClassDecl;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_ConstDecl;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_InterfaceDecl;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_VarDecl;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.codeGeneration.generics.MonomorphizationPlan;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;

/**
 * Controls code generation by deciding which concrete definitions must be generated and delegating that to the {@link CodeGenerator}.
 *
 * <p>A single bottom-up traversal is not enough for generics because a generic definition may need to be generated multiple times,
 * once for each required specialization (or not generated at all). This visitor controls declaration level traversal, while the
 * {@code CodeGenerator} keeps the existing statement and expression traversal used to generate bytecode.</p>
 *
 * <p>To provide this control, the controller does not perform a single automatic bottom-up traversal.
 * Instead, it manually traverses declarations, handling ordinary declarations normally and selecting the concrete
 * specializations that the generator must emit for generic declarations.</p>
 */
public final class CodeGenerationController extends VisitorAdaptor {
    private final CodeGenerator generator;
    private final MonomorphizationPlan monomorphizationPlan;

    public CodeGenerationController(CodeGenerator generator, MonomorphizationPlan monomorphizationPlan) {
        this.generator = generator;
        this.monomorphizationPlan = monomorphizationPlan;
    }

    public void generate(Program program) {
        registerClasses(program.getProgramDeclarations().getProgramDeclList());
        program.getProgramDeclarations().getProgramDeclList().accept(this);
        generator.emitProgramInitialization();
        program.getMethodDeclList().accept(this);
    }

    @Override
    public void visit(ProgramDeclList_ConstDecl declarations) {
        declarations.getProgramDeclList().accept(this);
    }

    @Override
    public void visit(ProgramDeclList_VarDecl declarations) {
        declarations.getProgramDeclList().accept(this);
    }

    @Override
    public void visit(ProgramDeclList_InterfaceDecl declarations) {
        declarations.getProgramDeclList().accept(this);
        declarations.getInterfaceDecl().traverseBottomUp(generator);
    }

    @Override
    public void visit(ProgramDeclList_ClassDecl declarations) {
        declarations.getProgramDeclList().accept(this);

        var classDeclaration = declarations.getClassDecl();
        if (!(classDeclaration.obj instanceof GenericTypeObj genericType)) {
            classDeclaration.traverseBottomUp(generator);
            return;
        }

        for (var specialization : monomorphizationPlan.getNeededSpecializations(genericType)) {
            for (var method : getDeclaredMethods(classDeclaration)) {
                generator.generateMethod(method.getStatementList(), specialization.resolveObject(method.obj), specialization);
            }
        }
    }

    @Override
    public void visit(MethodDeclList_MethodDecl methodList) {
        methodList.getMethodDeclList().accept(this);

        var method = methodList.getMethodDecl();
        generator.generateMethod(method.getStatementList(), method.obj, null);
    }

    @Override
    public void visit(MethodDeclList_GenericMethodDecl methodList) {
        methodList.getMethodDeclList().accept(this);

        var method = methodList.getGenericMethodDecl();
        var declaration = (GenericMethodObj)method.obj;
        for (var specialization : monomorphizationPlan.getNeededSpecializations(declaration)) {
            generator.generateMethod(method.getStatementList(), specialization.getGeneratedObject(), specialization);
        }
    }

    private static ArrayList<MethodDecl> getDeclaredMethods(ClassDecl declaration) {
        var methods = new ArrayList<MethodDecl>();
        declaration.traverseTopDown(new VisitorAdaptor() {
            @Override
            public void visit(MethodDecl method) {
                methods.add(method);
            }
        });
        return methods;
    }

    /**
     * Registers all regular classes and specializations of generic classes before their definitions are generated.
     * This allows us to know the addresses of their virtual tables in advance so that we can use them directly in definitions
     * without having to patch them at the end. Once all definitions have been generated and their method addresses are known,
     * the virtual tables are populated with those addresses.
     */
    private void registerClasses(ProgramDeclList declarations) {
        if (declarations instanceof ProgramDeclList_ClassDecl classDeclarations) {
            registerClasses(classDeclarations.getProgramDeclList());

            var classDeclaration = classDeclarations.getClassDecl();
            if (classDeclaration.obj instanceof GenericTypeObj genericType) {
                for (var specialization : monomorphizationPlan.getNeededSpecializations(genericType)) {
                    generator.registerClass(specialization.getGeneratedObject());
                }
            }
            else {
                generator.registerClass(classDeclaration.obj);
            }
        }
        else if (declarations instanceof ProgramDeclList_InterfaceDecl interfaceDeclarations) {
            registerClasses(interfaceDeclarations.getProgramDeclList());
        }
        else if (declarations instanceof ProgramDeclList_ConstDecl constDeclarations) {
            registerClasses(constDeclarations.getProgramDeclList());
        }
        else if (declarations instanceof ProgramDeclList_VarDecl varDeclarations) {
            registerClasses(varDeclarations.getProgramDeclList());
        }
    }
}