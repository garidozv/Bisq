package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

public abstract class GenericSpecialization<TDeclaration extends GenericObj> {
    private final TDeclaration declaration;
    private final Map<GenericParameterStruct, Struct> substitutionMap;
    // The generated declaration Obj with closed types
    private Obj generatedObject;
    // A map of generated symbols with closed types
    private final IdentityHashMap<Obj, Obj> generatedSymbolsMap = new IdentityHashMap<>();
    // A map of specializations for each generic method call and generic type creation inside the current generic definition
    private final IdentityHashMap<SyntaxNode, GenericSpecialization<?>> targetSpecializations = new IdentityHashMap<>();

    protected GenericSpecialization(TDeclaration declaration, List<Struct> typeArguments) {
        this(declaration, declaration.validateAndCreateSubstitution(typeArguments));
    }

    protected GenericSpecialization(TDeclaration declaration, Map<GenericParameterStruct, Struct> substitutionMap) {
        this.declaration = declaration;
        for (var typeArgument : substitutionMap.values()) {
            if (!GenericTypeUtils.isClosed(typeArgument))
                throw new IllegalArgumentException("Generic specializations require closed type arguments");
        }
        this.substitutionMap = substitutionMap;
    }

    public final TDeclaration getDeclaration() {
        return declaration;
    }

    public final Obj getGeneratedObject() {
        return generatedObject;
    }

    protected final void setGeneratedObject(Obj generatedObject) {
        this.generatedObject = generatedObject;
    }

    public Struct resolveType(Struct type) {
        var resolved = GenericTypeUtils.substituteType(type, substitutionMap);
        // Not really needed since we check arguments in the constructor, but is here just in case we mess something up
        if (!GenericTypeUtils.isClosed(resolved))
            throw new IllegalStateException("A generic specialization contains an open type");
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

        return copyObject(object);
    }

    protected final Obj copyObject(Obj original) {
        return copyObject(original, original.getName());
    }

    protected final Obj copyObject(Obj original, String name) {
        var copy = new Obj(original.getKind(), name, resolveType(original.getType()), original.getAdr(), original.getLevel());
        copy.setFpPos(original.getFpPos());
        generatedSymbolsMap.put(original, copy);

        if (!original.getLocalSymbols().isEmpty()) {
            var generatedLocals = new HashTableDataStructure();
            for (var local : original.getLocalSymbols()) {
                generatedLocals.insertKey(copyObject(local));
            }
            copy.setLocals(generatedLocals);
        }
        return copy;
    }

    /**
     * Gets the target specialization for the given generic use that is present inside the generic definition that this class specializes.
     * <p>
     * For example, if we have a call to some generic method within another generic method, and the type arguments from the
     * enclosing method are passed to the inner call. What specialization will be generated and used for the inner method call
     * depends on the specification of the enclosing generic method.
     * </p>
     */
    public GenericSpecialization<?> getTargetSpecialization(SyntaxNode use) {
        return targetSpecializations.get(use);
    }

    /**
     * Sets the target specialization for the given generic use that is present inside this generic definition.
     */
    public void setTargetSpecialization(SyntaxNode use, GenericSpecialization<?> target) {
        targetSpecializations.put(use, target);
    }
}