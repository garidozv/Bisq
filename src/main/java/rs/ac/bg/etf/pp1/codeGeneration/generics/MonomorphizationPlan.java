package rs.ac.bg.etf.pp1.codeGeneration.generics;

import java.util.IdentityHashMap;
import java.util.List;

import rs.ac.bg.etf.pp1.ast.CallableRef_Applied;
import rs.ac.bg.etf.pp1.ast.ExtendedClassName_Valid;
import rs.ac.bg.etf.pp1.ast.Factor_NewObject;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;

/**
 * A plan whose purpose is to tell the code generator which specializations need to be generated for each generic declaration.
 */
public final class MonomorphizationPlan {
    private final IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>> methodSpecializationsByDeclaration;
    private final IdentityHashMap<GenericTypeObj, List<GenericTypeSpecialization>> typeSpecializationsByDeclaration;
    private final IdentityHashMap<SyntaxNode, GenericSpecialization<?>> rootTargets;

    public MonomorphizationPlan(
            IdentityHashMap<GenericMethodObj, List<GenericMethodSpecialization>> methodSpecializationsByDeclaration,
            IdentityHashMap<GenericTypeObj, List<GenericTypeSpecialization>> typeSpecializationsByDeclaration,
            IdentityHashMap<SyntaxNode, GenericSpecialization<?>> rootTargets) {
        this.methodSpecializationsByDeclaration = methodSpecializationsByDeclaration;
        this.typeSpecializationsByDeclaration = typeSpecializationsByDeclaration;
        this.rootTargets = rootTargets;
    }

    public List<GenericMethodSpecialization> getNeededSpecializations(GenericMethodObj declaration) {
        return methodSpecializationsByDeclaration.getOrDefault(declaration, List.of());
    }

    public List<GenericTypeSpecialization> getNeededSpecializations(GenericTypeObj declaration) {
        return typeSpecializationsByDeclaration.getOrDefault(declaration, List.of());
    }

    public GenericMethodSpecialization getTargetSpecialization(CallableRef_Applied call, GenericSpecialization<?> caller) {
        return (GenericMethodSpecialization)getTargetSpecialization((SyntaxNode)call, caller);
    }

    public GenericTypeSpecialization getTargetSpecialization(Factor_NewObject creation, GenericSpecialization<?> caller) {
        return (GenericTypeSpecialization)getTargetSpecialization((SyntaxNode)creation, caller);
    }

    public GenericTypeSpecialization getTargetSpecialization(ExtendedClassName_Valid inheritance, GenericSpecialization<?> derivedSpecialization) {
        return (GenericTypeSpecialization)getTargetSpecialization((SyntaxNode)inheritance, derivedSpecialization);
    }

    private GenericSpecialization<?> getTargetSpecialization(SyntaxNode node, GenericSpecialization<?> caller) {
        return caller == null ? rootTargets.get(node) : caller.getTargetSpecialization(node);
    }
}