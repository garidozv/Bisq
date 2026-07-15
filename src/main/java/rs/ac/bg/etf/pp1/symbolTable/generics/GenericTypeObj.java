package rs.ac.bg.etf.pp1.symbolTable.generics;

import java.util.List;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

/**
 * The symbol for a generic class or interface declaration.
 */
public final class GenericTypeObj extends GenericObj {
    public GenericTypeObj(String name, Struct type, List<Obj> typeParameters) {
        super(Obj.Type, name, type, typeParameters);

        if (type == null || type instanceof AppliedGenericTypeStruct || (type.getKind() != Struct.Class && type.getKind() != Struct.Interface))
            throw new IllegalArgumentException("Only an ordinary class and interface types can be generic.");
    }

    public AppliedGenericTypeStruct applyArguments(List<Struct> arguments) {
        return new AppliedGenericTypeStruct(this, arguments);
    }
}