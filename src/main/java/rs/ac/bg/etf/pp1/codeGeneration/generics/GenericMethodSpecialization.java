package rs.ac.bg.etf.pp1.codeGeneration.generics;

import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.List;
import java.util.Map;

/**
 * Represents a fully closed method definition produced from a generic method declaration.
 */
public final class GenericMethodSpecialization extends GenericSpecialization<GenericMethodObj> {
    private final List<Struct> ownerTypeArguments;
    private final List<Struct> methodTypeArguments;

    public GenericMethodSpecialization(GenericMethodObj declaration, List<Struct> ownerTypeArguments,
                                       List<Struct> methodTypeArguments, String generatedName) {
        super(declaration, createSubstitutionMap(declaration, ownerTypeArguments, methodTypeArguments));
        this.ownerTypeArguments = List.copyOf(ownerTypeArguments);
        this.methodTypeArguments = List.copyOf(methodTypeArguments);
        setGeneratedObject(copyObject(declaration, generatedName));
    }

    public List<Struct> getOwnerTypeArguments() {
        return ownerTypeArguments;
    }

    public List<Struct> getMethodTypeArguments() {
        return methodTypeArguments;
    }

    private static Map<GenericParameterStruct, Struct> createSubstitutionMap(
            GenericMethodObj declaration, List<Struct> ownerArguments, List<Struct> methodArguments) {
        var ownerSubstitution = Map.<GenericParameterStruct, Struct>of();
        if (declaration.getOwner() instanceof GenericTypeObj genericOwner)
            ownerSubstitution = genericOwner.validateAndCreateSubstitution(ownerArguments);
        return declaration.validateAndCreateSubstitution(methodArguments, ownerSubstitution);
    }
}
