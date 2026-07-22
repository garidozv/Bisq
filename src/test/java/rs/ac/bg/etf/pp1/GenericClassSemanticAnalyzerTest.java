package rs.ac.bg.etf.pp1;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericClassSemanticAnalyzerTest extends SemanticAnalyzerTestBase {

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
