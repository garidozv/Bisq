package rs.ac.bg.etf.pp1.symbolTable.generics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Struct;

public final class GenericTypeUtils {

    /**
     * Returns the declaration applied to its own parameters, such as {@code Box<T>}.
     */
    public static AppliedGenericTypeStruct createOpenApplication(GenericTypeObj declaration) {
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
        if (type instanceof AppliedGenericTypeStruct genericType) {
            for (var argument : genericType.getTypeArguments()) {
                if (!isValidTypeArgument(argument)) return false;
            }
        }
        return true;
    }

    private static void collectContainedTypeParameters(Struct type, Set<GenericParameterStruct> result) {
        if (type instanceof GenericParameterStruct parameter) {
            result.add(parameter);
            return;
        }
        if (type instanceof AppliedGenericTypeStruct genericType) {
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