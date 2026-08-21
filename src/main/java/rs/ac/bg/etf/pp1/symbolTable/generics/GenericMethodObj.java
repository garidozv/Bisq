package rs.ac.bg.etf.pp1.symbolTable.generics;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.List;

/**
 * The symbol for a generic method declaration.
 */
public final class GenericMethodObj extends GenericObj {
    private final Obj owner;

    public GenericMethodObj(String name, Struct returnType, List<Obj> typeParameters) {
        this(name, returnType, typeParameters, null, List.of());
    }

    public GenericMethodObj(String name, Struct returnType, List<Obj> typeParameters, Obj owner, List<Obj> enclosingTypeParameters) {
        super(Obj.Meth, name, returnType, typeParameters, enclosingTypeParameters);
        this.owner = owner;
    }

    public Obj getOwner() {
        return owner;
    }

    public boolean isMemberMethod() {
        return owner != null;
    }
}
