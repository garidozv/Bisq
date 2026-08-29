package rs.ac.bg.etf.pp1;

import org.junit.jupiter.api.Test;
import rs.ac.bg.etf.pp1.ast.GenericMethodDecl;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenericMethodTest extends CompilerTestBase {
    @Test
    void genericDeclarationUsesTheSameParameterTypesThroughoutItsSignatureAndBody() throws Exception {
        var result = analyze("""
                program Generics {
                    <T> T identity(T value) T local; {
                        local = value;
                        return local;
                    }

                    <E> E[] identityArray(E[] value) {
                        return value;
                    }

                    void main() {}
                }
                """);

        assertTrue(result.analyzer().passed());
        assertEquals(2, result.genericMethods().size());

        var identity = assertInstanceOf(GenericMethodObj.class, result.genericMethods().getFirst().obj);
        var parameterType = identity.getTypeParameterType(0);
        assertSame(parameterType, identity.getType());
        assertEquals(List.of("value", "local"), identity.getLocalSymbols().stream().map(Obj::getName).toList());
        assertSame(parameterType, identity.getLocalSymbols().stream()
                .filter(symbol -> symbol.getName().equals("value"))
                .findFirst().orElseThrow().getType());
        assertTrue(identity.getLocalSymbols().stream().noneMatch(symbol -> symbol.getKind() == Obj.Type));

        var identityArray = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        assertEquals(Struct.Array, identityArray.getType().getKind());
        assertSame(identityArray.getTypeParameterType(0), identityArray.getType().getElemType());
        assertSame(identityArray.getTypeParameterType(0),
                identityArray.getLocalSymbols().stream().findFirst().orElseThrow().getType().getElemType());
    }

    @Test
    void boundedAndMultipleParametersPreserveDeclarationMetadata() throws Exception {
        var result = analyze("""
                program Generics
                class Base {}
                {
                    <T extends Base, U> T first(T value, U ignored) {
                        return value;
                    }

                    void main() {}
                }
                """);

        assertTrue(result.analyzer().passed());
        var method = assertInstanceOf(GenericMethodObj.class, result.genericMethods().getFirst().obj);
        assertEquals(List.of("T", "U"), method.getTypeParameters().stream().map(Obj::getName).toList());

        var bounded = assertInstanceOf(GenericParameterStruct.class, method.getTypeParameterType(0));
        assertTrue(bounded.hasConstraint());
        assertEquals(Struct.Class, bounded.getConstraint().getKind());
        assertFalse(method.getTypeParameterType(1).hasConstraint());
        assertEquals(2, method.getLevel());
    }

    @Test
    void genericMemberMethodKeepsOwnerAndMethodParametersSeparate() throws Exception {
        var result = analyze("""
                program MemberGenerics
                class Box<T> {
                    {
                        <U> T choose(T ownerValue, U methodValue) { return ownerValue; }
                    }
                }
                { void main() {} }
                """);

        assertTrue(result.analyzer().passed());
        var method = assertInstanceOf(GenericMethodObj.class, result.genericMethods().getFirst().obj);
        var owner = assertInstanceOf(GenericTypeObj.class, method.getOwner());
        assertSame(owner.getTypeParameterType(0), method.getType());
        assertEquals(List.of("U"), method.getTypeParameters().stream().map(Obj::getName).toList());
        assertSame(method.getTypeParameterType(0), method.getLocalSymbols().stream()
                .filter(symbol -> symbol.getName().equals("methodValue"))
                .findFirst().orElseThrow().getType());
    }

    @Test
    void genericMemberMethodsAreSpecializedOnlyForCalls() throws Exception {
        var result = analyze("""
                program MemberGenericCalls
                class Box<T> {
                    {
                        <U> T choose(T ownerValue, U methodValue) { return ownerValue; }
                        <V> V unused(V value) { return value; }
                    }
                }
                {
                    void main() Box<int> box; int number; {
                        box = new Box<int>();
                        number = box.choose::<char>(3, 'a');
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var choose = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var unused = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        var plan = result.analyzer().createMonomorphizationPlan();
        var specialization = plan.getNeededSpecializations(choose).getFirst();

        assertEquals(List.of(Tab.intType), specialization.getOwnerTypeArguments());
        assertEquals(List.of(Tab.charType), specialization.getMethodTypeArguments());
        assertTrue(plan.getNeededSpecializations(unused).isEmpty());
    }

    @Test
    void nestedMemberMethodCallsCloseOwnerAndMethodArguments() throws Exception {
        var result = analyze("""
                program NestedMemberCalls
                class Box<T> {
                    {
                        <U> U echo(U value) { return value; }
                        <V> V forward(V value) { return this.echo::<V>(value); }
                    }
                }
                {
                    void main() Box<int> box; int value; {
                        box = new Box<int>();
                        value = box.forward::<int>(7);
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var echo = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var forward = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        var plan = result.analyzer().createMonomorphizationPlan();

        assertEquals(1, plan.getNeededSpecializations(forward).size());
        assertEquals(List.of(Tab.intType), plan.getNeededSpecializations(echo).getFirst().getOwnerTypeArguments());
        assertEquals(List.of(Tab.intType), plan.getNeededSpecializations(echo).getFirst().getMethodTypeArguments());
    }

    @Test
    void genericMethodOverridesCompareOwnerAndMethodParametersStructurally() throws Exception {
        var valid = analyze("""
                program GenericOverrides
                class Left {}
                class Right {}
                class Pair<A, B> {}
                class Base<T extends Left, U extends Right> {
                    {
                        <V extends Pair<T, U>, W extends Right> V choose(V first, W second) { return first; }
                    }
                }
                class Derived<T extends Left, U extends Right> extends Base<T, U> {
                    {
                        <X extends Pair<T, U>, Y extends Right> X choose(X first, Y second) { return first; }
                    }
                }
                { void main() {} }
                """);

        assertTrue(valid.analyzer().passed());

        var invalidConstraint = analyze("""
                program InvalidGenericOverride
                class Left {}
                class Right {}
                class Pair<A, B> {}
                class Base<T extends Left, U extends Right> {
                    {
                        <V extends Pair<T, U>, W extends Right> V choose(V first, W second) { return first; }
                    }
                }
                class Derived<T extends Left, U extends Right> extends Base<T, U> {
                    {
                        <X extends Pair<T, U>, Y> X choose(X first, Y second) { return first; }
                    }
                }
                { void main() {} }
                """);

        assertFalse(invalidConstraint.analyzer().passed());
    }

    @Test
    void genericMethodOverridesMustPreserveTheirGenericShapeAndTypeRequirements() throws Exception {
        assertFalse(analyze("""
                program DifferentParameterCount
                class Base {
                    { <T, U> T select(T first, U second) { return first; } }
                }
                class Derived extends Base {
                    { <T> T select(T first, T second) { return first; } }
                }
                { void main() {} }
                """).analyzer().passed());

        assertFalse(analyze("""
                program GenericToOrdinary
                class Base {
                    { <T> T identity(T value) { return value; } }
                }
                class Derived extends Base {
                    { int identity(int value) { return value; } }
                }
                { void main() {} }
                """).analyzer().passed());

        assertFalse(analyze("""
                program DifferentArrayRequirement
                class Base {
                    {
                        <T> T copy(T value) T values[]; {
                            values = new T[1];
                            return value;
                        }
                    }
                }
                class Derived extends Base {
                    { <U> U copy(U value) { return value; } }
                }
                { void main() {} }
                """).analyzer().passed());
    }

    @Test
    void overridingGenericMethodsReuseVirtualNamesAndAreSpecializedForDerivedOwners() throws Exception {
        var result = analyze("""
                program GenericOverridePlan
                class Base<T> {
                    {
                        <U> int value(T ownerValue, U methodValue) { return 1; }
                    }
                }
                class Derived<T> extends Base<T[]> {
                    {
                        <V> int value(T[] ownerValue, V methodValue) { return 2; }
                    }
                }
                {
                    void main() Base<int[]> value; int ownerValues[]; {
                        value = new Derived<int>();
                        print(value.value::<char>(ownerValues, 'a'));
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var baseMethod = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var overridingMethod = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        var plan = result.analyzer().createMonomorphizationPlan();
        var baseSpecialization = plan.getNeededSpecializations(baseMethod).getFirst();
        var overridingSpecialization = plan.getNeededSpecializations(overridingMethod).getFirst();

        assertEquals(List.of(GenericTypeUtils.createArrayType(Tab.intType)), baseSpecialization.getOwnerTypeArguments());
        assertEquals(List.of(Tab.intType), overridingSpecialization.getOwnerTypeArguments());
        assertEquals(List.of(Tab.charType), overridingSpecialization.getMethodTypeArguments());
        assertEquals(baseSpecialization.getGeneratedObject().getName(), overridingSpecialization.getGeneratedObject().getName());
    }

    @Test
    void virtualCompletionIgnoresUnrelatedMethodsWithTheSameName() throws Exception {
        var result = analyze("""
                program UnrelatedGenericMethods
                class Base {
                    { <T> int value(T argument) { return 1; } }
                }
                class Derived extends Base {
                    { <T> int value(T argument) { return 2; } }
                }
                class Unrelated {
                    { <T> int value(T argument) { return 3; } }
                }
                {
                    void main() Base object; {
                        object = new Derived();
                        print(object.value::<int>(1));
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var baseMethod = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var derivedMethod = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        var unrelatedMethod = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(2).obj);
        var plan = result.analyzer().createMonomorphizationPlan();

        var baseSpecialization = plan.getNeededSpecializations(baseMethod).getFirst();
        var derivedSpecialization = plan.getNeededSpecializations(derivedMethod).getFirst();
        assertEquals(baseSpecialization.getGeneratedObject().getName(), derivedSpecialization.getGeneratedObject().getName());
        assertTrue(plan.getNeededSpecializations(unrelatedMethod).isEmpty());
    }

    @Test
    void baseDemandReachesTransitiveOverridesAndProcessesTheirNestedUses() throws Exception {
        var result = analyze("""
                program TransitiveGenericOverrides
                class Base {
                    { <T> int value(T argument) { return 1; } }
                }
                class Middle extends Base {
                    {
                        <T> int helper(T argument) { return 2; }
                        <T> int value(T argument) { return this.helper::<T>(argument); }
                    }
                }
                class Leaf extends Middle {
                    {
                        <T> int helper(T argument) { return 3; }
                        <T> int value(T argument) { return this.helper::<T>(argument); }
                    }
                }
                {
                    void main() Base object; {
                        object = new Leaf();
                        print(object.value::<int>(1));
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var baseValue = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var middleHelper = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        var middleValue = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(2).obj);
        var leafHelper = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(3).obj);
        var leafValue = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(4).obj);
        var plan = result.analyzer().createMonomorphizationPlan();

        var generatedName = plan.getNeededSpecializations(baseValue).getFirst().getGeneratedObject().getName();
        assertEquals(generatedName, plan.getNeededSpecializations(middleValue).getFirst().getGeneratedObject().getName());
        assertEquals(generatedName, plan.getNeededSpecializations(leafValue).getFirst().getGeneratedObject().getName());
        assertEquals(1, plan.getNeededSpecializations(middleHelper).size());
        assertEquals(1, plan.getNeededSpecializations(leafHelper).size());
    }

    @Test
    void memberMethodConstraintsAreCheckedAfterSubstitutingTheOwnerArguments() throws Exception {
        var valid = analyze("""
                program ValidMemberConstraint
                class Holder<T> {}
                class Box<T> {
                    {
                        <U extends Holder<T>> U keep(U value) { return value; }
                    }
                }
                {
                    void main() Box<int> box; Holder<int> value; {
                        box = new Box<int>();
                        value = box.keep::<Holder<int>>(value);
                    }
                }
                """);

        assertTrue(valid.analyzer().passed());

        var invalid = analyze("""
                program InvalidMemberConstraint
                class Holder<T> {}
                class Box<T> {
                    {
                        <U extends Holder<T>> U keep(U value) { return value; }
                    }
                }
                {
                    void main() Box<char> box; Holder<int> value; {
                        box = new Box<char>();
                        value = box.keep::<Holder<int>>(value);
                    }
                }
                """);

        assertFalse(invalid.analyzer().passed());
    }

    @Test
    void invalidGenericParameterDeclarationsAreReportedWithoutCrashingAnalysis() throws Exception {
        assertFalse(analyze("""
                program Duplicate {
                    <T, T> T duplicate(T value) { return value; }
                    void main() {}
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program PrimitiveBound {
                    <T extends int> T invalid(T value) { return value; }
                    void main() {}
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program UndefinedBound {
                    <T extends Missing> T invalid(T value) { return value; }
                    void main() {}
                }
                """).analyzer().passed());
    }

    @Test
    void genericMainDoesNotSatisfyTheProgramEntryPointRequirement() throws Exception {
        var result = analyze("""
                program GenericMain {
                    <T> void main() {}
                }
                """);

        assertFalse(result.analyzer().passed());
    }

    @Test
    void explicitApplicationsSubstituteClosedAndOpenTypes() throws Exception {
        var result = analyze("""
                program Calls {
                    <T> T identity(T value) { return value; }

                    <E> E[] identityArray(E[] value) { return value; }

                    <D> D forward(D value) {
                        return identity::<D>(value);
                    }

                    <U> void consume(U value) {}

                    void main() int result; int values[]; {
                        result = identity::<int>(3);
                        values = identityArray::<int>(values);
                        consume::<char>('a');
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
    }

    @Test
    void methodTypeArgumentsAreInferredFromCallArguments() throws Exception {
        var result = analyze("""
                program InferredCalls
                class Box<T> {
                    {
                        <U> U choose(U value) { return value; }
                    }
                }
                {
                    <T> T identity(T value) { return value; }
                    <E> E[] singleton(E value) E values[]; {
                        values = new E[1];
                        values[0] = value;
                        return values;
                    }

                    void main() int number; int numbers[]; char letter; Box<int> box; {
                        number = identity(3);
                        numbers = singleton(7);
                        box = new Box<int>();
                        letter = box.choose('a');
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var plan = result.analyzer().createMonomorphizationPlan();
        var choose = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var identity = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        var singleton = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(2).obj);

        assertEquals(List.of(Tab.charType), plan.getNeededSpecializations(choose).getFirst().getMethodTypeArguments());
        assertEquals(List.of(Tab.intType), plan.getNeededSpecializations(identity).getFirst().getMethodTypeArguments());
        assertEquals(List.of(Tab.intType), plan.getNeededSpecializations(singleton).getFirst().getMethodTypeArguments());
    }

    @Test
    void inferredOpenArgumentsAreClosedByTheEnclosingSpecialization() throws Exception {
        var result = analyze("""
                program NestedInference {
                    <T> T identity(T value) { return value; }

                    <U> U forward(U value) {
                        return identity(value);
                    }

                    void main() int value; {
                        value = forward(3);
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var plan = result.analyzer().createMonomorphizationPlan();
        var identity = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var forward = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);

        assertEquals(List.of(Tab.intType), plan.getNeededSpecializations(identity).getFirst().getMethodTypeArguments());
        assertEquals(List.of(Tab.intType), plan.getNeededSpecializations(forward).getFirst().getMethodTypeArguments());
    }

    @Test
    void inferenceFindsTheNearestCommonBaseClass() throws Exception {
        var result = analyze("""
                program CommonBaseInference
                class Base { int baseValue; }
                class Left extends Base { int leftValue; }
                class Right extends Base { char rightValue; }
                {
                    <T extends Base, U> T choose(T first, T second, U ignored) {
                        return first;
                    }

                    void main() Left left; Right right; Base result; {
                        result = choose(left, right, 'a');
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var plan = result.analyzer().createMonomorphizationPlan();
        var choose = assertInstanceOf(GenericMethodObj.class, result.genericMethods().getFirst().obj);
        var specialization = plan.getNeededSpecializations(choose).getFirst();

        assertSame(choose.getTypeParameterType(0).getConstraint(), specialization.getMethodTypeArguments().get(0));
        assertSame(Tab.charType, specialization.getMethodTypeArguments().get(1));
    }

    @Test
    void inferenceFindsACommonImplementedInterface() throws Exception {
        var result = analyze("""
                program CommonInterfaceInference
                interface Printable {
                    void printMe();
                }
                class Left extends Printable {
                    int leftValue;
                    { void printMe() {} }
                }
                class Right extends Printable {
                    char rightValue;
                    { void printMe() {} }
                }
                {
                    <T extends Printable> T choose(T first, T second) {
                        return first;
                    }

                    void main() Left left; Right right; Printable result; {
                        result = choose(left, right);
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var plan = result.analyzer().createMonomorphizationPlan();
        var choose = assertInstanceOf(GenericMethodObj.class, result.genericMethods().getFirst().obj);

        var inferredInterface = plan.getNeededSpecializations(choose).getFirst().getMethodTypeArguments().getFirst();
        assertEquals(Struct.Interface, inferredInterface.getKind());
    }

    @Test
    void inferredArgumentsMustBeConsistentCompleteAndSatisfyConstraints() throws Exception {
        assertFalse(analyze("""
                program ConflictingInference {
                    <T> void acceptSame(T first, T second) {}
                    void main() { acceptSame(1, 'a'); }
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program IncompleteInference {
                    <T> void consumeNothing() {}
                    void main() { consumeNothing(); }
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program InferredConstraint
                class Base {}
                {
                    <T extends Base> void consume(T value) {}
                    void main() { consume('a'); }
                }
                """).analyzer().passed());
    }

    @Test
    void monomorphizationPlanClosesOpenUsesAndDeduplicatesSpecializations() throws Exception {
        var result = analyze("""
                program Plan {
                    <T> T identity(T value) { return value; }

                    <U> U forward(U value) {
                        return identity::<U>(value);
                    }

                    <V> V unused(V value) { return value; }

                    void main() int number; char letter; {
                        number = identity::<int>(1);
                        letter = identity::<char>('a');
                        number = forward::<int>(2);
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        var plan = result.analyzer().createMonomorphizationPlan();

        var identity = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(0).obj);
        var forward = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(1).obj);
        var unused = assertInstanceOf(GenericMethodObj.class, result.genericMethods().get(2).obj);
        var identitySpecializations = plan.getNeededSpecializations(identity);
        assertEquals(2, identitySpecializations.size());
        assertEquals(1, plan.getNeededSpecializations(forward).size());
        assertTrue(plan.getNeededSpecializations(unused).isEmpty());

        for (var specialization : identitySpecializations) {
            assertFalse(specialization.getGeneratedObject() instanceof GenericMethodObj);
            assertTrue(GenericTypeUtils.isClosed(specialization.getGeneratedObject().getType()));
            assertTrue(specialization.getGeneratedObject().getLocalSymbols().stream()
                    .allMatch(symbol -> GenericTypeUtils.isClosed(symbol.getType())));
        }
    }

    @Test
    void explicitApplicationsValidateConstraintsForClosedAndOpenArguments() throws Exception {
        var result = analyze("""
                program Bounds
                class Base { int baseField; }
                class Derived extends Base { int derivedField; }
                {
                    <T extends Base> T requireBase(T value) { return value; }

                    <D extends Derived> D forward(D value) {
                        return requireBase::<D>(value);
                    }

                    void main() Derived value; {
                        value = requireBase::<Derived>(value);
                    }
                }
                """);

        assertTrue(result.analyzer().passed());

        var weakBound = analyze("""
                program WeakBound
                class Base { int baseField; }
                class Derived extends Base { int derivedField; }
                {
                    <T extends Derived> T requireDerived(T value) { return value; }

                    <D extends Base> D invalid(D value) {
                        return requireDerived::<D>(value);
                    }
                    void main() {}
                }
                """);
        var requiredDerived = assertInstanceOf(GenericMethodObj.class, weakBound.genericMethods().getFirst().obj);
        var constrainedToBase = assertInstanceOf(GenericMethodObj.class, weakBound.genericMethods().get(1).obj);
        assertFalse(TabUtils.assignableTo(requiredDerived.getTypeParameterType(0).getConstraint(),
                constrainedToBase.getTypeParameterType(0)));
        assertFalse(weakBound.analyzer().passed());
    }

    @Test
    void invalidGenericMethodApplicationsAreRejected() throws Exception {
        assertFalse(analyze("""
                program OrdinaryApplication {
                    int identity(int value) { return value; }
                    void main() int result; { result = identity::<int>(3); }
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program WrongTypeArgumentCount {
                    <T> T identity(T value) { return value; }
                    void main() int result; { result = identity::<int, char>(3); }
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program WrongValueArgument {
                    <T> T identity(T value) { return value; }
                    void main() int result; { result = identity::<int>('a'); }
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program ViolatedConstraint
                class Base {}
                {
                    <T extends Base> T requireBase(T value) { return value; }
                    void main() { requireBase::<char>('a'); }
                }
                """).analyzer().passed());
    }

    @Test
    void constrainedParametersExposeConstraintMembersInsideGenericDefinitions() throws Exception {
        var result = analyze("""
                program ConstraintMembers
                class Base {
                    int value;
                    {
                        int getValue() { return this.value; }
                        Base self() { return this; }
                    }
                }
                class Derived extends Base {
                    int other;
                }
                interface HasValue {
                    int getValue();
                }
                {
                    <T extends Base> int readMethod(T value) {
                        return value.getValue();
                    }

                    <T extends Base> int readField(T value) {
                        return value.value;
                    }

                    <T extends Base> Base chained(T value) {
                        return value.self();
                    }

                    <T extends Derived> int readInherited(T value) {
                        return value.getValue();
                    }

                    <T extends HasValue> int readInterface(T value) {
                        return value.getValue();
                    }

                    void main() {}
                }
                """);

        assertTrue(result.analyzer().passed());
    }

    @Test
    void genericDefinitionsRejectMembersMissingFromTheConstraint() throws Exception {
        assertFalse(analyze("""
                program UnboundedMember {
                    <T> int invalid(T value) {
                        return value.getValue();
                    }
                    void main() {}
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program MissingConstraintMember
                class Base {}
                {
                    <T extends Base> int invalid(T value) {
                        return value.getValue();
                    }
                    void main() {}
                }
                """).analyzer().passed());
    }

    @Test
    void setArgumentsCannotCloseTypesThatUseParametersAsArrayElements() throws Exception {
        assertFalse(analyze("""
                program LocalSetArray {
                    <T> void allocate() T values[]; {
                        values = new T[1];
                    }

                    void main() {
                        allocate::<set>();
                    }
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program NestedSetArray {
                    <T> void consume() {}

                    <U> void relay() {
                        consume::<U[]>();
                    }

                    void main() {
                        relay::<set>();
                    }
                }
                """).analyzer().passed());
    }

    @Test
    void unboundedParametersCannotBeUsedWithOrderedRelationalOperators() throws Exception {
        assertFalse(analyze("""
                program GenericComparison {
                    <T> void compare(T first, T second) {
                        if (first < second) print(1);
                    }

                    void main() {}
                }
                """).analyzer().passed());
    }

    @Test
    void setArgumentsAndEqualityRemainValidWithoutArrayElementRequirements() throws Exception {
        var result = analyze("""
                program ValidGenericOperations {
                    <T> T identity(T value) {
                        return value;
                    }

                    <T> void compare(T first, T second) {
                        if (first == second) print(1);
                    }

                    void main() set value; {
                        value = identity::<set>(value);
                        compare::<set>(value, value);
                    }
                }
                """);

        assertTrue(result.analyzer().passed());
        result.analyzer().createMonomorphizationPlan();
    }

    private static GenericMethodAnalysisResult analyze(String source) throws Exception {
        var analysis = analyzeProgram(source);

        var genericMethods = new ArrayList<GenericMethodDecl>();
        analysis.program().traverseBottomUp(new VisitorAdaptor() {
            @Override
            public void visit(GenericMethodDecl method) {
                genericMethods.add(method);
            }
        });
        return new GenericMethodAnalysisResult(analysis.analyzer(), List.copyOf(genericMethods));
    }

    private record GenericMethodAnalysisResult(SemanticAnalyzer analyzer, List<GenericMethodDecl> genericMethods) {
    }
}
