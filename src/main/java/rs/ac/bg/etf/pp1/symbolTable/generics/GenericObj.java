package rs.ac.bg.etf.pp1.symbolTable.generics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

/**
 * Common symbol table metadata for generic type and method declarations.
 */
public abstract class GenericObj extends Obj {
    private final List<Obj> typeParameters;

    protected GenericObj(int kind, String name, Struct type, List<Obj> typeParameters) {
        super(kind, name, type);
        this.typeParameters = validateTypeParameterDeclarations(typeParameters);
    }

    public final List<Obj> getTypeParameters() {
        return typeParameters;
    }

    public final int getTypeParameterCount() {
        return typeParameters.size();
    }

    public final GenericParameterStruct getTypeParameterType(int index) {
        return (GenericParameterStruct)typeParameters.get(index).getType();
    }

    private static List<Obj> validateTypeParameterDeclarations(List<Obj> parameters) {
        if (parameters == null || parameters.isEmpty())
            throw new IllegalArgumentException("A generic declaration must have at least one type parameter");

        var result = new ArrayList<Obj>(parameters.size());
        var names = new LinkedHashSet<>();
        var preceding = Collections.<GenericParameterStruct>newSetFromMap(new IdentityHashMap<>());

        for (var parameter : parameters) {
            if (parameter == null || parameter.getName() == null || parameter.getName().isBlank() ||
                    parameter.getKind() != Obj.Type || !(parameter.getType() instanceof GenericParameterStruct parameterType))
                throw new IllegalArgumentException("Generic parameters must be 'Type' symbols with parameter types");
            if (!names.add(parameter.getName()))
                throw new IllegalArgumentException("Duplicate generic parameter '" + parameter.getName() + "'");

            for (var typeParam : GenericTypeUtils.getContainedTypeParameters(parameterType.getConstraint())) {
                if (!preceding.contains(typeParam))
                    throw new IllegalArgumentException("A generic constraint may only reference earlier type parameters");
            }

            result.add(parameter);
            preceding.add(parameterType);
        }

        return List.copyOf(result);
    }
}