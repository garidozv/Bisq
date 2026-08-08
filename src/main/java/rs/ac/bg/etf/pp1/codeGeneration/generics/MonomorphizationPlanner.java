package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;

import rs.ac.bg.etf.pp1.ast.CallableRef_Applied;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.concepts.Struct;

/**
 * Collects uses of generics during semantic analysis and creates a plan of what needs to be generated with which
 * type arguments. This plan is then used by the code generator in the second phase.
 */
public class MonomorphizationPlanner {
    // Uses that appear inside other generic definitions. If the enclosing definition is never specialized, they won't be either.
    private final IdentityHashMap<GenericObj, List<GenericUse>> nestedUses = new IdentityHashMap<>();
    private final List<GenericUse> rootUses = new ArrayList<>();

    public void registerMethodUse(CallableRef_Applied call, GenericMethodObj declaration, List<Struct> ownerTypeArguments,
                                  List<Struct> typeArguments, GenericObj enclosingDeclaration) {
        registerUse(new GenericMethodUse(call, declaration, ownerTypeArguments, typeArguments), enclosingDeclaration);
    }

    public void registerTypeUse(SyntaxNode use, GenericTypeObj declaration, List<Struct> typeArguments,
                                GenericObj enclosingDeclaration) {
        registerUse(new GenericTypeUse(use, declaration, typeArguments), enclosingDeclaration);
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
     * The algorithm is simple. We start from the root uses, the ones that appear in non-generic context with concrete type arguments.
     * We know that they will always be specialized for those arguments. After we specialize one of the root uses, we also
     * specialize all the uses nested inside it with the same arguments.
     * The result is a plan that only creates specializations of generics for the arguments that they will actually be used with.
     * Only one specialization is created for each unique combination of generic declaration and type arguments.
     */
    private final class PlanBuilder {
        private final LinkedHashMap<SpecializationKey, GenericSpecialization<?>> specializations = new LinkedHashMap<>();
        private final IdentityHashMap<SyntaxNode, GenericSpecialization<?>> rootTargets = new IdentityHashMap<>();
        private final IdentityHashMap<GenericTypeSpecialization,
                IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>>> memberMethodSpecializationsByOwner =
                new IdentityHashMap<>();
        private int nextMemberMethodId;

        private MonomorphizationPlan build() {
            for (var use : rootUses) getOrCreateSpecialization(use, null);

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

        private void getOrCreateSpecialization(GenericUse use, GenericSpecialization<?> enclosingSpecialization) {
            var closedArguments = use.typeArguments().stream()
                    .map(argument -> enclosingSpecialization == null ? argument : enclosingSpecialization.resolveType(argument))
                    .toList();

            GenericTypeSpecialization ownerSpecialization = null;
            // If this is a generic method inside a generic type, ensure that type is also specialized without recording it
            // as the call node's target; the method specialization requested below will be the actual call target
			if (use instanceof GenericMethodUse methodUse && methodUse.declaration().getOwner() instanceof GenericTypeObj owner) {
				var ownerArguments = methodUse.extractOwnerArguments(closedArguments);
				ownerSpecialization = (GenericTypeSpecialization) getOrCreateSpecialization(
                        new GenericTypeUse(methodUse.node(), owner, ownerArguments), enclosingSpecialization, ownerArguments, false);
			}

			var specialization = getOrCreateSpecialization(use, enclosingSpecialization, closedArguments, true);
            if (ownerSpecialization != null && specialization instanceof GenericMethodSpecialization methodSpecialization) {
                var methodsByDeclaration = memberMethodSpecializationsByOwner.computeIfAbsent(ownerSpecialization, _ -> new IdentityHashMap<>());
                var methodSpecializations = methodsByDeclaration.computeIfAbsent(methodSpecialization.getDeclaration(), _ -> new ArrayList<>());
                if (!methodSpecializations.contains(methodSpecialization))
                    methodSpecializations.add(methodSpecialization);
            }
		}

        /**
         * @param recordTarget Whether to associate the use node with this specialization as either a root or nested target.
         */
        private GenericSpecialization<?> getOrCreateSpecialization(
                GenericUse use, GenericSpecialization<?> enclosingSpecialization, List<Struct> closedArguments,
                boolean recordTarget) {
            var key = new SpecializationKey(use.declaration(), closedArguments);
            var specialization = specializations.get(key);

            if (specialization == null) {
                specialization = use.createSpecialization(closedArguments, nextMemberMethodId);
                if (specialization instanceof GenericMethodSpecialization method && method.getDeclaration().isMemberMethod())
                    nextMemberMethodId++;
                specializations.put(key, specialization);

                for (var nestedUse : nestedUses.getOrDefault(use.declaration(), List.of())) {
                    getOrCreateSpecialization(nestedUse, specialization);
                }
            }

			if (recordTarget) {
				if (enclosingSpecialization == null) rootTargets.put(use.node(), specialization);
				else enclosingSpecialization.setTargetSpecialization(use.node(), specialization);
			}
            return specialization;
        }
    }

    private record SpecializationKey(GenericObj declaration, List<Struct> typeArguments) {
        private SpecializationKey(GenericObj declaration, List<Struct> typeArguments) {
            this.declaration = declaration;
            this.typeArguments = List.copyOf(typeArguments);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SpecializationKey(GenericObj declaration1, List<Struct> arguments)) ||
                    declaration != declaration1 || typeArguments.size() != arguments.size()) {
                return false;
            }
            for (var index = 0; index < typeArguments.size(); index++) {
                if (!TabUtils.equals(typeArguments.get(index), arguments.get(index)))
                    return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            var result = System.identityHashCode(declaration);
            for (var argument : typeArguments) {
                result = 31 * result + GenericTypeUtils.typeHashCode(argument);
            }
            return result;
        }
    }

    private sealed interface GenericUse permits GenericMethodUse, GenericTypeUse {
        SyntaxNode node();

        GenericObj declaration();

        List<Struct> typeArguments();

        GenericSpecialization<?> createSpecialization(List<Struct> closedArguments, int generatedNameId);
    }

    private record GenericMethodUse(CallableRef_Applied node, GenericMethodObj declaration,
                                    List<Struct> ownerTypeArguments,
									List<Struct> methodTypeArguments) implements GenericUse {
		@Override
		public List<Struct> typeArguments() {
			var arguments = new ArrayList<Struct>(ownerTypeArguments.size() + methodTypeArguments.size());
			arguments.addAll(ownerTypeArguments);
			arguments.addAll(methodTypeArguments);
			return arguments;
		}

        @Override
		public GenericMethodSpecialization createSpecialization(List<Struct> closedArguments, int generatedNameId) {
			var generatedName = declaration.isMemberMethod()
					? TabUtils.createInternalName(declaration.getName() + "$" + generatedNameId)
					: declaration.getName();
			return new GenericMethodSpecialization(declaration, extractOwnerArguments(closedArguments),
                    extractMethodArguments(closedArguments), generatedName);
        }

        private List<Struct> extractOwnerArguments(List<Struct> arguments) {
            return arguments.subList(0, ownerTypeArguments.size());
        }

        private List<Struct> extractMethodArguments(List<Struct> arguments) {
            return arguments.subList(ownerTypeArguments.size(), arguments.size());
        }
    }

    private record GenericTypeUse(SyntaxNode node, GenericTypeObj declaration,
                                  List<Struct> typeArguments) implements GenericUse {
        @Override
		public GenericTypeSpecialization createSpecialization(List<Struct> closedArguments, int generatedNameId) {
            return new GenericTypeSpecialization(declaration, closedArguments);
        }
    }
}
