package rs.ac.bg.etf.pp1;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.concepts.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericClassTest extends CompilerTestBase {

    @Test
    void monomorphizationPlanIncludesReachableClassSpecializations() throws Exception {
        var analysis = analyzeProgram("""
                program GenericClassPlan

                class Box<T> {
                    T value;
                    {
                        T get() { return value; }
                    }
                }

                class Wrapper<T> {
                    Box<T> box;
                    {
                        void initialize() { box = new Box<T>(); }
                    }
                }

                class Unused<T> {}
                {
                    <T> Box<T> create() Box<T> box; {
                        box = new Box<T>();
                        return box;
                    }

                    void main() Box<int> first; Box<int> second; Box<char> letters; Wrapper<int> wrapper; {
                        first = new Box<int>();
                        second = new Box<int>();
                        letters = new Box<char>();
                        wrapper = new Wrapper<int>();
                        first = create::<int>();
                    }
                }
                """);

        assertTrue(analysis.analyzer().passed());
        var declarations = findGenericTypes(analysis.program());

        var plan = analysis.analyzer().createMonomorphizationPlan();
        assertEquals(2, plan.getNeededSpecializations(declarations.get("Box")).size());
        assertEquals(1, plan.getNeededSpecializations(declarations.get("Wrapper")).size());
        assertTrue(plan.getNeededSpecializations(declarations.get("Unused")).isEmpty());
        for (var specialization : plan.getNeededSpecializations(declarations.get("Box"))) {
            assertTrue(specialization.getGeneratedObject().getType().getMembers().stream()
                    .allMatch(member -> GenericTypeUtils.isClosed(member.getType())));
        }
    }

    @Test
    void supportsMemberAccessThroughTheCurrentRegularClassType() throws Exception {
        var analyzer = analyze("""
                program RegularSelfReference

                class Node {
                    int value;
                    Node next;
                    {
                        int nextValue() { return next.value; }
                    }
                }
                {
                    void main() {}
                }
                """);

        assertTrue(analyzer.passed());
    }

    @Test
    void supportsGenericClassesAndSubstitutedMemberTypes() throws Exception {
        var analyzer = analyze("""
                program GenericClasses

                class Base {
                    int value;
                    {
                        int getValue() { return value; }
                    }
                }

                class Child extends Base {}

                class Box<T> {
                    T value;
                    T values[];
                    {
                        void set(T item) { value = item; }
                        T get() { return value; }
                        T[] allocate(int size) { return new T[size]; }
                    }
                }

                class Node<T> {
                    T value;
                    Node<T> next;
                    {
                        void link(Node<T> node) { next = node; }
                        T nextValue() { return next.value; }
                        Node<T> self() { return this; }
                    }
                }

                class Holder<T : Base> {
                    T value;
                    {
                        int readValue() { return value.getValue(); }
                    }
                }
                {
                    <U> Box<U> makeBox(U value) Box<U> result; {
                        result = new Box<U>();
                        result.set(value);
                        return result;
                    }

                    void main() Box<int> box; Node<int> node; Holder<Child> holder; int values[]; {
                        box = new Box<int>();
                        box.set(3);
                        print(box.get());
                        values = box.allocate(1);
                        node = new Node<int>();
                        node.link(node);
                        holder = new Holder<Child>();
                    }
                }
                """);

        assertTrue(analyzer.passed());
    }

    @Test
    void supportsSelfReferencesIncludingExpandingOpenApplicationsSemantically() throws Exception {
        var analyzer = analyze("""
                program GenericSelfReference

                class Node<T> {
                    Node<T[]> next;
                }
                {
                    void main() Node<int> node; {
                        node = new Node<int>();
                    }
                }
                """);

        assertTrue(analyzer.passed());
    }

    @Test
    void rejectsInvalidGenericTypeApplications() throws Exception {
        var analyzer = analyze("""
                program InvalidGenericApplications

                class Base {}
                class Box<T : Base> {}
                {
                    void main() Box raw; Base<int> appliedBase; Box<int> invalidBound; Box<Base, Base> wrongArity; {
                    }
                }
                """);

        assertFalse(analyzer.passed());
    }

    @Test
    void rejectsInvariantAssignmentsAndArrayIncompatibleArguments() throws Exception {
        var analyzer = analyze("""
                program InvalidGenericAssignments

                class Base {}
                class Child extends Base {}
                class Box<T> {
                    T values[];
                }
                {
                    void main() Box<Base> baseBox; Box<Child> childBox; Box<set> setBox; {
                        baseBox = childBox;
                    }
                }
                """);

        assertFalse(analyzer.passed());
    }

    @Test
    void supportsGenericClassInheritanceAndPlansBaseSpecializations() throws Exception {
        var analysis = analyzeProgram("""
                program GenericInheritance

                class Base<T> {
                    T value;
                    {
                        void set(T item) { value = item; }
                        T get() { return value; }
                    }
                }

                class Derived<T> extends Base<T> {
                    T extra;
                    {
                        T get() { return extra; }
                    }
                }

                class Leaf<T> extends Derived<T> {}
                class IntDerived extends Base<int> {}

                class Left {}
                class Right {}
                class Pair<T : Left, U : Right> {
                    T left;
                    U right;
                }
                class DerivedPair<T : Left, U : Right> extends Pair<T, U> {}
                {
                    void main() Base<int> base; Derived<int> derived; Derived<char> chars;
                                Leaf<int> leaf; IntDerived fixed; DerivedPair<Left, Right> pair; {
                        derived = new Derived<int>();
                        chars = new Derived<char>();
                        leaf = new Leaf<int>();
                        fixed = new IntDerived();
                        pair = new DerivedPair<Left, Right>();
                        base = derived;
                        base = leaf;
                        base = fixed;
                    }
                }
                """);

        assertTrue(analysis.analyzer().passed());

        var declarations = findGenericTypes(analysis.program());

        var plan = analysis.analyzer().createMonomorphizationPlan();
        assertEquals(2, plan.getNeededSpecializations(declarations.get("Base")).size());
        assertEquals(2, plan.getNeededSpecializations(declarations.get("Derived")).size());
        assertEquals(1, plan.getNeededSpecializations(declarations.get("Leaf")).size());
        assertEquals(1, plan.getNeededSpecializations(declarations.get("Pair")).size());
        assertEquals(1, plan.getNeededSpecializations(declarations.get("DerivedPair")).size());
    }

    @Test
    void rejectsInvalidGenericClassInheritance() throws Exception {
        var invalidOverride = analyze("""
                program InvalidGenericOverride
                class Base<T> {
                    { T get() T value; { return value; } }
                }
                class Broken<T> extends Base<T> {
                    { char get() { return 'x'; } }
                }
                { void main() {} }
                """);
        assertFalse(invalidOverride.passed());

        var invalidBases = analyze("""
                program InvalidGenericBases
                class Self<T> extends Self<T> {}
                class ParameterBase<T> extends T {}
                { void main() {} }
                """);
        assertFalse(invalidBases.passed());

        var invalidAssignment = analyze("""
                program InvalidInheritedAssignment
                class Base<T> {}
                class Derived<T> extends Base<T> {}
                { void main() Base<int> base; Derived<char> derived; {
                    derived = new Derived<char>();
                    base = derived;
                } }
                """);
        assertFalse(invalidAssignment.passed());

        var invalidConstraint = analyze("""
                program InvalidInheritedConstraint
                class Printable {}
                class Base<T : Printable> {}
                class Derived<T> extends Base<T> {}
                { void main() {} }
                """);
        assertFalse(invalidConstraint.passed());
    }

    private static SemanticAnalyzer analyze(String source) throws Exception {
        return analyzeProgram(source).analyzer();
    }

    private static HashMap<String, GenericTypeObj> findGenericTypes(Program program) {
        var declarations = new HashMap<String, GenericTypeObj>();
        program.traverseBottomUp(new VisitorAdaptor() {
            private void add(Obj declaration) {
                if (declaration instanceof GenericTypeObj genericType)
                    declarations.put(genericType.getName(), genericType);
            }

            @Override
            public void visit(ClassDecl_NonDerived declaration) {
                add(declaration.obj);
            }

            @Override
            public void visit(ClassDecl_Derived declaration) {
                add(declaration.obj);
            }
        });
        return declarations;
    }
}
