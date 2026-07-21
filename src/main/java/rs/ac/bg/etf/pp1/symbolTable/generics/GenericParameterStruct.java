package rs.ac.bg.etf.pp1.symbolTable.generics;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Struct;

/**
 * Represents a generic parameter with an optional constraint.
 */
public final class GenericParameterStruct extends Struct {
    private boolean isUsedAsArrayElementType;

    public GenericParameterStruct() {
        this(null);
    }

    public GenericParameterStruct(Struct constraint) {
        super(Struct.None);

        if (constraint instanceof GenericParameterStruct)
            throw new IllegalArgumentException("A type parameter cannot be used directly as a constraint");
        if (constraint != null && constraint.getKind() != Struct.Class && constraint.getKind() != Struct.Interface)
            throw new IllegalArgumentException("Type constraints can only be class or interface types");

        // Store constraining type in the elemType field
        setElementType(constraint);
    }

    public Struct getConstraint() {
        return getElemType();
    }

    public boolean hasConstraint() {
        return getConstraint() != null;
    }

    public void markUsedAsArrayElementType() {
        isUsedAsArrayElementType = true;
    }

    /**
     * Specifies if this generic parameter type is used as an array element type at any point.
     * This then allows us to check type arguments and report error for the ones that cannot create arrays, like sets.
     */
    public boolean isUsedAsArrayElementType() {
        return isUsedAsArrayElementType;
    }

    @Override
    public boolean isRefType() {
        return hasConstraint();
    }

    @Override
    public boolean compatibleWith(Struct other) {
        if (other == null)
            return false;
        if (equals(other))
            return true;
        if (!hasConstraint() || other instanceof GenericParameterStruct)
            return false;
        if (other == Tab.nullType)
            return true;
        return getConstraint().compatibleWith(other);
    }

    @Override
    public boolean assignableTo(Struct destination) {
        if (destination == null)
            return false;
        if (equals(destination))
            return true;
        if (!hasConstraint() || destination instanceof GenericParameterStruct)
            return false;

        // This is only the direct Struct relation. TabUtils additionally checks
        // inheritance and implemented interfaces through this constraint.
        return getConstraint().assignableTo(destination);
    }

    @Override
    public boolean equals(Struct other) {
        return this == other;
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}