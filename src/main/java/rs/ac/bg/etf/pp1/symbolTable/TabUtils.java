package rs.ac.bg.etf.pp1.symbolTable;

import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeApplicationStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.List;

public class TabUtils {
    private static final String INTERNAL_NAME_PREFIX = "__";
    public static final String THIS_VARIABLE_NAME = "this";

    // Enum is used for sets
    public static final Struct setType = new Struct(Struct.Enum);
    public static final Struct boolType = new Struct(Struct.Bool);

    public enum MethodTypes {
        GLOBAL(-2),
        LOCAL(-3),
        LOCAL_UNIMPLEMENTED(-4);

        public final int value;

        MethodTypes(int val) {
            value = val;
        }
    }

    public static void init() {
        Tab.init();

        Tab.find("ord").setFpPos(MethodTypes.GLOBAL.value);
        Tab.find("chr").setFpPos(MethodTypes.GLOBAL.value);
        Tab.find("len").setFpPos(MethodTypes.GLOBAL.value);

        // Add missing types to universe scope
        var boolTypeObj = Tab.insert(Obj.Type, "bool", TabUtils.boolType);
        boolTypeObj.setAdr(-1);
        boolTypeObj.setLevel(-1);

        setType.setElementType(Tab.intType);
        var setTypeObj = Tab.insert(Obj.Type, "set", TabUtils.setType);
        setTypeObj.setAdr(-1);
        setTypeObj.setLevel(-1);

        // Add pre-defined methods to universe scope
        insertAddMethodSymbol();
        insertAddAllMethodSymbol();
    }

    public static Obj createDummyObj(int kind, int adr, boolean global) {
        var obj = new Obj(kind, null, Tab.noType);
        obj.setAdr(adr);
        obj.setLevel(global ? 0 : 1);

        return obj;
    }

    public static String createInternalName(String name) {
        return INTERNAL_NAME_PREFIX + name;
    }

    /**
     * Creates the {@link Obj#Type} symbol whose type is one generic parameter.
     */
    public static Obj createGenericParameter(String name) {
        return createGenericParameter(name, null);
    }

    /**
     * Creates the {@link Obj#Type} symbol whose type is one constrained generic parameter.
     */
    public static Obj createGenericParameter(String name, Struct constraint) {
        return new Obj(Obj.Type, name, new GenericParameterStruct(constraint));
    }

    /**
     * Creates, but does not insert in symbol table, a generic class or interface declaration.
     */
    public static GenericTypeObj createGenericType(String name, int kind, List<Obj> genericParameters) {
        if (genericParameters == null)
            throw new IllegalArgumentException("Generic parameters are required");
        return new GenericTypeObj(name, new Struct(kind), genericParameters);
    }

    /**
     * Creates a generic global method without inserting it into the symbol table.
     */
    public static GenericMethodObj createGenericMethod(String name, Struct returnType, List<Obj> genericParameters) {
        return createGenericMethod(name, returnType, genericParameters, null, List.of());
    }

    /**
     * Creates a generic member method without inserting it into the symbol table.
     *
     * @param owner                   Declaring class or interface.
     * @param enclosingTypeParameters Generic parameters declared by the owner.
     */
    public static GenericMethodObj createGenericMethod(String name, Struct returnType, List<Obj> genericParameters,
                                                       Obj owner, List<Obj> enclosingTypeParameters) {
        if (genericParameters == null)
            throw new IllegalArgumentException("Generic parameters are required");
        return new GenericMethodObj(name, returnType, genericParameters, owner, enclosingTypeParameters);
    }

    /**
     * Inserts a preconstructed {@link Obj} subclass while preserving {@link Tab#insert}'s behavior.
     */
    public static Obj insert(Obj obj) {
        if (obj == null)
            throw new IllegalArgumentException("Cannot insert a null symbol");
        obj.setAdr(0);
        obj.setLevel(Tab.currentScope().getOuter() == null ? 0 : 1);

        if (Tab.currentScope().addToLocals(obj)) return obj;

        Obj existing = Tab.currentScope().findSymbol(obj.getName());
        return existing != null ? existing : Tab.noObj;
    }

    /**
     * Compares types using generic-aware equality whenever either operand is a generic {@link Struct} subclass.
     */
    public static boolean equals(Struct first, Struct second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first instanceof GenericParameterStruct || first instanceof GenericTypeApplicationStruct)
            return first.equals(second);
        if (second instanceof GenericParameterStruct || second instanceof GenericTypeApplicationStruct)
            return second.equals(first);
        return first.equals(second);
    }

    /**
     * Interface-aware {@link Struct#isRefType()} implementation.
     */
    public static boolean isRefType(Struct type) {
        if (type == null) return false;
        return type.isRefType() || type.getKind() == Struct.Interface;
    }

    /**
     * Generic-aware {@link Struct#compatibleWith(Struct)} implementation.
     */
    public static boolean compatibleWith(Struct first, Struct second) {
        if (first == null || second == null) return false;
        if (equals(first, second)) return true;
        if (first == Tab.nullType) return isRefType(second);
        if (second == Tab.nullType) return isRefType(first);
        if (first instanceof GenericParameterStruct || second instanceof GenericParameterStruct)
            return assignableTo(first, second) || assignableTo(second, first);
        return first.compatibleWith(second);
    }

    /**
     * Inheritance and generic-aware {@link Struct#assignableTo(Struct)} implementation.
     */
    public static boolean assignableTo(Struct dest, Struct src) {
        if (dest == null || src == null) return false;
        if (equals(dest, src)) return true;

        if (src instanceof GenericParameterStruct parameter) {
            if (parameter.assignableTo(dest)) return true;
            if (!parameter.hasConstraint()) return false;
            src = parameter.getConstraint();
        }
        if (dest instanceof GenericParameterStruct parameter)
            return src == Tab.nullType && parameter.isRefType();

        if (src == Tab.nullType && isRefType(dest)) return true;
        if (src.assignableTo(dest)) return true;

        // Inheritance checks
        if (dest.getKind() == Struct.Interface && src.getKind() == Struct.Class && implementsInterface(src, dest))
            return true;

        if (dest.getKind() == Struct.Class && src.getKind() == Struct.Class) {
            var baseType = src.getElemType();
            while (baseType != null) {
                if (equals(baseType, dest)) return true;
                baseType = baseType.getElemType();
            }
        }

        return false;
    }

    /**
     * Finds the most specific common type to which both supplied types can be assigned.
     * Common base classes take priority over implemented interfaces.
     *
     * @return the common type, or {@code null} if one cannot be found
     */
    public static Struct findCommonType(Struct first, Struct second) {
        if (first == null || second == null) return null;
        if (equals(first, second)) return first;

        if (first.getKind() != Struct.Class || second.getKind() != Struct.Class) {
            if (assignableTo(first, second)) return first;
            return assignableTo(second, first) ? second : null;
        }

        Struct commonInterface = null;
        // Goes through the first type's class hierarchy and implemented interfaces, and 'assignableTo' performs the corresponding checks for the second type
        for (var currentClass = first; currentClass != null && currentClass.getKind() == Struct.Class; currentClass = currentClass.getElemType()) {
            if (assignableTo(currentClass, second)) return currentClass;

            for (var implemented : currentClass.getImplementedInterfaces()) {
                if (assignableTo(implemented, second)) commonInterface = implemented;
            }
        }
        return commonInterface;
    }

    private static boolean implementsInterface(Struct source, Struct destination) {
        for (var current = source; current != null; current = current.getElemType()) {
            for (var implemented : current.getImplementedInterfaces()) {
                if (equals(implemented, destination)) return true;
            }
        }
        return false;
    }

    private static void insertAddMethodSymbol() {
        var methodObj = Tab.insert(Obj.Meth, "add", Tab.noType);
        Tab.openScope();

        Tab.insert(Obj.Var, "a", TabUtils.setType)
                .setLevel(1);

        Tab.insert(Obj.Var, "b", Tab.intType)
                .setLevel(1);

        Tab.insert(Obj.Var, "i", Tab.intType)
                .setLevel(1);

        methodObj.setAdr(0);
        methodObj.setLevel(2);
        methodObj.setFpPos(MethodTypes.GLOBAL.value);
        Tab.chainLocalSymbols(methodObj);
        Tab.closeScope();
    }

    private static void insertAddAllMethodSymbol() {
        var methodObj = Tab.insert(Obj.Meth, "addAll", Tab.noType);
        Tab.openScope();

        Tab.insert(Obj.Var, "a", TabUtils.setType)
                .setLevel(1);

        Tab.insert(Obj.Var, "b", new Struct(Struct.Array, Tab.intType))
                .setLevel(1);

        Tab.insert(Obj.Var, "i", Tab.intType)
                .setLevel(1);

        methodObj.setAdr(0);
        methodObj.setLevel(2);
        methodObj.setFpPos(MethodTypes.GLOBAL.value);
        Tab.chainLocalSymbols(methodObj);
        Tab.closeScope();
    }
}
