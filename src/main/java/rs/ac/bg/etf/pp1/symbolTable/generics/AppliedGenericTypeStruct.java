package rs.ac.bg.etf.pp1.symbolTable.generics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.etf.pp1.symboltable.concepts.Struct;

import static rs.ac.bg.etf.pp1.symbolTable.TabUtils.setType;
import static rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils.*;

/**
 * A use of a generic class or interface with all type arguments supplied.
 *
 * <p>
 * Note that the supplied type arguments may still be open and do not necessarily have to be of a concrete type (closed).
 * For example, both {@code Box<T>} and {@code Box<int>} below are represented by this struct.
 * The difference is that the former has an open type argument, while the latter has a closed {@code int} type argument:
 * </p>
 *
 * <pre>{@code
 * class Boxes<T> {
 *     Box<T> genericBox;
 *     Box<int> integerBox;
 * }
 * }</pre>
 */
public final class AppliedGenericTypeStruct extends Struct {
    // The generic declaration that this type was applied to
    private final GenericTypeObj genericDeclaration;
    // The type arguments that were applied to the generic declaration - they can be open or closed
    private final List<Struct> typeArguments;

    public AppliedGenericTypeStruct(GenericTypeObj genericDeclaration, List<Struct> typeArguments) {
        super(validateGenericDeclarationArgument(genericDeclaration).getType().getKind());

        validateTypeArguments(genericDeclaration, typeArguments);
        this.genericDeclaration = genericDeclaration;
        this.typeArguments = List.copyOf(typeArguments);
    }

    public GenericTypeObj getGenericDeclaration() {
        return genericDeclaration;
    }

    public List<Struct> getTypeArguments() {
        return typeArguments;
    }

    public boolean isOpen() {
        return !getContainedTypeParameters(this).isEmpty();
    }

    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public boolean isRefType() {
        // Applied generic types can only be classes or interfaces
        return true;
    }

    @Override
    public boolean equals(Struct other) {
        if (this == other)
            return true;
        if (!(other instanceof AppliedGenericTypeStruct genericOther))
            return false;
        if (genericDeclaration != genericOther.genericDeclaration || typeArguments.size() != genericOther.typeArguments.size())
            return false;

        for (var index = 0; index < typeArguments.size(); index++) {
            if (!TabUtils.equals(typeArguments.get(index), genericOther.typeArguments.get(index)))
                return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Struct struct && equals(struct);
    }

    @Override
    public int hashCode() {
        var hash = System.identityHashCode(genericDeclaration);
        for (var argument : typeArguments) {
            hash = 31 * hash + typeHashCode(argument);
        }
        return hash;
    }

    public static void validateTypeArguments(GenericObj declaration, List<Struct> arguments) {
        if (declaration == null)
            throw new IllegalArgumentException("A generic application needs a declaration");
        if (arguments == null || arguments.size() != declaration.getTypeParameterCount()) {
            var actual = arguments == null ? 0 : arguments.size();
            throw new IllegalArgumentException("Generic declaration '" + declaration.getName() + "' expects " +
                    declaration.getTypeParameterCount() + " type arguments, got " + actual);
        }

        var precedingArguments = new LinkedHashMap<GenericParameterStruct, Struct>();
        for (var index = 0; index < arguments.size(); index++) {
            var argument = arguments.get(index);
            var parameter = declaration.getTypeParameterType(index);
            if (!isValidTypeArgument(argument))
                throw new IllegalArgumentException("Invalid type argument at index " + index);

            var bound = substituteConstrainingType(parameter.getConstraint(), precedingArguments);
            if (bound != null && !TabUtils.assignableTo(bound, argument))
                throw new IllegalArgumentException("Type argument at index " + index + " does not satisfy its constraint");
            precedingArguments.put(parameter, argument);
        }
    }

    /**
     * Substitutes open types based on the provided type arguments.
     * <p>
     * For example, if you were to pass {@code Comparable} in place of {@code T} in {@code DependentPair<T, U : T>}, the
     * constraint on {@code U} would turn into {@code U : Comparable}.
     * </p>
     */
    public static Struct substituteConstrainingType(Struct type, Map<GenericParameterStruct, ? extends Struct> substitutions) {
        if (type == null) {
            return null;
        }
        if (type instanceof GenericParameterStruct parameter) {
            var substitution = substitutions.get(parameter);
            return substitution != null ? substitution : parameter;
        }
        if (type.getKind() == Struct.Array) {
            var oldElement = type.getElemType();
            var newElement = substituteConstrainingType(oldElement, substitutions);
            return newElement == oldElement ? type : createArrayType(newElement);
        }
        if (type instanceof AppliedGenericTypeStruct genericType) {
            var oldArguments = genericType.getTypeArguments();
            var newArguments = new ArrayList<Struct>(oldArguments.size());
            var changed = false;
            for (var oldArgument : oldArguments) {
                var newArgument = substituteConstrainingType(oldArgument, substitutions);
                newArguments.add(newArgument);
                changed |= newArgument != oldArgument;
            }
            if (!changed) return type;

            return genericType.getGenericDeclaration().applyArguments(newArguments);
        }
        return type;
    }

    public static Struct createArrayType(Struct elementType) {
        if (!GenericTypeUtils.isValidTypeArgument(elementType) || elementType == setType)
            throw new IllegalArgumentException("Invalid array element type");
        return new Struct(Struct.Array, elementType);
    }

    private static int typeHashCode(Struct type) {
        if (type instanceof GenericParameterStruct || type instanceof AppliedGenericTypeStruct)
            return type.hashCode();
        if (type != null && type.getKind() == Struct.Array)
            return 31 * Struct.Array + typeHashCode(type.getElemType());

        return type == null ? 0 : type.getKind();
    }

    private static GenericTypeObj validateGenericDeclarationArgument(GenericTypeObj argument) {
        if (argument == null)
            throw new IllegalArgumentException("A generic application needs a declaration");
        return argument;
    }
}