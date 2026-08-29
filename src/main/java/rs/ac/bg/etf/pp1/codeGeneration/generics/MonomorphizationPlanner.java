package rs.ac.bg.etf.pp1.codeGeneration.generics;

import rs.ac.bg.etf.pp1.ast.CallableRef;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.*;

/**
 * Collects uses of generics during semantic analysis and creates a plan of what needs to be generated with which
 * type arguments. This plan is then used by the code generator in the second phase.
 */
public class MonomorphizationPlanner {
    // Uses that appear inside other generic definitions. If the enclosing definition is never specialized, they won't be either.
    private final IdentityHashMap<GenericObj, List<GenericUse>> nestedUses = new IdentityHashMap<>();
    private final List<GenericUse> rootUses = new ArrayList<>();
    private final IdentityHashMap<Obj, List<GenericMethodObj>> genericMethodsByClass = new IdentityHashMap<>();

    public void registerMethodUse(CallableRef call, GenericMethodObj declaration, List<Struct> ownerTypeArguments,
                                  List<Struct> typeArguments, GenericObj enclosingDeclaration) {
        registerUse(new GenericMethodUse(call, declaration, ownerTypeArguments, typeArguments), enclosingDeclaration);
    }

    public void registerTypeUse(SyntaxNode use, GenericTypeObj declaration, List<Struct> typeArguments,
                                GenericObj enclosingDeclaration) {
        registerUse(new GenericTypeUse(use, declaration, typeArguments), enclosingDeclaration);
    }

    public void registerClassGenericMethod(GenericMethodObj method) {
        var methods = genericMethodsByClass.computeIfAbsent(method.getOwner(), _ -> new ArrayList<>());
        if (!methods.contains(method)) methods.add(method);
    }

    public MonomorphizationPlan build() {
        return new PlanBuilder().build();
    }

    private void registerUse(GenericUse use, GenericObj enclosingDeclaration) {
        if (enclosingDeclaration == null) {
            rootUses.add(use);
        } else {
            nestedUses.computeIfAbsent(enclosingDeclaration, _ -> new ArrayList<>()).add(use);
        }
    }

    /*
     * We start from root uses, which appear in non-generic contexts with concrete arguments. Creating a specialization
     * also processes the uses nested inside its definition with those arguments. Member method uses additionally become
     * virtual method requests. Each request is compared with every concrete class method that could implement it, so
     * virtual dispatch has every required overriding body available.
     */
    private final class PlanBuilder {
        private final LinkedHashMap<SpecializationKey, GenericSpecialization<?>> specializations = new LinkedHashMap<>();
        private final IdentityHashMap<SyntaxNode, GenericSpecialization<?>> rootTargets = new IdentityHashMap<>();
        private final IdentityHashMap<GenericTypeSpecialization, IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>>>
                memberMethodSpecializationsByOwner = new IdentityHashMap<>();

        // Member method specializations requested by actual calls, used to discover required overriding implementations
        private final List<GenericMethodSpecialization> virtualMethodRequests = new ArrayList<>();
        private final Set<GenericMethodSpecialization> virtualMethodRequestSet = Collections.newSetFromMap(new IdentityHashMap<>());
        private final List<ConcreteMethodCandidate> concreteMethodCandidates = new ArrayList<>();
        private final ArrayDeque<VirtualMethodRequestCheck> pendingVirtualMethodRequestChecks = new ArrayDeque<>();

        private final LinkedHashMap<VirtualMethodKey, Integer> virtualMethodIds = new LinkedHashMap<>();
        private int nextMemberMethodId;

        private MonomorphizationPlan build() {
            registerOrdinaryClassMethodCandidates();
            for (var use : rootUses) getOrCreateSpecialization(use, null);
            processPendingVirtualMethodCheckRequests();

            var methodSpecializationsByDeclaration = new IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>>();
            var typeSpecializationsByDeclaration = new IdentityHashMap<GenericTypeObj, List<GenericTypeSpecialization>>();
            for (var specialization : specializations.values()) {
                if (specialization instanceof GenericMethodSpecialization methodSpecialization) {
                    methodSpecializationsByDeclaration
                            .computeIfAbsent(methodSpecialization.getDeclaration(), _ -> new ArrayList<>())
                            .add(methodSpecialization);
                } else if (specialization instanceof GenericTypeSpecialization typeSpecialization) {
                    typeSpecializationsByDeclaration
                            .computeIfAbsent(typeSpecialization.getDeclaration(), _ -> new ArrayList<>())
                            .add(typeSpecialization);
                }
            }
            return new MonomorphizationPlan(methodSpecializationsByDeclaration, typeSpecializationsByDeclaration,
                    memberMethodSpecializationsByOwner, rootTargets);
        }

        private void registerOrdinaryClassMethodCandidates() {
            for (var entry : genericMethodsByClass.entrySet()) {
                if (entry.getKey() instanceof GenericTypeObj) continue;
                for (var method : entry.getValue()) {
                    registerConcreteMethodCandidate(method, null);
                }
            }
        }

        private void registerGenericClassMethodCandidates(GenericTypeSpecialization ownerSpecialization) {
            for (var method : genericMethodsByClass.getOrDefault(ownerSpecialization.getDeclaration(), List.of())) {
                registerConcreteMethodCandidate(method, ownerSpecialization);
            }
        }

        private void registerConcreteMethodCandidate(GenericMethodObj method, GenericTypeSpecialization ownerSpecialization) {
            var candidate = new ConcreteMethodCandidate(method, ownerSpecialization);
            concreteMethodCandidates.add(candidate);
            for (var request : virtualMethodRequests) {
                pendingVirtualMethodRequestChecks.addLast(new VirtualMethodRequestCheck(request, candidate));
            }
        }

        private void registerVirtualMethodRequest(GenericMethodSpecialization request) {
            if (!virtualMethodRequestSet.add(request)) return;

            virtualMethodRequests.add(request);
            for (var candidate : concreteMethodCandidates) {
                pendingVirtualMethodRequestChecks.addLast(new VirtualMethodRequestCheck(request, candidate));
            }
        }

        /**
         * Creates every concrete method specialization required to satisfy the registered virtual method requests.
         *
         * <p>A single request may produce specializations for the declaring class and for every compatible
         * overriding method in its derived classes. For example:</p>
         *
         * <pre>{@code
         * class Base<T> {
         *     <U> U convert(U value) { ... }
         * }
         *
         * class Derived<T> extends Base<T> {
         *     <U> U convert(U value) { ... }
         * }
         * }</pre>
         *
         * <p>A call to {@code Base<int>.convert::<char>} creates a request for that specialization.
         * Both {@code Base<int>.convert} and {@code Derived<int>.convert} are matching candidates, so the request produces
         * {@code Base<int>.convert::<char>} and {@code Derived<int>.convert::<char>}. Their shared generated method name
         * lets virtual table construction retain the appropriate implementation for each class.</p>
         */
        private void processPendingVirtualMethodCheckRequests() {
            while (!pendingVirtualMethodRequestChecks.isEmpty()) {
                var check = pendingVirtualMethodRequestChecks.removeFirst();
                if (!matchesRequest(check.request, check.candidate)) continue;

                var candidate = check.candidate;
                var specialization = getOrCreateMethodSpecialization(candidate.method, candidate.getOwnerTypeArguments(),
                        check.request.getMethodTypeArguments());
                if (candidate.ownerSpecialization != null) {
                    recordMemberMethodSpecialization(candidate.ownerSpecialization, specialization);
                }
            }
        }

        /**
         * Checks whether the candidate method can implement the requested virtual method specialization.
         *
         * <p>The semantic analysis has already verified the override signature. Here, it is enough to check that the methods
         * have the same name and that the candidate belongs either to the class where the requested method was declared
         * or to a class derived from it.</p>
         *
         * <p>Method type arguments are not checked here because, on a match, the candidate is specialized
         * using the method type arguments carried by the request.</p>
         */
        private boolean matchesRequest(GenericMethodSpecialization request, ConcreteMethodCandidate candidate) {
            if (!request.getDeclaration().getName().equals(candidate.method().getName())) return false;

            var requestOwner = request.getDeclaration().getOwner();
            if (requestOwner instanceof GenericTypeObj genericRequestOwner) {
                if (candidate.method().getOwner() == genericRequestOwner) {
                    return GenericTypeUtils.typeListsEqual(candidate.getOwnerTypeArguments(), request.getOwnerTypeArguments());
                }

                var requestOwnerApplication = GenericTypeUtils.findGenericTypeApplication(
                        candidate.getConcreteOwnerType(), genericRequestOwner);
                return requestOwnerApplication != null && GenericTypeUtils.typeListsEqual(
                        requestOwnerApplication.getTypeArguments(), request.getOwnerTypeArguments());
            }

            return TabUtils.assignableTo(requestOwner.getType(), candidate.getConcreteOwnerType());
        }

        private void getOrCreateSpecialization(GenericUse use, GenericSpecialization<?> enclosingSpecialization) {
            var specialization = switch (use) {
                case GenericMethodUse methodUse -> {
                    var ownerArguments = resolveArguments(methodUse.ownerTypeArguments(), enclosingSpecialization);
                    var methodArguments = resolveArguments(methodUse.methodTypeArguments(), enclosingSpecialization);

                    GenericTypeSpecialization ownerSpecialization = null;
                    if (methodUse.declaration().getOwner() instanceof GenericTypeObj genericOwner) {
                        // A method specialization belonging to a generic type requires the corresponding concrete owner specialization
                        ownerSpecialization = getOrCreateTypeSpecialization(genericOwner, ownerArguments);
                    }

                    var methodSpecialization = getOrCreateMethodSpecialization(methodUse.declaration(), ownerArguments, methodArguments);
                    if (ownerSpecialization != null) {
                        recordMemberMethodSpecialization(ownerSpecialization, methodSpecialization);
                    }
                    if (methodUse.declaration().isMemberMethod()) {
                        registerVirtualMethodRequest(methodSpecialization);
                    }
                    yield methodSpecialization;
                }
                case GenericTypeUse typeUse -> {
                    var typeArguments = resolveArguments(typeUse.typeArguments(), enclosingSpecialization);
                    yield getOrCreateTypeSpecialization(typeUse.declaration(), typeArguments);
                }
            };

            if (enclosingSpecialization == null) rootTargets.put(use.node(), specialization);
            else enclosingSpecialization.setTargetSpecialization(use.node(), specialization);
        }

        private List<Struct> resolveArguments(List<Struct> arguments, GenericSpecialization<?> enclosingSpecialization) {
            if (enclosingSpecialization == null) return arguments;
            return arguments.stream().map(enclosingSpecialization::resolveType).toList();
        }

        private GenericTypeSpecialization getOrCreateTypeSpecialization(GenericTypeObj declaration, List<Struct> typeArguments) {
            var key = new SpecializationKey(declaration, typeArguments);
            var existing = specializations.get(key);
            if (existing != null) return (GenericTypeSpecialization) existing;

            var specialization = new GenericTypeSpecialization(declaration, typeArguments);
            specializations.put(key, specialization);
            registerGenericClassMethodCandidates(specialization);
            processNestedUses(declaration, specialization);
            return specialization;
        }

        private GenericMethodSpecialization getOrCreateMethodSpecialization(GenericMethodObj declaration,
                                                                            List<Struct> ownerArguments, List<Struct> methodArguments) {
            var arguments = new ArrayList<Struct>(ownerArguments.size() + methodArguments.size());
            arguments.addAll(ownerArguments);
            arguments.addAll(methodArguments);
            var key = new SpecializationKey(declaration, arguments);
            var existing = specializations.get(key);
            if (existing != null) return (GenericMethodSpecialization) existing;

            var generatedName = declaration.isMemberMethod()
                    ? TabUtils.createInternalName(declaration.getName() + "$" + getVirtualMethodId(declaration.getName(), methodArguments))
                    : declaration.getName();
            var specialization = new GenericMethodSpecialization(declaration, ownerArguments, methodArguments, generatedName);
            specializations.put(key, specialization);
            processNestedUses(declaration, specialization);
            return specialization;
        }

        private void processNestedUses(GenericObj declaration, GenericSpecialization<?> specialization) {
            for (var nestedUse : nestedUses.getOrDefault(declaration, List.of())) {
                getOrCreateSpecialization(nestedUse, specialization);
            }
        }

        private void recordMemberMethodSpecialization(GenericTypeSpecialization ownerSpecialization,
                                                      GenericMethodSpecialization methodSpecialization) {
            var methodsByDeclaration = memberMethodSpecializationsByOwner.computeIfAbsent(
                    ownerSpecialization, _ -> new IdentityHashMap<>());
            var methodSpecializations = methodsByDeclaration.computeIfAbsent(
                    methodSpecialization.getDeclaration(), _ -> new ArrayList<>());
            if (!methodSpecializations.contains(methodSpecialization)) methodSpecializations.add(methodSpecialization);
        }

        /*
         * Using method arguments inside the key distinguishes specializations of the same source method, avoiding name conflicts,
         * and the shared mapping for name argument pairs ensures that matching overrides have the same name.
         * IDs use one global counter, so their values are not that useful for debugging purposes
         */
        private int getVirtualMethodId(String methodName, List<Struct> methodArguments) {
            var key = new VirtualMethodKey(methodName, methodArguments);
            var existing = virtualMethodIds.get(key);
            if (existing != null) return existing;

            var id = nextMemberMethodId++;
            virtualMethodIds.put(key, id);
            return id;
        }
    }

    private sealed interface GenericUse permits GenericMethodUse, GenericTypeUse {
        SyntaxNode node();
    }

    private record GenericMethodUse(CallableRef node, GenericMethodObj declaration,
                                    List<Struct> ownerTypeArguments, List<Struct> methodTypeArguments) implements GenericUse {
    }

    private record GenericTypeUse(SyntaxNode node, GenericTypeObj declaration, List<Struct> typeArguments) implements GenericUse {
    }

    /**
     * Represents a generic {@link #method()} that could be specialized for a concrete owner class.
     * {@link #ownerSpecialization()} identifies the concrete generic class specialization, or is {@code null} when the method belongs to an ordinary class.
     */
    private record ConcreteMethodCandidate(GenericMethodObj method, GenericTypeSpecialization ownerSpecialization) {
        private Struct getConcreteOwnerType() {
            return ownerSpecialization == null ? method.getOwner().getType() : ownerSpecialization.getGeneratedObject().getType();
        }

        private List<Struct> getOwnerTypeArguments() {
            return ownerSpecialization == null ? List.of() : ownerSpecialization.getTypeArguments();
        }
    }

    /**
     * A pending check of whether a concrete generic method candidate must be specialized to satisfy a virtual method request.
     */
    private record VirtualMethodRequestCheck(GenericMethodSpecialization request, ConcreteMethodCandidate candidate) {
    }

    private record SpecializationKey(GenericObj declaration, List<Struct> typeArguments) {
        private SpecializationKey(GenericObj declaration, List<Struct> typeArguments) {
            this.declaration = declaration;
            this.typeArguments = List.copyOf(typeArguments);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof SpecializationKey(GenericObj declaration1, List<Struct> arguments) &&
                    declaration == declaration1 && GenericTypeUtils.typeListsEqual(typeArguments, arguments);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(declaration) + GenericTypeUtils.typeListHashCode(typeArguments);
        }
    }

    private record VirtualMethodKey(String methodName, List<Struct> methodArguments) {
        private VirtualMethodKey(String methodName, List<Struct> methodArguments) {
            this.methodName = methodName;
            this.methodArguments = List.copyOf(methodArguments);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof VirtualMethodKey(String name, List<Struct> arguments) &&
                    methodName.equals(name) && GenericTypeUtils.typeListsEqual(methodArguments, arguments);
        }

        @Override
        public int hashCode() {
            return 31 * methodName.hashCode() + GenericTypeUtils.typeListHashCode(methodArguments);
        }
    }
}
