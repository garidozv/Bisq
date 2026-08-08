package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.List;

import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

/**
 * Represents one fully closed class or interface definition produced from a generic type declaration.
 */
public final class GenericTypeSpecialization extends GenericSpecialization<GenericTypeObj> {
    public GenericTypeSpecialization(GenericTypeObj declaration, List<Struct> typeArguments) {
        super(declaration, typeArguments);

        var declarationType = declaration.getType();
        var generatedStruct = new Struct(declarationType.getKind());
        generatedStruct.setElementType(resolveType(declarationType.getElemType()));
        for (var implemented : declarationType.getImplementedInterfaces()) {
            generatedStruct.addImplementedInterface(resolveType(implemented));
        }

        var generatedMembers = new HashTableDataStructure();
        for (var member : declarationType.getMembers()) {
            // Generic member methods have to be specialized separately
            if (member instanceof GenericMethodObj) continue;
            generatedMembers.insertKey(copyObject(member));
        }
        generatedStruct.setMembers(generatedMembers);

        setGeneratedObject(new Obj(Obj.Type, declaration.getName(), generatedStruct, declaration.getAdr(), declaration.getLevel()));
    }
}