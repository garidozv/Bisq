package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.pp1.ast.CallableRef_Applied;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

/**
 * Represents a fully closed method definition produced from a generic method declaration.
 */
public final class GenericMethodSpecialization {
    private final GenericMethodObj declaration;
    private final Map<GenericParameterStruct, Struct> substitutionMap;
    // The generated method Obj with closed return type
    private final Obj generatedMethod;
    // A map of generated local symbols with closed types
    private final IdentityHashMap<Obj, Obj> generatedSymbolsMap = new IdentityHashMap<>();
    // A map of specializations for each generic method call inside the current generic method
    private final IdentityHashMap<CallableRef_Applied, GenericMethodSpecialization> callTargetsMap = new IdentityHashMap<>();

    public GenericMethodSpecialization(GenericMethodObj declaration, List<Struct> typeArguments) {
        this.declaration = declaration;
        for (var typeArgument : typeArguments) {
            if (!GenericTypeUtils.isClosed(typeArgument))
                throw new IllegalArgumentException("Generic method specializations require closed type arguments");
        }

        substitutionMap = declaration.validateAndCreateSubstitution(typeArguments);
        generatedMethod = copyObject(declaration, resolveType(declaration.getType()));

        var generatedLocals = new HashTableDataStructure();
        for (var symbol : declaration.getLocalSymbols()) {
            var generatedSymbol = copyObject(symbol, resolveType(symbol.getType()));
            generatedLocals.insertKey(generatedSymbol);
            generatedSymbolsMap.put(symbol, generatedSymbol);
        }
        generatedMethod.setLocals(generatedLocals);
    }

    public GenericMethodObj getDeclaration() {
        return declaration;
    }

    public Obj getGeneratedMethod() {
        return generatedMethod;
    }

    public Struct resolveType(Struct type) {
        var resolved = GenericTypeUtils.substitute(type, substitutionMap);
        // Not really needed since we check arguments in the constructor, but is here just in case we mess something up
        if (!GenericTypeUtils.isClosed(resolved))
            throw new IllegalStateException("A generic method specialization contains an open type");
        return resolved;
    }

    public Obj resolveObject(Obj object) {
        if (object == null) return null;

        var generated = generatedSymbolsMap.get(object);
        if (generated != null) return generated;

        // This part is needed to handle temporary objects that are not inside method locals list or in symbol table
        // An example would be a list element, which has a temporary object created for it
        var resolvedType = resolveType(object.getType());
        if (resolvedType == object.getType()) return object;

        generated = copyObject(object, resolvedType);
        generatedSymbolsMap.put(object, generated);
        return generated;
    }

    /**
     * Gets the call target specialization for the given generic method call that is present inside this generic method.
     */
    public GenericMethodSpecialization getCallTargetSpecialization(CallableRef_Applied call) {
        return callTargetsMap.get(call);
    }

    /**
     * Sets the call target specialization for the given generic method call that is present inside this generic method.
     */
    void setCallTargetSpecialization(CallableRef_Applied call, GenericMethodSpecialization target) {
        callTargetsMap.put(call, target);
    }

    private static Obj copyObject(Obj original, Struct type) {
        var copy = new Obj(original.getKind(), original.getName(), type, original.getAdr(), original.getLevel());
        copy.setFpPos(original.getFpPos());
        return copy;
    }
}