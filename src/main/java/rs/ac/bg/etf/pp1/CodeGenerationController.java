package rs.ac.bg.etf.pp1;

import rs.ac.bg.etf.pp1.ast.MethodDeclList_GenericMethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodDeclList_MethodDecl;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.generics.MonomorphizationPlan;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;

/**
 * Controls code generation by deciding which concrete definitions must be generated and delegating that to the {@link CodeGenerator}.
 *
 * <p>A single bottom-up traversal is not enough for generics because a generic definition may need to be generated multiple times,
 * once for each required specialization (or not generated at all). This visitor controls declaration level traversal, while the
 * {@code CodeGenerator} keeps the existing statement and expression traversal used to generate bytecode.</p>
 */
public final class CodeGenerationController extends VisitorAdaptor {

    private final CodeGenerator generator;
    private final MonomorphizationPlan monomorphizationPlan;

    public CodeGenerationController(CodeGenerator generator, MonomorphizationPlan monomorphizationPlan) {
        this.generator = generator;
        this.monomorphizationPlan = monomorphizationPlan;
    }

    public void generate(Program program) {
        program.getProgramDeclarations().getProgramDeclList().traverseBottomUp(generator);
        generator.emitProgramInitialization();
        program.getMethodDeclList().accept(this);
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
            generator.generateMethod(method.getStatementList(), specialization.getGeneratedMethod(), specialization);
        }
    }
}