package rs.ac.bg.etf.pp1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import rs.ac.bg.etf.pp1.ast.GenericMethodDecl;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

class GenericMethodSemanticAnalyzerTest {
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
                    <T : Base, U> T first(T value, U ignored) {
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
    void invalidGenericParameterDeclarationsAreReportedWithoutCrashingAnalysis() throws Exception {
        assertFalse(analyze("""
                program Duplicate {
                    <T, T> T duplicate(T value) { return value; }
                    void main() {}
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program PrimitiveBound {
                    <T : int> T invalid(T value) { return value; }
                    void main() {}
                }
                """).analyzer().passed());

        assertFalse(analyze("""
                program UndefinedBound {
                    <T : Missing> T invalid(T value) { return value; }
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
            assertFalse(specialization.getGeneratedMethod() instanceof GenericMethodObj);
            assertTrue(GenericTypeUtils.isClosed(specialization.getGeneratedMethod().getType()));
            assertTrue(specialization.getGeneratedMethod().getLocalSymbols().stream()
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
                    <T : Base> T requireBase(T value) { return value; }

                    <D : Derived> D forward(D value) {
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
                    <T : Derived> T requireDerived(T value) { return value; }

                    <D : Base> D invalid(D value) {
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
                program MissingTypeArguments {
                    <T> T identity(T value) { return value; }
                    void main() int result; { result = identity(3); }
                }
                """).analyzer().passed());

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
                    <T : Base> T requireBase(T value) { return value; }
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
                    <T : Base> int readMethod(T value) {
                        return value.getValue();
                    }

                    <T : Base> int readField(T value) {
                        return value.value;
                    }

                    <T : Base> Base chained(T value) {
                        return value.self();
                    }

                    <T : Derived> int readInherited(T value) {
                        return value.getValue();
                    }

                    <T : HasValue> int readInterface(T value) {
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
                    <T : Base> int invalid(T value) {
                        return value.getValue();
                    }
                    void main() {}
                }
                """).analyzer().passed());
    }

    private static AnalysisResult analyze(String source) throws Exception {
        TabUtils.init();
        var parser = new MJParser(new Yylex(new StringReader(source)));
        parser.loggingEnabled = false;
        var parsed = parser.parse();
        var program = assertInstanceOf(Program.class, parsed.value);

        var analyzer = new SemanticAnalyzer();
        program.traverseBottomUp(analyzer);

        var genericMethods = new ArrayList<GenericMethodDecl>();
        program.traverseBottomUp(new VisitorAdaptor() {
            @Override
            public void visit(GenericMethodDecl method) {
                genericMethods.add(method);
            }
        });
        return new AnalysisResult(analyzer, List.copyOf(genericMethods));
    }

    private record AnalysisResult(SemanticAnalyzer analyzer, List<GenericMethodDecl> genericMethods) {}
}
