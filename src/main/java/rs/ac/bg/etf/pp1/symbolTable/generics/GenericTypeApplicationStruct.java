package rs.ac.bg.etf.pp1.symbolTable.generics;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

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
public final class GenericTypeApplicationStruct extends Struct {
    private final GenericTypeObj declaration;
    private final List<Struct> typeArguments;
    private final Map<GenericParameterStruct, Struct> substitution;

    public GenericTypeApplicationStruct(GenericTypeObj declaration, List<Struct> typeArguments) {
        super(requireDeclaration(declaration).getType().getKind());
        this.substitution = declaration.validateAndCreateSubstitution(typeArguments);
        this.declaration = declaration;
        this.typeArguments = List.copyOf(typeArguments);
    }

    public GenericTypeObj getDeclaration() {
        return declaration;
    }

    public List<Struct> getTypeArguments() {
        return typeArguments;
    }

    public Map<GenericParameterStruct, Struct> getSubstitution() {
        return substitution;
    }

    public boolean isOpen() {
        return !GenericTypeUtils.getContainedTypeParameters(this).isEmpty();
    }

    public boolean isClosed() {
        return !isOpen();
    }

    /**
     * Finds a declaration member and substitutes this application's arguments into its type.
     */
    public Obj findMember(String name) {
        var member = declaration.getType().getMembersTable().searchKey(name);
        return GenericTypeUtils.substituteObjectTypes(member, substitution);
    }

    /**
     * Returns the declaration members with this application's arguments substituted into their types.
     */
    @Override
    public Collection<Obj> getMembers() {
        return declaration.getType().getMembers().stream()
                .map(member -> GenericTypeUtils.substituteObjectTypes(member, substitution))
                .toList();
    }

    /**
     * Resolves the superclass template declared by the generic type using this application's arguments.
     * <br/><br/>
     * The {@link GenericTypeApplicationStruct} is just a type Struct generated from provided types, it doesn't hold
     * a reference to its base type, the same way {@link rs.etf.pp1.symboltable.concepts.Obj} does. So, we have to first
     * go to the declaration of the generic type, find the declaration of the base type there, and if it's also generic,
     * substitute its parameters based on the ones from the application.
     */
    @Override
    public Struct getElemType() {
        return GenericTypeUtils.substituteType(declaration.getType().getElemType(), substitution);
    }

    /**
     * Resolves the interface templates declared by the generic type using this application's arguments.
     * <br/><br/>
     * Similar to {@link GenericParameterStruct#getElemType()}, we find all implemented interfaces by taking their
     * declarations and substituting their parameters if needed.
     */
    @Override
    public List<Struct> getImplementedInterfaces() {
        return declaration.getType().getImplementedInterfaces().stream()
                .map(type -> GenericTypeUtils.substituteType(type, substitution))
                .toList();
    }

    @Override
    public boolean isRefType() {
        return true;
    }

    @Override
    public boolean equals(Struct other) {
        if (this == other) return true;
        if (!(other instanceof GenericTypeApplicationStruct genericOther)) return false;
        if (declaration != genericOther.declaration || typeArguments.size() != genericOther.typeArguments.size())
            return false;

        for (var index = 0; index < typeArguments.size(); index++) {
            if (!TabUtils.equals(typeArguments.get(index), genericOther.typeArguments.get(index))) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Struct struct && equals(struct);
    }

    @Override
    public int hashCode() {
        var hash = System.identityHashCode(declaration);
        for (var argument : typeArguments) {
            hash = 31 * hash + GenericTypeUtils.typeHashCode(argument);
        }
        return hash;
    }

    private static GenericTypeObj requireDeclaration(GenericTypeObj declaration) {
        if (declaration == null)
            throw new IllegalArgumentException("A generic application needs a declaration");
        return declaration;
    }
}