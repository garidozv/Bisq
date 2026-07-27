package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.List;

import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.etf.pp1.symboltable.concepts.Struct;

/**
 * Represents a fully closed method definition produced from a generic method declaration.
 */
public final class GenericMethodSpecialization extends GenericSpecialization<GenericMethodObj> {
    public GenericMethodSpecialization(GenericMethodObj declaration, List<Struct> typeArguments) {
        super(declaration, typeArguments);
        setGeneratedObject(copyObject(declaration));
    }
}