package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.IdentityHashMap;
import java.util.List;

import rs.ac.bg.etf.pp1.ast.CallableRef_Applied;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;

/**
 * A plan whose purpose is to tell the code generator which specializations need to be generated for each generic object.
 */
public final class MonomorphizationPlan {
    private final IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>> specializationsByDeclaration;
    private final IdentityHashMap<CallableRef_Applied, GenericMethodSpecialization> rootCallTargets;

    public MonomorphizationPlan(
            IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>> specializationsByDeclaration,
            IdentityHashMap<CallableRef_Applied, GenericMethodSpecialization> rootCallTargets) {
        this.specializationsByDeclaration = specializationsByDeclaration;
        this.rootCallTargets = new IdentityHashMap<>(rootCallTargets);
    }

    public List<GenericMethodSpecialization> getNeededSpecializations(GenericMethodObj declaration) {
        return specializationsByDeclaration.getOrDefault(declaration, List.of());
    }

    public GenericMethodSpecialization getTargetSpecialization(CallableRef_Applied call, GenericMethodSpecialization caller) {
        return caller == null ? rootCallTargets.get(call) : caller.getCallTargetSpecialization(call);
    }
}