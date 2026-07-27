package rs.ac.bg.etf.pp1;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;

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
        var declarations = new ArrayList<GenericTypeObj>();
        analysis.program().traverseBottomUp(new VisitorAdaptor() {
            @Override
            public void visit(ClassDecl_NonDerived declaration) {
                if (declaration.obj instanceof GenericTypeObj genericType) declarations.add(genericType);
            }

            @Override
            public void visit(ClassDecl_Derived declaration) {
                if (declaration.obj instanceof GenericTypeObj genericType) declarations.add(genericType);
            }
        });

        var plan = analysis.analyzer().createMonomorphizationPlan();
        assertEquals(2, plan.getNeededSpecializations(declarations.get(0)).size());
        assertEquals(1, plan.getNeededSpecializations(declarations.get(1)).size());
        assertTrue(plan.getNeededSpecializations(declarations.get(2)).isEmpty());
        for (var specialization : plan.getNeededSpecializations(declarations.get(0))) {
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
    void rejectsMethodOwnedTypeParameters() throws Exception {
        var analyzer = analyze("""
                program UnsupportedGenericFeatures

                class Box<T> {
                    {
                        <U> U convert(U value) { return value; }
                    }
                }
                {
                    void main() {}
                }
                """);

        assertFalse(analyzer.passed());
    }

    private static SemanticAnalyzer analyze(String source) throws Exception {
        return analyzeProgram(source).analyzer();
    }
}
