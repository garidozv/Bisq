package rs.ac.bg.etf.pp1.symbolTable.generics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

public final class GenericTypeUtils {
    private GenericTypeUtils() {}

    /**
     * Returns the declaration applied to its own parameters, such as {@code Box<T>}.
     */
    public static GenericTypeApplicationStruct createOpenApplication(GenericTypeObj declaration) {
        if (declaration == null)
            throw new IllegalArgumentException("A generic application needs a declaration");
        var parameters = new ArrayList<Struct>(declaration.getTypeParameterCount());
        for (var index = 0; index < declaration.getTypeParameterCount(); index++) {
            parameters.add(declaration.getTypeParameterType(index));
        }
        return declaration.applyArguments(parameters);
    }

    /**
     * Returns all type parameters contained in the given type.
     * <p>
     * For example, for {@code Pair<List<T>, Pair<int, U>>} the result will be {@code {T, U}}.
     * </p>
     */
    public static Set<GenericParameterStruct> getContainedTypeParameters(Struct type) {
        var result = Collections.<GenericParameterStruct>newSetFromMap(new IdentityHashMap<>());
        collectContainedTypeParameters(type, result);
        return Collections.unmodifiableSet(result);
    }

    public static boolean isClosed(Struct type) {
        return getContainedTypeParameters(type).isEmpty();
    }

    /**
     * Checks if a specified type can be used as a type argument.
     */
    public static boolean isValidTypeArgument(Struct type) {
        if (type == null || type == Tab.noType || type == Tab.nullType) {
            return false;
        }
        if (type.getKind() == Struct.Array) {
            var element = type.getElemType();
            return element != null && element != TabUtils.setType && isValidTypeArgument(element);
        }
        if (type instanceof GenericTypeApplicationStruct genericType) {
            for (var argument : genericType.getTypeArguments()) {
                if (!isValidTypeArgument(argument)) return false;
            }
        }
        return true;
    }

    /**
     * Returns a type with the provided generic parameter substitutions applied recursively.
     */
    public static Struct substituteType(Struct type, Map<GenericParameterStruct, ? extends Struct> substitutions) {
        if (type == null) return null;

        if (type instanceof GenericParameterStruct parameter) {
            var substitution = substitutions.get(parameter);
            return substitution != null ? substitution : parameter;
        }
        if (type.getKind() == Struct.Array) {
            var oldElement = type.getElemType();
            var newElement = substituteType(oldElement, substitutions);
            return newElement == oldElement ? type : createArrayType(newElement);
        }
        if (type instanceof GenericTypeApplicationStruct application) {
            var newArguments = new ArrayList<Struct>(application.getTypeArguments().size());
            var changed = false;
            for (var oldArgument : application.getTypeArguments()) {
                var newArgument = substituteType(oldArgument, substitutions);
                newArguments.add(newArgument);
                changed |= newArgument != oldArgument;
            }
            return changed ? application.getDeclaration().applyArguments(newArguments) : type;
        }
        return type;
    }

    /**
     * Creates a new object with substitutions applied to its type and local symbol types.
     */
    public static Obj substituteObjectTypes(Obj object, Map<GenericParameterStruct, ? extends Struct> substitutions) {
        if (object == null) return null;

        var substituted = new Obj(object.getKind(), object.getName(), substituteType(object.getType(), substitutions),
                object.getAdr(), object.getLevel());
        substituted.setFpPos(object.getFpPos());

        if (!object.getLocalSymbols().isEmpty()) {
            var locals = new HashTableDataStructure();
            for (var local : object.getLocalSymbols()) {
                locals.insertKey(substituteObjectTypes(local, substitutions));
            }
            substituted.setLocals(locals);
        }
        return substituted;
    }

    public static Struct createArrayType(Struct elementType) {
        if (!isValidTypeArgument(elementType) || elementType == TabUtils.setType)
            throw new IllegalArgumentException("Invalid array element type");
        return new Struct(Struct.Array, elementType);
    }

    public static int typeHashCode(Struct type) {
        if (type instanceof GenericParameterStruct || type instanceof GenericTypeApplicationStruct)
            return type.hashCode();
        if (type != null && type.getKind() == Struct.Array)
            return 31 * Struct.Array + typeHashCode(type.getElemType());
        return type == null ? 0 : type.getKind();
    }

    private static void collectContainedTypeParameters(Struct type, Set<GenericParameterStruct> result) {
        if (type instanceof GenericParameterStruct parameter) {
            result.add(parameter);
            return;
        }
        if (type instanceof GenericTypeApplicationStruct genericType) {
            for (var argument : genericType.getTypeArguments()) {
                collectContainedTypeParameters(argument, result);
            }
            return;
        }
        if (type != null && type.getKind() == Struct.Array) {
            collectContainedTypeParameters(type.getElemType(), result);
        }
    }
}