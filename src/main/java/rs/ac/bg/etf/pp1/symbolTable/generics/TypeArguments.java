package rs.ac.bg.etf.pp1.symbolTable.generics;

import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.ArrayList;
import java.util.List;

public record TypeArguments(List<Struct> types) {
    public TypeArguments(List<Struct> types) {
        this.types = List.copyOf(types);
    }

    public TypeArguments append(Struct type) {
        var result = new ArrayList<>(types);
        result.add(type);
        return new TypeArguments(result);
    }
}
