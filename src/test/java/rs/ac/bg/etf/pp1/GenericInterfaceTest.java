package rs.ac.bg.etf.pp1;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.InterfaceDecl;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.etf.pp1.symboltable.concepts.Obj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericInterfaceTest extends CompilerTestBase {

    @Test
    void supportsGenericInterfacesAndPlansAllDependencies() throws Exception {
        var analysis = analyzeProgram("""
                program GenericInterfaces

                class LeftBase {}
                class RightBase {}
                class Left extends LeftBase {}
                class Right extends RightBase {}

                interface Pair<T : LeftBase, U : RightBase> {
                    T first(T value);
                    T forward(T value) { return this.first(value); }
                    U second(U value) { return value; }
                    int marker() { return 1; }
                }

                class Implementation<T : LeftBase, U : RightBase> extends Pair<T, U> {
                    {
                        T first(T value) { return value; }
                        int marker() { return 2; }
                    }
                }

                class Consumer<V : Pair<Left, Right>> {
                    { int marker(V value) { return value.marker(); } }
                }
                {
                    void main() Pair<Left, Right> base; Implementation<Left, Right> implementation;
                                Consumer<Implementation<Left, Right>> consumer;
                                Left left; Right right; {
                        implementation = new Implementation<Left, Right>();
                        consumer = new Consumer<Implementation<Left, Right>>();
                        base = implementation;
                        left = base.forward(left);
                        right = base.second(right);
                        print(base.marker());
                        print(consumer.marker(implementation));
                    }
                }
                """);

        assertTrue(analysis.analyzer().passed());
        var declarations = findGenericTypes(analysis.program());
        var plan = analysis.analyzer().createMonomorphizationPlan();
        assertEquals(1, plan.getNeededSpecializations(declarations.get("Pair")).size());
        assertEquals(1, plan.getNeededSpecializations(declarations.get("Implementation")).size());
        assertEquals(1, plan.getNeededSpecializations(declarations.get("Consumer")).size());
    }

    @Test
    void rejectsInvalidGenericInterfacesAndImplementations() throws Exception {
        var invalidApplications = analyzeProgram("""
                program InvalidInterfaceApplications
                class Base {}
                class Valid extends Base {}
                class Invalid {}
                interface Pair<T : Base, U : Base> {}
                { void main() Pair raw; Pair<Valid> arity; Pair<Valid, Invalid> constraint; {} }
                """).analyzer();
        assertFalse(invalidApplications.passed());

        var invalidOverrides = analyzeProgram("""
                program InvalidInterfaceOverrides
                interface Base<T> { T get(); }
                class BadImplementation<T> extends Base<T> {
                    { char get() { return 'x'; } }
                }
                { void main() Base<int> integers; Base<char> characters; { integers = characters; } }
                """).analyzer();
        assertFalse(invalidOverrides.passed());
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

            @Override
            public void visit(InterfaceDecl declaration) {
                add(declaration.obj);
            }
        });
        return declarations;
    }
}