package rs.ac.bg.etf.pp1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeApplicationStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static rs.ac.bg.etf.pp1.symbolTable.TabUtils.THIS_VARIABLE_NAME;

class GenericsTypeSystemTest {
    @BeforeEach
    void initializeSymbolTable() {
        TabUtils.init();
    }

    @Test
    void parameterIdentityAndConstraintsControlTypeRelations() {
        var base = classWithFields("baseField");
        var child = classWithFields("baseField", "childField");
        child.setElementType(base);
        var implementedInterface = new Struct(Struct.Interface);
        child.addImplementedInterface(implementedInterface);

        var first = new GenericParameterStruct(child);
        var second = new GenericParameterStruct(child);
        var unbounded = new GenericParameterStruct();

        assertFalse(first.equals(second));
        assertFalse(first.compatibleWith(second));
        assertFalse(first.compatibleWith(null));
        assertFalse(first.assignableTo(null));
        assertSame(child, first.getConstraint());
        assertTrue(first.hasConstraint());
        assertFalse(unbounded.hasConstraint());
        assertTrue(first.isRefType());
        assertFalse(unbounded.isRefType());

        // GenericParameterStruct implements only the direct Struct relation.
        assertTrue(first.assignableTo(child));
        assertFalse(first.assignableTo(base));

        // TabUtils is the language-level relation and adds inheritance handling.
        assertTrue(TabUtils.assignableTo(base, first));
        assertTrue(TabUtils.assignableTo(implementedInterface, child));
        assertFalse(TabUtils.assignableTo(first, child));
        assertTrue(TabUtils.assignableTo(first, Tab.nullType));
        assertTrue(TabUtils.compatibleWith(first, Tab.nullType));
        assertFalse(TabUtils.assignableTo(unbounded, Tab.nullType));
        assertFalse(TabUtils.compatibleWith(unbounded, Tab.nullType));
        assertTrue(TabUtils.assignableTo(unbounded, unbounded));
    }

    @Test
    void commonTypesIncludeDirectSupertypesAndNearestCommonBaseClasses() {
        var base = classWithFields("baseField");
        var left = classWithFields("baseField", "leftField");
        var right = classWithFields("baseField", "rightField");
        var unrelated = classWithFields("unrelatedField");
        left.setElementType(base);
        right.setElementType(base);

        assertSame(base, TabUtils.findCommonType(base, left));
        assertSame(base, TabUtils.findCommonType(left, base));
        assertSame(base, TabUtils.findCommonType(left, right));
        assertNull(TabUtils.findCommonType(left, unrelated));
        assertNull(TabUtils.findCommonType(Tab.intType, Tab.charType));
        assertNull(TabUtils.findCommonType(null, left));
    }

    @Test
    void commonTypesIncludeImplementedInterfaces() {
        var sharedInterface = interfaceWithMethods("sharedMethod");
        var first = classWithFields("firstField");
        var second = classWithFields("secondField");
        first.addImplementedInterface(sharedInterface);
        second.addImplementedInterface(sharedInterface);

        assertSame(sharedInterface, TabUtils.findCommonType(first, second));
    }

    @Test
    void constraintsAreLimitedToClassesAndInterfaces() {
        var interfaceType = new Struct(Struct.Interface);
        var interfaceParameter = new GenericParameterStruct(interfaceType);

        assertTrue(TabUtils.isRefType(interfaceType));
        assertTrue(interfaceParameter.isRefType());
        assertTrue(interfaceParameter.compatibleWith(Tab.nullType));
        assertTrue(TabUtils.assignableTo(interfaceParameter, Tab.nullType));

        assertThrows(IllegalArgumentException.class, () -> new GenericParameterStruct(Tab.intType));
        assertThrows(IllegalArgumentException.class, () -> new GenericParameterStruct(new Struct(Struct.Array, Tab.intType)));
        assertThrows(IllegalArgumentException.class, () -> new GenericParameterStruct(interfaceParameter));
    }

    @Test
    void appliedTypesAreNominalInvariantAndImmutable() {
        var parameter = TabUtils.createGenericParameter("T");
        var box = TabUtils.createGenericType("Box", Struct.Class, List.of(parameter));

        List<Struct> mutableArguments = new ArrayList<>(List.of(Tab.intType));
        var intBox = box.applyArguments(mutableArguments);
        mutableArguments.set(0, Tab.charType);
        var anotherIntBox = box.applyArguments(List.of(Tab.intType));
        var charBox = box.applyArguments(List.of(Tab.charType));

        assertEquals(intBox, anotherIntBox);
        assertEquals(intBox.hashCode(), anotherIntBox.hashCode());
        assertNotEquals(intBox, charBox);
        assertTrue(TabUtils.equals(intBox, anotherIntBox));
        assertTrue(TabUtils.assignableTo(intBox, anotherIntBox));
        assertFalse(TabUtils.assignableTo(intBox, charBox));
        assertFalse(TabUtils.assignableTo(charBox, intBox));
        assertSame(Tab.intType, intBox.getTypeArguments().getFirst());
        assertSame(box, intBox.getDeclaration());
        assertTrue(intBox.isClosed());
        assertTrue(intBox.isRefType());

        assertEquals(Struct.Class, box.getType().getKind());
        assertFalse(box.getType() instanceof GenericTypeApplicationStruct);

        var openBox = GenericTypeUtils.createOpenApplication(box);
        assertTrue(openBox.isOpen());
        assertSame(box, openBox.getDeclaration());
        assertSame(parameter.getType(), openBox.getTypeArguments().getFirst());

        var genericInterface = TabUtils.createGenericType("Source", Struct.Interface, List.of(TabUtils.createGenericParameter("E")));
        assertTrue(genericInterface.applyArguments(List.of(Tab.charType)).isRefType());

        // Struct.equals is structural for classes; the utility must give an applied generic type precedence so the
        // two representations do not compare equal merely because both have an empty class layout.
        assertFalse(TabUtils.equals(new Struct(Struct.Class), intBox));
        assertFalse(TabUtils.equals(intBox, new Struct(Struct.Class)));
    }

    @Test
    void typeArgumentValidationRecursesThroughArraysAndApplications() {
        var element = TabUtils.createGenericParameter("E");
        var list = TabUtils.createGenericType("List", Struct.Class, List.of(element));
        var listOfInt = list.applyArguments(List.of(Tab.intType));

        assertTrue(GenericTypeUtils.isValidTypeArgument(Tab.intType));
        assertTrue(GenericTypeUtils.isValidTypeArgument(TabUtils.setType));
        assertTrue(GenericTypeUtils.isValidTypeArgument(listOfInt));
        assertTrue(GenericTypeUtils.isValidTypeArgument(new Struct(Struct.Array, listOfInt)));
        assertFalse(GenericTypeUtils.isValidTypeArgument(null));
        assertFalse(GenericTypeUtils.isValidTypeArgument(Tab.noType));
        assertFalse(GenericTypeUtils.isValidTypeArgument(Tab.nullType));
        assertFalse(GenericTypeUtils.isValidTypeArgument(new Struct(Struct.Array, TabUtils.setType)));
        assertFalse(GenericTypeUtils.isValidTypeArgument(new Struct(Struct.Array, new Struct(Struct.Array, TabUtils.setType))));
    }

    @Test
    void containedParametersAreCollectedRecursivelyAndSubstitutionRebuildsApplications() {
        var listParameter = TabUtils.createGenericParameter("E");
        var list = TabUtils.createGenericType("List", Struct.Class, List.of(listParameter));
        var pairFirst = TabUtils.createGenericParameter("A");
        var pairSecond = TabUtils.createGenericParameter("B");
        var pair = TabUtils.createGenericType("Pair", Struct.Class, List.of(pairFirst, pairSecond));

        var tObject = TabUtils.createGenericParameter("T");
        var uObject = TabUtils.createGenericParameter("U");
        var t = (GenericParameterStruct) tObject.getType();
        var u = (GenericParameterStruct) uObject.getType();
        var nested = pair.applyArguments(List.of(Tab.intType, u));
        var composite = pair.applyArguments(List.of(list.applyArguments(List.of(t)), nested));

        var contained = GenericTypeUtils.getContainedTypeParameters(composite);
        assertEquals(2, contained.size());
        assertTrue(contained.contains(t));
        assertTrue(contained.contains(u));

        Map<GenericParameterStruct, Struct> substitutions = new HashMap<>();
        substitutions.put(t, Tab.charType);
        substitutions.put(u, Tab.intType);
        var substituted = GenericTypeUtils.substituteType(composite, substitutions);

        assertInstanceOf(GenericTypeApplicationStruct.class, substituted);
        var substitutedPair = (GenericTypeApplicationStruct) substituted;
        assertEquals(Tab.charType, ((GenericTypeApplicationStruct) substitutedPair.getTypeArguments().get(0)).getTypeArguments().getFirst());
        var substitutedNested = (GenericTypeApplicationStruct) substitutedPair.getTypeArguments().get(1);
        assertEquals(Tab.intType, substitutedNested.getTypeArguments().get(1));
        assertSame(composite, GenericTypeUtils.substituteType(composite, Map.of()));

        var array = GenericTypeUtils.createArrayType(t);
        var substitutedArray = GenericTypeUtils.substituteType(array, substitutions);
        assertEquals(Struct.Array, substitutedArray.getKind());
        assertSame(Tab.charType, substitutedArray.getElemType());
    }

    @Test
    void dependentBoundsUseEarlierArgumentsAndAllowConcreteBounds() {
        var element = TabUtils.createGenericParameter("E");
        var container = TabUtils.createGenericType("Container", Struct.Interface, List.of(element));

        var t = TabUtils.createGenericParameter("T");
        Struct containerOfT = container.applyArguments(List.of(t.getType()));
        var u = TabUtils.createGenericParameter("U", containerOfT);
        var marker = new Struct(Struct.Interface);
        var result = TabUtils.createGenericParameter("R", marker);
        var method = TabUtils.createGenericMethod("convert", result.getType(), List.of(t, u, result));

        method.validateAndCreateSubstitution(List.of(Tab.intType, container.applyArguments(List.of(Tab.intType)), marker));
        assertThrows(IllegalArgumentException.class, () ->
                method.validateAndCreateSubstitution(List.of(Tab.intType, container.applyArguments(List.of(Tab.charType)), marker)));

        var forward = TabUtils.createGenericParameter("U", containerOfT);
        assertThrows(IllegalArgumentException.class, () -> TabUtils.createGenericMethod("forward", Tab.noType, List.of(forward, t)));

        var self = TabUtils.createGenericParameter("S");
        var recursive = TabUtils.createGenericType("Recursive", Struct.Interface, List.of(element));
        var selfWithBound = TabUtils.createGenericParameter("S", recursive.applyArguments(List.of(self.getType())));
        assertThrows(IllegalArgumentException.class, () -> TabUtils.createGenericMethod("recursive", Tab.noType, List.of(selfWithBound)));
    }

    @Test
    void genericInheritanceCanBeSubstitutedParentFirst() {
        var baseParameter = TabUtils.createGenericParameter("T");
        var base = TabUtils.createGenericType("Base", Struct.Class, List.of(baseParameter));
        var keyParameter = TabUtils.createGenericParameter("K");
        var valueParameter = TabUtils.createGenericParameter("V");
        var pair = TabUtils.createGenericType("Pair", Struct.Class, List.of(keyParameter, valueParameter));
        pair.getType().setElementType(base.applyArguments(List.of(valueParameter.getType())));

        var pairOfIntAndChar = pair.applyArguments(List.of(Tab.intType, Tab.charType));
        Map<GenericParameterStruct, Struct> substitutions = new HashMap<>();
        substitutions.put((GenericParameterStruct) keyParameter.getType(), Tab.intType);
        substitutions.put((GenericParameterStruct) valueParameter.getType(), Tab.charType);
        var closedBase = GenericTypeUtils.substituteType(pair.getType().getElemType(), substitutions);

        assertTrue(pairOfIntAndChar.isClosed());
        assertInstanceOf(GenericTypeApplicationStruct.class, closedBase);
        assertSame(base, ((GenericTypeApplicationStruct) closedBase).getDeclaration());
        assertEquals(List.of(Tab.charType), ((GenericTypeApplicationStruct) closedBase).getTypeArguments());
    }

    @Test
    void appliedTypesExposeMembersWithSubstitutedTypes() {
        var parameter = TabUtils.createGenericParameter("T");
        var base = TabUtils.createGenericType("Base", Struct.Class, List.of(parameter));
        var members = new HashTableDataStructure();
        members.insertKey(new Obj(Obj.Fld, "value", parameter.getType()));
        members.insertKey(new Obj(Obj.Meth, "get", parameter.getType()));
        base.getType().setMembers(members);

        var applied = base.applyArguments(List.of(Tab.intType));
        var substitutedMembers = applied.getMembers();

        assertEquals(2, substitutedMembers.size());
        assertTrue(substitutedMembers.stream().allMatch(member -> member.getType() == Tab.intType));
        assertSame(parameter.getType(), base.getType().getMembersTable().searchKey("value").getType());
    }

    @Test
    void appliedTypesPreserveGenericMethodDeclarations() {
        var ownerParameter = TabUtils.createGenericParameter("T");
        var methodParameter = TabUtils.createGenericParameter("U");
        var box = TabUtils.createGenericType("Box", Struct.Class, List.of(ownerParameter));
        var method = TabUtils.createGenericMethod("convert", methodParameter.getType(), List.of(methodParameter), box, List.of(ownerParameter));
        var members = new HashTableDataStructure();
        members.insertKey(method);
        box.getType().setMembers(members);

        var application = box.applyArguments(List.of(Tab.intType));

        assertSame(method, application.findMember("convert"));
        assertSame(method, application.getMembers().iterator().next());
    }

    @Test
    void genericSymbolsPreserveDeclarationMetadataAndTabInsertionSemantics() {
        var first = TabUtils.createGenericParameter("T");
        var second = TabUtils.createGenericParameter("U");
        var method = TabUtils.createGenericMethod("identity", first.getType(), List.of(first, second));

        assertEquals(Obj.Meth, method.getKind());
        assertEquals(List.of("T", "U"), method.getTypeParameters().stream().map(Obj::getName).toList());
        assertSame(first.getType(), method.getTypeParameterType(0));
        assertSame(second.getType(), method.getTypeParameterType(1));
        assertSame(method, TabUtils.insert(method));
        assertSame(method, Tab.find("identity"));

        var duplicate = TabUtils.createGenericMethod("identity", Tab.noType, List.of(first));
        assertSame(method, TabUtils.insert(duplicate));
    }

    @Test
    void openMethodArgumentsAreCheckedThroughTheirConstraints() {
        var base = classWithFields("baseField");
        var derived = classWithFields("baseField", "derivedField");
        derived.setElementType(base);

        var required = TabUtils.createGenericParameter("T", base);
        var method = TabUtils.createGenericMethod("accept", Tab.noType, List.of(required));
        var sufficientlyConstrained = TabUtils.createGenericParameter("D", derived);
        var unbounded = TabUtils.createGenericParameter("U");

        var substitution = method.validateAndCreateSubstitution(List.of(sufficientlyConstrained.getType()));
        assertSame(sufficientlyConstrained.getType(), substitution.get((GenericParameterStruct) required.getType()));
        assertThrows(IllegalArgumentException.class, () -> method.validateAndCreateSubstitution(List.of(unbounded.getType())));

        var derivedRequired = TabUtils.createGenericMethod("derived", Tab.noType, List.of(TabUtils.createGenericParameter("T", derived)));
        var onlyBase = TabUtils.createGenericParameter("D", base);
        assertThrows(IllegalArgumentException.class, () -> derivedRequired.validateAndCreateSubstitution(List.of(onlyBase.getType())));
    }

    @Test
    void ordinaryMemberMethodsUseTheirGenericOwnerSubstitution() {
        var ownerParameter = TabUtils.createGenericParameter("T");
        var owner = TabUtils.createGenericType("Box", Struct.Class, List.of(ownerParameter));
        var method = new Obj(Obj.Meth, "get", ownerParameter.getType());
        var ownerApplication = owner.applyArguments(List.of(Tab.intType));

        assertEquals(Obj.Meth, method.getKind());
        assertSame(Obj.class, method.getClass());
        assertSame(Tab.intType, GenericTypeUtils.substituteType(method.getType(), ownerApplication.getSubstitution()));
    }

    @Test
    void objectTypesAreSubstitutedWithoutChangingOriginalSymbols() {
        var parameter = TabUtils.createGenericParameter("T");
        var box = TabUtils.createGenericType("Box", Struct.Class, List.of(parameter));
        var parameterType = parameter.getType();

        var method = new Obj(Obj.Meth, "replace", parameterType, 17, 2);
        method.setFpPos(TabUtils.MethodTypes.LOCAL.value);
        var originalLocals = new HashTableDataStructure();
        originalLocals.insertKey(new Obj(Obj.Var, THIS_VARIABLE_NAME, GenericTypeUtils.createOpenApplication(box), 0, 1));
        originalLocals.insertKey(new Obj(Obj.Var, "value", parameterType, 1, 1));
        originalLocals.insertKey(new Obj(Obj.Var, "values", new Struct(Struct.Array, parameterType), 2, 1));
        method.setLocals(originalLocals);

        var application = box.applyArguments(List.of(Tab.intType));
        var substituted = GenericTypeUtils.substituteObjectTypes(method, application.getSubstitution());
        var substitutedLocals = substituted.getLocalSymbols().stream().collect(java.util.stream.Collectors.toMap(Obj::getName, Obj::getType));

        assertNotSame(method, substituted);
        assertEquals(method.getName(), substituted.getName());
        assertEquals(method.getKind(), substituted.getKind());
        assertEquals(method.getAdr(), substituted.getAdr());
        assertEquals(method.getLevel(), substituted.getLevel());
        assertEquals(method.getFpPos(), substituted.getFpPos());
        assertSame(Tab.intType, substituted.getType());
        assertEquals(box.applyArguments(List.of(Tab.intType)), substitutedLocals.get(THIS_VARIABLE_NAME));
        assertSame(Tab.intType, substitutedLocals.get("value"));
        assertSame(Tab.intType, substitutedLocals.get("values").getElemType());

        assertSame(parameterType, method.getType());
        assertSame(parameterType, method.getLocalSymbols().stream()
                .filter(local -> local.getName().equals("value")).findFirst().orElseThrow().getType());
    }

    @Test
    void appliedGenericInheritanceSubstitutesArgumentsAutomatically() {
        var baseParameter = TabUtils.createGenericParameter("T");
        var base = TabUtils.createGenericType("Base", Struct.Class, List.of(baseParameter));
        var derivedParameter = TabUtils.createGenericParameter("U");
        var derived = TabUtils.createGenericType("Derived", Struct.Class, List.of(derivedParameter));
        derived.getType().setElementType(base.applyArguments(List.of(derivedParameter.getType())));
        var interfaceParameter = TabUtils.createGenericParameter("V");
        var genericInterface = TabUtils.createGenericType("Consumer", Struct.Interface, List.of(interfaceParameter));
        derived.getType().addImplementedInterface(genericInterface.applyArguments(List.of(derivedParameter.getType())));

        var derivedInt = derived.applyArguments(List.of(Tab.intType));
        assertTrue(TabUtils.assignableTo(base.applyArguments(List.of(Tab.intType)), derivedInt));
        assertFalse(TabUtils.assignableTo(base.applyArguments(List.of(Tab.charType)), derivedInt));
        assertTrue(TabUtils.assignableTo(genericInterface.applyArguments(List.of(Tab.intType)), derivedInt));
        assertFalse(TabUtils.assignableTo(genericInterface.applyArguments(List.of(Tab.charType)), derivedInt));
        assertEquals(base.applyArguments(List.of(Tab.intType)), derivedInt.getElemType());
        assertEquals(List.of(genericInterface.applyArguments(List.of(Tab.intType))), derivedInt.getImplementedInterfaces());
    }

    @Test
    void invalidTypesApplicationsAndDeclarationsAreRejected() {
        var parameter = TabUtils.createGenericParameter("T");
        var box = TabUtils.createGenericType("Box", Struct.Class, List.of(parameter));

        assertThrows(IllegalArgumentException.class, () ->
                TabUtils.createGenericMethod("empty", Tab.noType, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                TabUtils.createGenericMethod("duplicate", Tab.noType, List.of(parameter, TabUtils.createGenericParameter("T"))));
        assertThrows(IllegalArgumentException.class, () ->
                TabUtils.createGenericMethod("wrongKind", Tab.noType, List.of(new Obj(Obj.Var, "T", Tab.intType))));
        assertThrows(IllegalArgumentException.class, () ->
                TabUtils.createGenericMethod("nullParameter", Tab.noType, Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () ->
                TabUtils.createGenericMethod("unnamed", Tab.noType, List.of(TabUtils.createGenericParameter(""))));

        assertThrows(IllegalArgumentException.class, () -> box.applyArguments(List.of()));
        assertThrows(IllegalArgumentException.class, () -> GenericTypeUtils.createOpenApplication(null));
        assertThrows(IllegalArgumentException.class, () -> box.applyArguments(List.of(Tab.noType)));
        assertThrows(IllegalArgumentException.class, () -> box.applyArguments(List.of(Tab.nullType)));
        assertThrows(IllegalArgumentException.class, () -> box.applyArguments(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> GenericTypeUtils.createArrayType(TabUtils.setType));
        assertThrows(IllegalArgumentException.class, () -> GenericTypeUtils.createArrayType(new Struct(Struct.Array, TabUtils.setType)));
        assertEquals(Struct.Array, GenericTypeUtils.createArrayType(Tab.intType).getKind());

        assertThrows(IllegalArgumentException.class, () -> new GenericTypeObj("Bad", Tab.intType, List.of(parameter)));
        assertThrows(IllegalArgumentException.class, () -> new GenericTypeObj("Bad", null, List.of(parameter)));
    }

    private static Struct classWithFields(String... names) {
        var members = new HashTableDataStructure();
        for (var name : names) {
            members.insertKey(new Obj(Obj.Fld, name, Tab.intType));
        }
        return new Struct(Struct.Class, members);
    }

    private static Struct interfaceWithMethods(String... names) {
        var members = new HashTableDataStructure();
        for (var name : names) {
            members.insertKey(new Obj(Obj.Meth, name, Tab.noType));
        }
        return new Struct(Struct.Interface, members);
    }
}
