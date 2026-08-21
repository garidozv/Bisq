package rs.ac.bg.etf.pp1.codeGeneration;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.codeGeneration.generics.GenericSpecialization;
import rs.ac.bg.etf.pp1.codeGeneration.generics.GenericTypeSpecialization;
import rs.ac.bg.etf.pp1.codeGeneration.generics.MonomorphizationPlan;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils.MethodTypes;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeApplicationStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

import java.util.ArrayList;
import java.util.stream.Collectors;

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
        var hasGenericMethods = !getDeclaredGenericMethods(interfaceDeclaration).isEmpty();
        if (!(interfaceDeclaration.obj instanceof GenericTypeObj) && !hasGenericMethods) {
            interfaceDeclaration.traverseBottomUp(generator);
            return;
        }

        emitTypeMethods(interfaceDeclaration, interfaceDeclaration.obj);
    }

    @Override
    public void visit(ProgramDeclList_ClassDecl declarations) {
        declarations.getProgramDeclList().accept(this);

        var classDeclaration = declarations.getClassDecl();
        var hasGenericMethods = !getDeclaredGenericMethods(classDeclaration).isEmpty();
        if (!(classDeclaration.obj instanceof GenericTypeObj) && !hasGenericMethods) {
            classDeclaration.traverseBottomUp(generator);
            return;
        }

        emitTypeMethods(classDeclaration, classDeclaration.obj);
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
        var declaration = (GenericMethodObj) method.obj;
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

    private static ArrayList<GenericMethodDecl> getDeclaredGenericMethods(SyntaxNode declaration) {
        var methods = new ArrayList<GenericMethodDecl>();
        declaration.traverseTopDown(new VisitorAdaptor() {
            @Override
            public void visit(GenericMethodDecl method) {
                methods.add(method);
            }
        });
        return methods;
    }

    /**
     * Emits methods for every concrete form of the given type.
     * <p>Ordinary types are emitted once, while generic types are emitted once for each required type specialization.
     * Within each concrete type, generic methods are emitted once for each required method specialization.</p>
     */
    private void emitTypeMethods(SyntaxNode declaration, Obj typeObject) {
        if (typeObject instanceof GenericTypeObj genericType) {
            for (var ownerSpecialization : monomorphizationPlan.getNeededSpecializations(genericType)) {
                emitConcreteTypeMethods(declaration, ownerSpecialization);
            }
        } else {
            emitConcreteTypeMethods(declaration, null);
        }
    }

    private void emitConcreteTypeMethods(SyntaxNode declaration, GenericTypeSpecialization ownerSpecialization) {
        // Regular methods are generated only once
        for (var method : getDeclaredMethods(declaration)) {
            var generatedMethod = ownerSpecialization == null ? method.obj : ownerSpecialization.resolveObject(method.obj);
            generator.generateMethod(method.getStatementList(), generatedMethod, ownerSpecialization);
        }

        // Generic methods need to be generated for each one of their specializations
        for (var method : getDeclaredGenericMethods(declaration)) {
            var genericMethod = (GenericMethodObj) method.obj;
            var specializations = ownerSpecialization == null
                    ? monomorphizationPlan.getNeededSpecializations(genericMethod)
                    : monomorphizationPlan.getNeededSpecializations(genericMethod, ownerSpecialization);
            for (var specialization : specializations) {
                generator.generateMethod(method.getStatementList(), specialization.getGeneratedObject(), specialization);
            }
        }
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
                    configureTypeMembers(classDeclaration, specialization.getGeneratedObject().getType(), specialization);
                    generator.registerClass(specialization.getGeneratedObject());
                }
            } else {
                configureTypeMembers(classDeclaration, classDeclaration.obj.getType(), null);
                generator.registerClass(classDeclaration.obj);
            }
        } else if (declarations instanceof ProgramDeclList_InterfaceDecl interfaceDeclarations) {
            registerClasses(interfaceDeclarations.getProgramDeclList());
            var declaration = interfaceDeclarations.getInterfaceDecl();
            if (declaration.obj instanceof GenericTypeObj genericType) {
                for (var specialization : monomorphizationPlan.getNeededSpecializations(genericType)) {
                    configureTypeMembers(declaration, specialization.getGeneratedObject().getType(), specialization);
                }
            } else {
                configureTypeMembers(declaration, declaration.obj.getType(), null);
            }
        } else if (declarations instanceof ProgramDeclList_ConstDecl constDeclarations) {
            registerClasses(constDeclarations.getProgramDeclList());
        } else if (declarations instanceof ProgramDeclList_VarDecl varDeclarations) {
            registerClasses(varDeclarations.getProgramDeclList());
        }
    }

    private void configureTypeMembers(SyntaxNode declaration, Struct generatedType, GenericTypeSpecialization specialization) {
        var generatedMembers = new HashTableDataStructure();
        generatedType.getMembers().forEach(generatedMembers::insertKey);

        if (declaration instanceof ClassDecl classDeclaration) {
            configureClassInheritance(classDeclaration, generatedType, specialization, generatedMembers);
            configureImplementedInterface(classDeclaration, generatedType, specialization, generatedMembers);
        }
        configureGenericMemberMethods(declaration, generatedType, specialization, generatedMembers);
        generatedType.setMembers(generatedMembers);
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
    private void configureClassInheritance(ClassDecl declaration, Struct generatedType,
                                           GenericSpecialization<?> specialization, HashTableDataStructure generatedMembers) {
        if (!(declaration instanceof ClassDecl_Derived derived) || declaration.obj.getType().getElemType() == null)
            return;

        // Rebuild members when either side is specialized so inherited methods use the same generated objects as the base.
        // For ordinary inheritance, rebuild only when the class inherits generic methods from the base
        var extendedType = declaration.obj.getType().getElemType();
        var hasGenericMember = generatedType.getMembers().stream().anyMatch(GenericMethodObj.class::isInstance);
        if (specialization == null && !(extendedType instanceof GenericTypeApplicationStruct) && !hasGenericMember) return;

        var generatedExtendedClass = extendedType;
        if (extendedType instanceof GenericTypeApplicationStruct) {
            var inheritance = (ExtendedTypeName_Valid) derived.getExtendedTypeName();
            generatedExtendedClass = monomorphizationPlan.getTargetSpecialization(inheritance, specialization).getGeneratedObject().getType();
        }

        // Replace inherited method copies with the actual method objects from the generated base
        generatedExtendedClass.getMembers().stream()
                .filter(member -> member.getKind() == Obj.Meth)
                .forEach(member -> replaceMember(generatedMembers, member));

        // Add methods from the derived specialized Obj and handle overriding while doing that
        for (var method : getDeclaredMethods(declaration)) {
            var generatedMethod = specialization == null ? method.obj : specialization.resolveObject(method.obj);
            replaceMember(generatedMembers, generatedMethod);
        }

        generatedType.setElementType(generatedExtendedClass);
    }

    /**
     * Replaces generic method declarations with the method objects for each required specialization.
     *
     * <pre>{@code
     * class Processor {
     *     {
     *         <T> T process(T value) { return value; }
     *     }
     * }
     *
     * Processor processor;
     * processor.process::<int>(1);
     * processor.process::<char>('a');
     * }</pre>
     *
     * <p>The member table initially contains one open {@code process<T>} declaration. This method removes that declaration
     * and inserts the generated method objects for {@code process<int>} and {@code process<char>}, allowing both
     * specializations to be stored in the owner's virtual table and the code to be generated for each of them.</p>
     */
    private void configureGenericMemberMethods(SyntaxNode declaration, Struct generatedType,
                                               GenericTypeSpecialization ownerSpecialization, HashTableDataStructure generatedMembers) {
        generatedType.getMembers().stream()
                .filter(GenericMethodObj.class::isInstance)
                .forEach(member -> generatedMembers.deleteKey(member.getName()));

        for (var method : getDeclaredGenericMethods(declaration)) {
            var genericMethod = (GenericMethodObj) method.obj;
            var specializations = ownerSpecialization == null
                    ? monomorphizationPlan.getNeededSpecializations(genericMethod)
                    : monomorphizationPlan.getNeededSpecializations(genericMethod, ownerSpecialization);
            for (var specialization : specializations) {
                replaceMember(generatedMembers, specialization.getGeneratedObject());
            }
        }
    }

    /**
     * Similar to {@link #configureClassInheritance(ClassDecl, Struct, GenericSpecialization, HashTableDataStructure)},
     * but handles interface implementation.
     * It replaces copied default method objects with the actual objects from the specialized interface.
     */
    private void configureImplementedInterface(ClassDecl declaration, Struct generatedType,
                                               GenericSpecialization<?> specialization, HashTableDataStructure generatedMembers) {
        if (!(declaration instanceof ClassDecl_Derived derived) ||
                !(derived.getExtendedTypeName() instanceof ExtendedTypeName_Valid inheritance))
            return;

        // Rebuild members when either side is specialized so inherited default methods use the same generated objects as the interface.
        // For an ordinary implementation, rebuild only when the class inherits generic methods from the interface
        var extendedType = inheritance.getType().struct;
        var hasGenericMember = generatedType.getMembers().stream().anyMatch(GenericMethodObj.class::isInstance);
        if (extendedType.getKind() != Struct.Interface || (specialization == null &&
                !(extendedType instanceof GenericTypeApplicationStruct) && !hasGenericMember))
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
        generatedExtendedInterface.getMembers().stream()
                .filter(method ->
                        method.getKind() == Obj.Meth &&
                                method.getFpPos() != MethodTypes.LOCAL_UNIMPLEMENTED.value &&
                                !declaredMethodNames.contains(method.getName()))
                .forEach(method -> replaceMember(generatedMembers, method));
    }

    private static void replaceMember(HashTableDataStructure members, Obj member) {
        members.deleteKey(member.getName());
        members.insertKey(member);
    }
}
