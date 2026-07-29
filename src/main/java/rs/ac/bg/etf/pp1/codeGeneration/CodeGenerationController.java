package rs.ac.bg.etf.pp1.codeGeneration;

import java.util.ArrayList;
import java.util.stream.Collectors;

import rs.ac.bg.etf.pp1.ast.ClassDecl;
import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ExtendedTypeName_Valid;
import rs.ac.bg.etf.pp1.ast.MethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodDeclList_GenericMethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodDeclList_MethodDecl;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_ClassDecl;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_ConstDecl;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_InterfaceDecl;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList_VarDecl;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.codeGeneration.generics.GenericSpecialization;
import rs.ac.bg.etf.pp1.codeGeneration.generics.MonomorphizationPlan;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils.MethodTypes;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeApplicationStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

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

        var interfaceDeclaration = declarations.getInterfaceDecl();
        if (!(interfaceDeclaration.obj instanceof GenericTypeObj genericType)) {
            interfaceDeclaration.traverseBottomUp(generator);
            return;
        }

        for (var specialization : monomorphizationPlan.getNeededSpecializations(genericType)) {
            for (var method : getDeclaredMethods(interfaceDeclaration)) {
                generator.generateMethod(method.getStatementList(), specialization.resolveObject(method.obj), specialization);
            }
        }
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

    private static ArrayList<MethodDecl> getDeclaredMethods(SyntaxNode declaration) {
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
     * Configures concrete inheritance relationships and registers all regular classes and generic class specializations
     * with the generator before their definitions are generated.
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
                    configureClassInheritance(classDeclaration, specialization.getGeneratedObject().getType(), specialization);
                    configureImplementedInterface(classDeclaration, specialization.getGeneratedObject().getType(), specialization);
                    generator.registerClass(specialization.getGeneratedObject());
                }
            }
            else {
                configureClassInheritance(classDeclaration, classDeclaration.obj.getType(), null);
                configureImplementedInterface(classDeclaration, classDeclaration.obj.getType(), null);
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

    /**
     * Connects a concrete derived type to its concrete base and makes its virtual table reuse the generated base method objects.
     *
     * <p>After semantic analysis, the base and derived declaration types contain their complete member tables, but generic
     * members may still use open types. Creating their specializations produces new objects whose member types are closed.
     * However, the inherited members in the derived specialization are copies made from the derived declaration; they are
     * not the member objects belonging to the concrete base specialization.</p>
     *
     * <p>This method replaces those copied inherited methods with the actual method objects from the concrete base
     * specialization, then overlays methods declared by the derived class. This is necessary because method addresses are
     * assigned to those objects during code generation. Fields retain their specialized derived objects because their
     * addresses are fixed layout offsets and do not change during code generation.</p>
     */
    private void configureClassInheritance(ClassDecl declaration, Struct generatedType, GenericSpecialization<?> specialization) {
        if (!(declaration instanceof ClassDecl_Derived derived) || declaration.obj.getType().getElemType() == null)
            return;

        // Only handle class inheritance in a generic context
        var extendedType = declaration.obj.getType().getElemType();
        if (specialization == null && !(extendedType instanceof GenericTypeApplicationStruct)) return;

        var generatedExtendedClass = extendedType;
        if (extendedType instanceof GenericTypeApplicationStruct) {
            var inheritance = (ExtendedTypeName_Valid)derived.getExtendedTypeName();
            generatedExtendedClass = monomorphizationPlan.getTargetSpecialization(inheritance, specialization).getGeneratedObject().getType();
        }

        // Rebuild the members - take the existing fields from the derived specialized Obj and methods from the base specialized Obj
        var generatedMembers = new HashTableDataStructure();
        generatedType.getMembers().stream()
                .filter(member -> member.getKind() != Obj.Meth)
                .forEach(generatedMembers::insertKey);
        generatedExtendedClass.getMembers().stream()
                .filter(member -> member.getKind() == Obj.Meth)
                .forEach(generatedMembers::insertKey);

        // Add methods from the derived specialized Obj and handle overriding while doing that
        for (var method : getDeclaredMethods(declaration)) {
            var generatedMethod = specialization == null ? method.obj : specialization.resolveObject(method.obj);
            generatedMembers.deleteKey(generatedMethod.getName());
            generatedMembers.insertKey(generatedMethod);
        }

        generatedType.setElementType(generatedExtendedClass);
        generatedType.setMembers(generatedMembers);
    }

    /**
     * Similar to {@link #configureClassInheritance(ClassDecl, Struct, GenericSpecialization)}, but handles interface implementation.
     * It replaces copied default method objects with the actual objects from the specialized interface.
     */
    private void configureImplementedInterface(ClassDecl declaration, Struct generatedType, GenericSpecialization<?> specialization) {
        if (!(declaration instanceof ClassDecl_Derived derived) ||
                !(derived.getExtendedTypeName() instanceof ExtendedTypeName_Valid inheritance))
            return;

        // Only handle interface inheritance in a generic context
        var extendedType = inheritance.getType().struct;
        if (extendedType.getKind() != Struct.Interface || (specialization == null && !(extendedType instanceof GenericTypeApplicationStruct)))
            return;

        var generatedExtendedInterface = extendedType;
        if (extendedType instanceof GenericTypeApplicationStruct) {
            generatedExtendedInterface = monomorphizationPlan.getTargetSpecialization(inheritance, specialization).getGeneratedObject().getType();
        }

        // Rebuild the members - take the existing fields and interface method implementations from the derived specialized Obj
        // and default (implemented) methods from the specialized interface Obj
        var declaredMethodNames = getDeclaredMethods(declaration).stream()
                .map(method -> method.obj.getName())
                .collect(Collectors.toSet());
        var generatedMembers = new HashTableDataStructure();
        generatedType.getMembers().forEach(generatedMembers::insertKey);
        generatedExtendedInterface.getMembers().stream()
                .filter(method ->
                        method.getKind() == Obj.Meth &&
                        method.getFpPos() != MethodTypes.LOCAL_UNIMPLEMENTED.value &&
                        !declaredMethodNames.contains(method.getName()))
                .forEach(method -> {
                    generatedMembers.deleteKey(method.getName());
                    generatedMembers.insertKey(method);
                });
        generatedType.setMembers(generatedMembers);
    }
}