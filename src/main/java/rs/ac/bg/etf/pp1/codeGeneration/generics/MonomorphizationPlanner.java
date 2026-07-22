package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;

import rs.ac.bg.etf.pp1.ast.CallableRef_Applied;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.etf.pp1.symboltable.concepts.Struct;

/**
 * Collects uses of generics during semantic analysis and creates a plan of what needs to be generated with which
 * type arguments. This plan is then used by the code generator in the second phase.
 */
public class MonomorphizationPlanner {
    // Uses that appear inside other generic methods. If the enclosing method is never specialized, they won't be either.
    private final IdentityHashMap<GenericMethodObj, List<GenericMethodUse>> nestedUses = new IdentityHashMap<>();
    private final List<GenericMethodUse> rootUses = new ArrayList<>();

    public void registerUse(CallableRef_Applied call, GenericMethodObj declaration, List<Struct> typeArguments,
                            GenericMethodObj enclosingDeclaration) {
        var use = new GenericMethodUse(call, declaration, typeArguments, enclosingDeclaration);
        if (enclosingDeclaration == null) {
            rootUses.add(use);
        } else {
            nestedUses.computeIfAbsent(enclosingDeclaration, ignored -> new ArrayList<>()).add(use);
        }
    }

    public MonomorphizationPlan build() {
        return new PlanBuilder().build();
    }

    /*
     * The algorithm is simple. We start from the root uses, the ones that appear in non-generic context with concrete type arguments.
     * We know that they will always be specialized for those arguments. After we specialize one of the root uses, we also
     * specialize all the uses nested inside it with the same arguments.
     * The result is a plan that only creates specializations of generics for the arguments that they will actually be used with.
     * Only one specialization is created for each unique combination of generic declaration and type arguments.
     */
    private final class PlanBuilder {
        private final LinkedHashMap<SpecializationKey, GenericMethodSpecialization> specializations = new LinkedHashMap<>();
        private final IdentityHashMap<CallableRef_Applied, GenericMethodSpecialization> rootCallTargets = new IdentityHashMap<>();

        private MonomorphizationPlan build() {
            for (var use : rootUses) requestSpecialization(use, null);

            var specializationsByDeclaration = new IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>>();
            for (var specialization : specializations.values()) {
                specializationsByDeclaration
                        .computeIfAbsent(specialization.getDeclaration(), _ -> new ArrayList<>())
                        .add(specialization);
            }
            return new MonomorphizationPlan(specializationsByDeclaration, rootCallTargets);
        }

        private void requestSpecialization(GenericMethodUse use, GenericMethodSpecialization enclosingSpecialization) {
            var closedArguments = use.typeArguments().stream()
                    .map(argument -> enclosingSpecialization == null ? argument : enclosingSpecialization.resolveType(argument))
                    .toList();
            var key = new SpecializationKey(use.declaration(), closedArguments);
            var specialization = specializations.get(key);

            if (specialization == null) {
                specialization = new GenericMethodSpecialization(use.declaration(), closedArguments);
                specializations.put(key, specialization);

                for (var nestedUse : nestedUses.getOrDefault(use.declaration(), List.of())) {
                    requestSpecialization(nestedUse, specialization);
                }
            }

            if (enclosingSpecialization == null) rootCallTargets.put(use.node(), specialization);
            else enclosingSpecialization.setCallTargetSpecialization(use.node(), specialization);
        }
    }

    private record SpecializationKey(GenericMethodObj declaration, List<Struct> typeArguments) {
        private SpecializationKey(GenericMethodObj declaration, List<Struct> typeArguments) {
            this.declaration = declaration;
            this.typeArguments = List.copyOf(typeArguments);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SpecializationKey(GenericMethodObj declaration1, List<Struct> arguments)) ||
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

    private record GenericMethodUse(
            CallableRef_Applied node,
            GenericMethodObj declaration,
            List<Struct> typeArguments,
            GenericMethodObj enclosingDeclaration) {

        public GenericMethodUse {
            typeArguments = List.copyOf(typeArguments);
        }
    }
}