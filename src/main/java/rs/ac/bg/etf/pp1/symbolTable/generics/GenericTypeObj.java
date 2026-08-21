package rs.ac.bg.etf.pp1.symbolTable.generics;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.List;

/**
 * The symbol for a generic class or interface declaration.
 */
public final class GenericTypeObj extends GenericObj {
    public GenericTypeObj(String name, Struct type, List<Obj> typeParameters) {
        super(Obj.Type, name, type, typeParameters);

        if (type == null || type instanceof GenericTypeApplicationStruct || (type.getKind() != Struct.Class && type.getKind() != Struct.Interface))
            throw new IllegalArgumentException("Only an ordinary class and interface types can be generic.");
    }

    public GenericTypeApplicationStruct applyArguments(List<Struct> arguments) {
        return new GenericTypeApplicationStruct(this, arguments);
    }
}
