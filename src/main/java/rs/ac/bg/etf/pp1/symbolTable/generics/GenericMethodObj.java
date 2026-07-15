package rs.ac.bg.etf.pp1.symbolTable.generics;

import java.util.List;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

/**
 * The symbol for a generic global method declaration.
 */
public final class GenericMethodObj extends GenericObj {
    public GenericMethodObj(String name, Struct returnType, List<Obj> typeParameters) {
        super(Obj.Meth, name, returnType, typeParameters);
    }
}