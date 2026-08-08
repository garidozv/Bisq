package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils.MethodTypes;
import rs.ac.bg.etf.pp1.ast.ActPars_Expr;
import rs.ac.bg.etf.pp1.ast.ActPars_ExprList;
import rs.ac.bg.etf.pp1.ast.CallPars_ActPars;
import rs.ac.bg.etf.pp1.ast.CallPars_Empty;
import rs.ac.bg.etf.pp1.ast.CallableRef;
import rs.ac.bg.etf.pp1.ast.CallableRef_Applied;
import rs.ac.bg.etf.pp1.ast.CallableRef_Plain;
import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.ClassName_Generic;
import rs.ac.bg.etf.pp1.ast.ClassName_Regular;
import rs.ac.bg.etf.pp1.ast.CondFact_Expr;
import rs.ac.bg.etf.pp1.ast.CondFact_RelopExpr;
import rs.ac.bg.etf.pp1.ast.CondTermList_CondFact;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Bool;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Char;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Num;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignExpr;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignSetop;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Call;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Dec;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Inc;
import rs.ac.bg.etf.pp1.ast.Designator;
import rs.ac.bg.etf.pp1.ast.Designator_ArrayAccess;
import rs.ac.bg.etf.pp1.ast.Designator_Ident;
import rs.ac.bg.etf.pp1.ast.Designator_MemberAccess;
import rs.ac.bg.etf.pp1.ast.ExprList_AddopTerm;
import rs.ac.bg.etf.pp1.ast.ExprList_SubTerm;
import rs.ac.bg.etf.pp1.ast.ExprList_Term;
import rs.ac.bg.etf.pp1.ast.ExprNonTern_ExprList;
import rs.ac.bg.etf.pp1.ast.ExprNonTern_Map;
import rs.ac.bg.etf.pp1.ast.ExprTern;
import rs.ac.bg.etf.pp1.ast.Expr_NonTern;
import rs.ac.bg.etf.pp1.ast.Expr_Tern;
import rs.ac.bg.etf.pp1.ast.ExtendedTypeName_Valid;
import rs.ac.bg.etf.pp1.ast.Factor_BoolConst;
import rs.ac.bg.etf.pp1.ast.Factor_CharConst;
import rs.ac.bg.etf.pp1.ast.Factor_Designator;
import rs.ac.bg.etf.pp1.ast.Factor_DesignatorCall;
import rs.ac.bg.etf.pp1.ast.Factor_Expr;
import rs.ac.bg.etf.pp1.ast.Factor_NewArray;
import rs.ac.bg.etf.pp1.ast.Factor_NewObject;
import rs.ac.bg.etf.pp1.ast.Factor_NumConst;
import rs.ac.bg.etf.pp1.ast.ForPostStatement_DesignatorStatement;
import rs.ac.bg.etf.pp1.ast.ForPostStatement_Epsilon;
import rs.ac.bg.etf.pp1.ast.FormParVar_Array;
import rs.ac.bg.etf.pp1.ast.FormParVar_NonArray;
import rs.ac.bg.etf.pp1.ast.InterfaceBody_MethodSignature;
import rs.ac.bg.etf.pp1.ast.InterfaceDecl;
import rs.ac.bg.etf.pp1.ast.InterfaceName_Generic;
import rs.ac.bg.etf.pp1.ast.InterfaceName_Regular;
import rs.ac.bg.etf.pp1.ast.GenericMethodDecl;
import rs.ac.bg.etf.pp1.ast.GenericMethodSignature;
import rs.ac.bg.etf.pp1.ast.GenericParameterScopeStart;
import rs.ac.bg.etf.pp1.ast.MethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodName;
import rs.ac.bg.etf.pp1.ast.MethodReturnType_Void;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.ast.ProgramName;
import rs.ac.bg.etf.pp1.ast.Relop_Equal;
import rs.ac.bg.etf.pp1.ast.Relop_NotEqual;
import rs.ac.bg.etf.pp1.ast.Statement_Break;
import rs.ac.bg.etf.pp1.ast.Statement_Continue;
import rs.ac.bg.etf.pp1.ast.Statement_DoWhileCondition;
import rs.ac.bg.etf.pp1.ast.Statement_DoWhileConditionWithDesignatorStatement;
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
import rs.ac.bg.etf.pp1.ast.Term_Factor;
import rs.ac.bg.etf.pp1.ast.Term_MulopFactor;
import rs.ac.bg.etf.pp1.ast.TypeAtom_Ident;
import rs.ac.bg.etf.pp1.ast.TypeParameter_Bounded;
import rs.ac.bg.etf.pp1.ast.TypeParameter_Unbounded;
import rs.ac.bg.etf.pp1.ast.TypeArgumentList_List;
import rs.ac.bg.etf.pp1.ast.TypeArgumentList_Type;
import rs.ac.bg.etf.pp1.ast.Type_Array;
import rs.ac.bg.etf.pp1.ast.Type_Atom;
import rs.ac.bg.etf.pp1.ast.Type_Generic;
import rs.ac.bg.etf.pp1.ast.VarName_Array;
import rs.ac.bg.etf.pp1.ast.VarName_NonArray;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericMethodObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericParameterStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeApplicationStruct;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeObj;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeUtils;
import rs.ac.bg.etf.pp1.symbolTable.generics.TypeArguments;
import rs.ac.bg.etf.pp1.codeGeneration.generics.MonomorphizationPlan;
import rs.ac.bg.etf.pp1.codeGeneration.generics.MonomorphizationPlanner;
import rs.ac.bg.etf.pp1.ast.Statement_DoWhileTrue;
import rs.ac.bg.etf.pp1.ast.Statement_For;
import rs.ac.bg.etf.pp1.ast.Statement_PrintExpr;
import rs.ac.bg.etf.pp1.ast.Statement_PrintExprWithNum;
import rs.ac.bg.etf.pp1.ast.Statement_Read;
import rs.ac.bg.etf.pp1.ast.Statement_ReturnExpr;
import rs.ac.bg.etf.pp1.ast.Statement_ReturnVoid;
import rs.ac.bg.etf.pp1.ast.DoToken;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public class SemanticAnalyzer extends VisitorAdaptor {

	private final static String VirtualMethodTableName = TabUtils.createInternalName("vtp");

	private Obj programObj = null;
	private Struct currentType = null;
	private Obj currentMethod = null;
	private Obj currentClass = null;
	private Obj currentInterface = null;
    private final Deque<List<Obj>> genericParameterScopes = new ArrayDeque<>();
    // Keeps track of applications of the generic class or interface currently being declared.
    // They must be revalidated at the end because requirements for its type parameters may be discovered later in the type body.
    // Currently, this happens when a type parameter is used as an array element, so we mustn't allow application of the set.
	private final List<Type_Generic> genericTypeApplicationsToRevalidate = new ArrayList<>();
	private final MonomorphizationPlanner monomorphizationPlanner = new MonomorphizationPlanner();
	private Scope programScope = null;

	private int nVars = 0;
	private int paramCount = 0;
	private int loopCount = 0;
	private boolean hasMethodReturned = false;
	private boolean hasMain = false;
	private boolean isErrorDetected = false;

	private final Logger log = Logger.getLogger(getClass());

	private static boolean isCallableWith(Obj method, Struct argsStruct) {
		return isCallableWith(method, argsStruct, type -> type);
	}

	private static boolean isCallableWith(GenericMethodObj method, Struct argsStruct, Map<GenericParameterStruct, Struct> substitutionMap) {
		return isCallableWith(method, argsStruct, type -> GenericTypeUtils.substituteType(type, substitutionMap));
	}

    // Checks if a method can be called with given arguments
    private static boolean isCallableWith(Obj method, Struct argsStruct, UnaryOperator<Struct> mapParamType) {
        // Take this parameter into account if method is not global
        var hasThisParam = method.getFpPos() != MethodTypes.GLOBAL.value;
        var explicitParamCnt = method.getLevel() - (hasThisParam ? 1 : 0);

        if (explicitParamCnt != argsStruct.getImplementedInterfaces().size()) {
            return false;
        }

        var params = method.getLocalSymbols().stream()
                .skip(hasThisParam ? 1 : 0)
                .limit(explicitParamCnt)
                .map(Obj::getType)
                .map(mapParamType)
                .toArray(Struct[]::new);

        var args = argsStruct.getImplementedInterfaces()
                .toArray(Struct[]::new);

        for (var i = 0; i < explicitParamCnt; i++) {
            if (!TabUtils.assignableTo(params[i], args[i])) {
                return false;
            }
        }

        return true;
    }

	// Checks if the signatures (excludes return type) of passed methods match
	private static boolean hasMatchingSignature(Obj firstMethod, Obj secondMethod) {
		if (firstMethod.getLevel() != secondMethod.getLevel()) {
			return false;
		}

		var explicitParamCnt = firstMethod.getLevel() - 1;
		var firstMethodParams = firstMethod.getLocalSymbols().stream()
				.skip(1) // Skip this param
				.limit(explicitParamCnt)
				.toArray(Obj[]::new);

		var secondMethodParams = secondMethod.getLocalSymbols().stream()
				.skip(1) // Skip this param
				.limit(explicitParamCnt)
				.toArray(Obj[]::new);

		for (int i = 0; i < explicitParamCnt; i++) {
			if (!TabUtils.equals(firstMethodParams[i].getType(), secondMethodParams[i].getType())) {
				return false;
			}
		}

		return true;
	}

	private static boolean validatePrintStatementExpr(Struct exprStruct) {
        return exprStruct.equals(Tab.intType) || exprStruct.equals(Tab.charType) ||
                exprStruct.equals(TabUtils.boolType) ||
                exprStruct.equals(TabUtils.setType);
    }

    private static void tryMarkGenericParameterArrayElement(Struct type) {
        if (type instanceof GenericParameterStruct parameter)
            parameter.markUsedAsArrayElementType();
    }

	public void report_error(String message, SyntaxNode info) {
		isErrorDetected = true;
		var msg = new StringBuilder(message);

		var line = (info == null) ? 0: info.getLine();
		if (line != 0) {
			msg.append(" on line ").append(line);
		}

		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		var msg = new StringBuilder(message);

		var line = (info == null) ? 0: info.getLine();
		if (line != 0) {
			msg.append(" on line ").append(line);
		}

		log.info(msg.toString());
	}

	public boolean passed(){
    	return !isErrorDetected;
    }

	public int getnVars() {
		return nVars;
	}

	public MonomorphizationPlan createMonomorphizationPlan() {
		return monomorphizationPlanner.build();
	}

	@Override
	public void visit(Program program) {
		nVars = Tab.currentScope.getnVars();
		Tab.chainLocalSymbols(programObj);
		Tab.closeScope();
		programObj = null;

		if (!hasMain) {
			report_error("Parameterless void method 'main' was not defined", program);
		}
	}

	@Override
	public void visit(ProgramName programName) {
		programObj = Tab.insert(Obj.Prog, programName.getI1(), Tab.noType);
		Tab.openScope();
		programScope = Tab.currentScope;

		// Reserve space for two global variables used to store temporary values for 'map' statements
		Tab.currentScope.addToLocals(new Obj(Obj.Var, TabUtils.createInternalName("temp1"), Tab.noType));
		Tab.currentScope.addToLocals(new Obj(Obj.Var, TabUtils.createInternalName("temp2"), Tab.noType));
	}

	@Override
	public void visit(TypeAtom_Ident type) {
		var typeName = type.getI1();
		var typeObj = findTypeSymbol(typeName);

		if (typeObj.equals(Tab.noObj)) {
			report_error(String.format("Use of undefined data type '%s'", typeName), type);
			currentType = Tab.noType;
		}
		else if (typeObj.getKind() != Obj.Type) {
			report_error(String.format("Use of invalid data type '%s'", typeName), type);
			currentType = Tab.noType;
		}
		else if (typeObj instanceof GenericTypeObj) {
			report_error(String.format("Generic type '%s' requires explicit type arguments", typeName), type);
			currentType = Tab.noType;
		}
		else {
			currentType = typeObj.getType();
		}

		type.struct = currentType;
	}

	@Override
	public void visit(Type_Generic type) {
		var typeName = type.getI1();
		var typeObj = findTypeSymbol(typeName);

		if (typeObj == Tab.noObj) {
			report_error(String.format("Use of undefined data type '%s'", typeName), type);
			currentType = Tab.noType;
		}
		else if (typeObj.getKind() != Obj.Type) {
			report_error(String.format("Use of invalid data type '%s'", typeName), type);
			currentType = Tab.noType;
		}
		else if (!(typeObj instanceof GenericTypeObj genericType)) {
			report_error(String.format("Type '%s' does not declare generic parameters", typeName), type);
			currentType = Tab.noType;
		}
		else {
			var arguments = type.getTypeArgumentList().typearguments.types();
			try {
				currentType = genericType.applyArguments(arguments);
				if (genericType == currentClass || genericType == currentInterface) {
					genericTypeApplicationsToRevalidate.add(type);
				}
			}
			catch (IllegalArgumentException exception) {
				report_error(exception.getMessage(), type);
				currentType = Tab.noType;
			}
		}

		type.struct = currentType;
	}

	@Override
	public void visit(Type_Atom type) {
		currentType = type.getTypeAtom().struct;
		type.struct = currentType;
	}

	@Override
	public void visit(Type_Array type) {
		var elementType = type.getType().struct;
		tryMarkGenericParameterArrayElement(elementType);

		if (elementType.equals(Tab.noType)) {
			currentType = Tab.noType;
		}
		else if (elementType.equals(TabUtils.setType)) {
			report_error("Array of sets is not supported", type);
			currentType = Tab.noType;
		}
		else {
			currentType = new Struct(Struct.Array, elementType);
		}

		type.struct = currentType;
	}

	@Override
	public void visit(ConstAssign_Num constAssignNum) {
		constAssign(constAssignNum.getI1(), Tab.intType,
				constAssignNum.getN2(), constAssignNum);
	}

	@Override
	public void visit(ConstAssign_Bool constAssignBool) {
		constAssign(constAssignBool.getI1(), TabUtils.boolType,
				constAssignBool.getB2() ? 1 : 0, constAssignBool);
	}

	@Override
	public void visit(ConstAssign_Char constAssignChar) {
		constAssign(constAssignChar.getI1(), Tab.charType,
				(int)constAssignChar.getC2(), constAssignChar);
	}

	/*
	 * VarDecl rule is used following places:
	 * - Global variable declarations
	 * - Local variable declarations (in methods)
	 * - Class field declarations
	 *
	 * The last one is field, and not a variable, so we have to handle
	 * that case differently.
	 *
	 * You are not allowed to redefine a field inside of derived class
	 */

	@Override
	public void visit(VarName_NonArray varName) {
		var name = varName.getI1();
		var varObj = getSymbolObj(name);

		if (varObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), varName);
		}
		if (currentClass != null && currentMethod == null) {
			// If currentMethod is not null, we are inside of a class method definition
			Tab.insert(Obj.Fld, name, currentType);
		}
		else {
			Tab.insert(Obj.Var, name, currentType);
		}
	}

	@Override
	public void visit(VarName_Array varName) {
		var name = varName.getI1();
		var varObj = getSymbolObj(name);
		var arrayTypeStruct = new Struct(Struct.Array, currentType);
        tryMarkGenericParameterArrayElement(currentType);

		if (varObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), varName);
		}
		else if (currentType.equals(TabUtils.setType)) {
			report_error("Array of sets is not supported", varName);
		}
		else if (currentInterface != null) {
			Tab.insert(Obj.Var, name, arrayTypeStruct);
		}
		else if (currentClass != null && currentMethod == null) {
			// if currentMethod is not null, we are inside of a class method definition
			Tab.insert(Obj.Fld, name, arrayTypeStruct);
		}
		else {
			Tab.insert(Obj.Var, name, arrayTypeStruct);
		}
	}

	@Override
	public void visit(GenericParameterScopeStart scopeStart) {
		genericParameterScopes.push(new ArrayList<>());
	}

	@Override
	public void visit(TypeParameter_Unbounded typeParameter) {
		typeParameter.obj = createGenericParameter(typeParameter.getI1(), null, typeParameter);
	}

	@Override
	public void visit(TypeParameter_Bounded typeParameter) {
		typeParameter.obj = createGenericParameter(typeParameter.getI1(), typeParameter.getType().struct, typeParameter);
	}

	@Override
	public void visit(MethodName methodName) {
		var name = methodName.getI1();
		var methodObj = getSymbolObj(name);

		if (methodObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), methodName);
		}
		else {
			var isGenericMethod = methodName.getParent() instanceof GenericMethodSignature;
			if (isGenericMethod) {
				var owner = currentClass != null ? currentClass : currentInterface;
				currentMethod = TabUtils.insert(TabUtils.createGenericMethod(name, currentType,
						getCurrentGenericParameters(), owner, getEnclosingGenericParameters()));
            }
            else {
                currentMethod = Tab.insert(Obj.Meth, name, currentType);
            }
			Tab.openScope();

			if (currentClass != null || currentInterface != null) {
				// Type of return param is not important, since it won't be checked anywhere
				var thisParamObj = Tab.insert(Obj.Var, "this", Tab.noType);
				thisParamObj.setFpPos(paramCount++);
				currentMethod.setFpPos(MethodTypes.LOCAL.value);
			}
			else {
				currentMethod.setFpPos(MethodTypes.GLOBAL.value);
			}
		}

		methodName.obj = currentMethod;
	}

	@Override
	public void visit(MethodReturnType_Void returnType) {
		currentType = Tab.noType;
	}

	@Override
	public void visit(MethodDecl methodDecl) {
		methodDecl.obj = completeCurrentMethod(methodDecl, true);
	}

	@Override
	public void visit(GenericMethodDecl methodDecl) {
		methodDecl.obj = completeCurrentMethod(methodDecl, false);
		popGenericParameterScope();
	}

	private Obj completeCurrentMethod(SyntaxNode methodDecl, boolean canBeMain) {
		if (currentMethod == null) {
			paramCount = 0;
			hasMethodReturned = false;
			return null;
		}

		/*
		 *  Main method must be global, so we check the level, which is set to 0 if the object
		 *  belongs to top scope upon its creation
		 */
		if (canBeMain && currentMethod.getLevel() == 0 && currentMethod.getName().equalsIgnoreCase("main") &&
			currentMethod.getFpPos() == MethodTypes.GLOBAL.value &&
			currentMethod.getType() == Tab.noType && paramCount == 0) {
			if (hasMain) {
				report_error("Multiple definitions of the main method", methodDecl);
			} else {
				hasMain = true;
			}
		}

		if (!currentMethod.getType().equals(Tab.noType) && !hasMethodReturned) {
			report_error(
					String.format("Non-void method '%s' must contain a return statement",
							currentMethod.getName()),
					methodDecl);
		}

		// Do this even if no return statement is detected, so that the analyzing can continue
		currentMethod.setLevel(paramCount);
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();

		var completedMethod = currentMethod;
		currentMethod = null;
		paramCount = 0;
		hasMethodReturned = false;
		return completedMethod;
	}

	@Override
	public void visit(FormParVar_NonArray formParVar) {
		var paramName = formParVar.getI2();
		var paramObj = Tab.currentScope.findSymbol(paramName);

		if (paramObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", paramName), formParVar);
		}
		else {
			paramObj = Tab.insert(Obj.Var, paramName, currentType);
			paramObj.setFpPos(paramCount++);
		}
	}

	@Override
	public void visit(FormParVar_Array formParVar) {
		var paramName = formParVar.getI2();
		var paramObj = Tab.currentScope.findSymbol(paramName);
		tryMarkGenericParameterArrayElement(currentType);

		if (paramObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", paramName), formParVar);
		}
		else if (currentType.equals(TabUtils.setType)) {
			report_error("Array of sets is not supported", formParVar);
		}
		else {
			Struct arrayType = new Struct(Struct.Array, currentType);
			paramObj = Tab.insert(Obj.Var, paramName, arrayType);
			paramObj.setFpPos(paramCount++);
		}
	}

	// TODO: Add support for constructors
	@Override
	public void visit(ClassName_Regular className) {
		var name = className.getI1();
		var classObj = getSymbolObj(name);

		if (classObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), className);
		}
		else {
			currentType = new Struct(Struct.Class);
			currentClass = Tab.insert(Obj.Type, name, currentType);
			Tab.openScope();
			// Virtual method table address field
			Tab.insert(Obj.Fld, SemanticAnalyzer.VirtualMethodTableName, Tab.intType);
		}
	}

	@Override
	public void visit(ClassName_Generic className) {
		var name = className.getI1();
		var classObj = getSymbolObj(name);

		if (classObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), className);
			return;
		}
		if (isGenericParameterDefined(name)) {
			report_error("A class and its generic parameter cannot have the same name", className);
		}

		try {
			var genericClass = TabUtils.createGenericType(name, Struct.Class, getCurrentGenericParameters());
			currentClass = TabUtils.insert(genericClass);
			currentType = currentClass.getType();
			Tab.openScope();
			Tab.insert(Obj.Fld, SemanticAnalyzer.VirtualMethodTableName, Tab.intType);
		}
		catch (IllegalArgumentException exception) {
			report_error(exception.getMessage(), className);
			currentType = Tab.noType;
		}
	}

	@Override
	public void visit(ExtendedTypeName_Valid extendedTypeName) {
		if (currentType == null || currentClass == null) return;
		if (currentType == currentClass.getType() ||
				currentType instanceof GenericTypeApplicationStruct application && application.getDeclaration() == currentClass) {
			report_error("A class cannot extend itself", extendedTypeName);
			return;
		}

		if (currentType.getKind() == Struct.Class) {
			currentClass.getType().setElementType(currentType);
			if (currentType instanceof GenericTypeApplicationStruct application) {
                // For a non-generic class, there is no enclosing generic declaration, so this becomes a root use.
                // This is intentional because ordinary classes are always generated, so their generic bases must also be specialized.
				monomorphizationPlanner.registerTypeUse(extendedTypeName, application.getDeclaration(),
                        application.getTypeArguments(), getEnclosingGenericDeclaration());
			}

			// Add implemented interfaces
			for (Struct interfaceStruct : currentType.getImplementedInterfaces()) {
				currentClass.getType().addImplementedInterface(interfaceStruct);
			}

			// Add fields from extended class
			for (Obj member: currentType.getMembers()) {
				if (member.getKind() == Obj.Meth) {
					continue;
				}

				Tab.insert(Obj.Fld, member.getName(), member.getType());
			}
		}
		else if (currentType.getKind() == Struct.Interface) {
			currentClass.getType().addImplementedInterface(currentType);
			if (currentType instanceof GenericTypeApplicationStruct application) {
				monomorphizationPlanner.registerTypeUse(extendedTypeName, application.getDeclaration(),
						application.getTypeArguments(), getEnclosingGenericDeclaration());
			}
		}
		else {
			report_error("Only class and interface types can be extended", extendedTypeName);
		}
	}

	@Override
	public void visit(ClassDecl_Derived classDecl) {
		if (currentClass == null) {
			clearGenericTypeState();
			return;
		}

		if (currentClass.getType().getElemType() != null) {
			// Check if base methods were overridden correctly, and add the ones that weren't overridden
			var baseClassMethods = currentClass.getType().getElemType().getMembers().stream()
					.filter(o -> o.getKind() == Obj.Meth).toArray(Obj[]::new);

			for (var baseMethod : baseClassMethods) {
				var method = Tab.currentScope.findSymbol(baseMethod.getName());

				if (method == null) {
					// Not overridden, add the method object to the current class
					Tab.currentScope.addToLocals(baseMethod);
				}
				else if (!hasMatchingSignature(baseMethod, method) || !TabUtils.equals(baseMethod.getType(), method.getType())) {
					report_error(String.format("Signature of overridden method '%s' must not be changed", baseMethod.getName()), classDecl);
				}
			}
		}

		// Check if interface has been implemented correctly, and add non-overriden concrete methods
		for (Struct interfaceSruct : currentClass.getType().getImplementedInterfaces()) {
			for (var baseMethod: interfaceSruct.getMembers()) {
				var method = Tab.currentScope.findSymbol(baseMethod.getName());

				if (method == null) {
					if (baseMethod.getFpPos() == MethodTypes.LOCAL_UNIMPLEMENTED.value) {
						report_error(
								String.format("Interface method '%s' must be implemented",
										baseMethod.getName()),
								classDecl);
					}
					else {
						Tab.currentScope.addToLocals(baseMethod);
					}
				}
				else if (!hasMatchingSignature(method, baseMethod) || !TabUtils.equals(baseMethod.getType(), method.getType())) {
                    report_error(String.format("Signature of overridden method '%s' must not be changed", baseMethod.getName()), classDecl);
				}
			}
		}

        classDecl.obj = completeClassDeclaration();
	}

	@Override
	public void visit(ClassDecl_NonDerived classDecl) {
		if (currentClass == null) {
			clearGenericTypeState();
			return;
		}

        classDecl.obj = completeClassDeclaration();
	}

	/*
	 * Default interface methods are copied into class that implements the interface
	 * And after the class definition is completed, we check if method signature was changed
	 * Because that is not allowed for default interface methods
	 *
	 * Interface method declarations are not copied into class
	 * After the class definition is completed, we check if these methods were defined
	 * In the class that implements the interface
	 * (They need to have the same signature)
	 */

	@Override
	public void visit(InterfaceName_Regular interfaceName) {
		var name = interfaceName.getI1();
		var interfaceObj = getSymbolObj(name);

		if (interfaceObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), interfaceName);
		}
		else {
			currentType = new Struct(Struct.Interface);
			currentInterface = Tab.insert(Obj.Type, name, currentType);
			Tab.openScope();
		}
	}

	@Override
	public void visit(InterfaceName_Generic interfaceName) {
		var name = interfaceName.getI1();
		var interfaceObj = getSymbolObj(name);

		if (interfaceObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), interfaceName);
			return;
		}
		if (isGenericParameterDefined(name)) {
			report_error("An interface and its generic parameter cannot have the same name", interfaceName);
		}

		try {
			var genericInterface = TabUtils.createGenericType(name, Struct.Interface, getCurrentGenericParameters());
			currentInterface = TabUtils.insert(genericInterface);
			currentType = currentInterface.getType();
			Tab.openScope();
		}
		catch (IllegalArgumentException exception) {
			report_error(exception.getMessage(), interfaceName);
			currentType = Tab.noType;
		}
	}

	@Override
	public void visit(InterfaceDecl interfaceDecl) {
		if (currentInterface == null) {
			clearGenericTypeState();
			return;
		}
		interfaceDecl.obj = completeInterfaceDeclaration();
	}

	@Override
	public void visit(InterfaceBody_MethodSignature methodSignature) {
		if (currentMethod == null) {
			paramCount = 0;
			return;
		}

		currentMethod.setFpPos(MethodTypes.LOCAL_UNIMPLEMENTED.value);
		currentMethod.setLevel(paramCount);
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		currentMethod = null;
		paramCount = 0;
	}

	@Override
	public void visit(Designator_Ident designator) {
		var currentObj = Tab.find(designator.getI1());
		var currentOwner = currentClass != null ? currentClass : currentInterface;

		if (designator.getI1().equals("this") && currentOwner != null) {
			var thisType = currentOwner instanceof GenericTypeObj genericType
					? GenericTypeUtils.createOpenApplication(genericType)
					: currentOwner.getType();
			designator.obj = new Obj(Obj.Var, "this", thisType);
		}
		else if (currentObj == Tab.noObj) {
			report_error(String.format("Access to undefined variable '%s'", designator.getI1()), designator);
			designator.obj = Tab.noObj;
		}
		else if (currentObj.getKind() != Obj.Con && currentObj.getKind() != Obj.Meth &&
				currentObj.getKind() != Obj.Var && currentObj.getKind() != Obj.Fld) {
			report_error(String.format("Invalid variable '%s'", designator.getI1()), designator);
			designator.obj = Tab.noObj;
		}
		else {
			designator.obj = currentObj;
		}
	}

	@Override
	public void visit(Designator_MemberAccess designator) {
		var currentObj = designator.getDesignator().obj;
		var objectName = designator.getI2();
		var currentObjType = currentObj.getType();
		var lookupType = currentObjType instanceof GenericParameterStruct parameter
				? parameter.getConstraint()
				: currentObjType;

		if (currentObj == Tab.noObj) {
			designator.obj = Tab.noObj;
		}
		else if (currentObj.getKind() != Obj.Elem && currentObj.getKind() != Obj.Fld &&
				currentObj.getKind() != Obj.Var && currentObj.getKind() != Obj.Meth ||
				lookupType == null || // No constraint - don't allow any member access
				(lookupType.getKind() != Struct.Class &&
				lookupType.getKind() != Struct.Interface)) {
			report_error(
					String.format("Access to a member '%s' of a non-complex type",
							designator.getI2()),
					designator);
			designator.obj = Tab.noObj;
		}
		else {
			Obj memberObj;

            // If the current lookup type is a generic type
			var receiverTypeApplication = lookupType instanceof GenericTypeApplicationStruct application
					? application
					: null;

			var currentOwner = currentClass != null ? currentClass : currentInterface;
			var memberBelongsToCurrentType = currentOwner != null &&
					(lookupType == currentOwner.getType() || receiverTypeApplication != null && receiverTypeApplication.getDeclaration() == currentOwner);
			if (currentObj.getName().equals("this") || memberBelongsToCurrentType) {
				/*
				 * If accessing a member of the class or interface currently being declared, we cannot use its type
				 * since it is not completed yet (scope is still open, and members have not been set).
				 * So we need to access the owner scope, which is one scope up, as we are currently in the method scope.
				 */
				memberObj = Tab.currentScope.getOuter().findSymbol(objectName);
			}
			else if (receiverTypeApplication != null) {
				memberObj = receiverTypeApplication.findMember(objectName);
			}
			else {
				memberObj = lookupType.getMembersTable().searchKey(objectName);
			}

            // Members found in the open type scope still use declaration types, so resolve them based on this receiver's application.
            // For example, if we are inside class Node<T>, and it has fields A of type T and B of type Node<T[]>. If we try accessing
            // B and getting its A field, it will no longer be just T, but T[]. This is why we have to perform substitution.
			if (memberObj != null && receiverTypeApplication != null && memberBelongsToCurrentType) {
				memberObj = receiverTypeApplication.resolveMember(memberObj);
			}

			if(memberObj == null || memberObj.getKind() != Obj.Fld && memberObj.getKind() != Obj.Meth) {
				report_error(String.format("Access to an undefined class member '%s'", objectName), designator);
				designator.obj = Tab.noObj;
			}
			else {
				designator.obj = memberObj;
			}
		}
	}

	@Override
	public void visit(Designator_ArrayAccess designator) {
		var currentObj = designator.getDesignator().obj;
		var exprStruct = designator.getExpr().struct;

		if (currentObj == Tab.noObj) {
			designator.obj = Tab.noObj;
		}
		else if (currentObj.getKind() != Obj.Var && currentObj.getKind() != Obj.Fld ||
				currentObj.getType().getKind() != Struct.Array) {
			report_error("Access to an invalid array variable", designator);
			designator.obj = Tab.noObj;
		}
		else if (!exprStruct.equals(Tab.intType)) {
			report_error("Array indexing with a non-integer value", designator);
			designator.obj = Tab.noObj;
		}
		else {
			designator.obj = new Obj(Obj.Elem, String.format(
					"%s[ind]", currentObj.getName()), currentObj.getType().getElemType());
		}
	}

	@Override
	public void visit(Factor_BoolConst factor) {
		factor.struct = TabUtils.boolType;
	}

	@Override
	public void visit(Factor_CharConst factor) {
		factor.struct = Tab.charType;
	}

	@Override
	public void visit(Factor_NumConst factor) {
		factor.struct = Tab.intType;
	}

	@Override
	public void visit(Factor_Designator factor) {
		factor.struct = factor.getDesignator().obj.getType();
	}

	@Override
	public void visit(Factor_Expr factor) {
		factor.struct = factor.getExpr().struct;
	}

	@Override
	public void visit(CallableRef_Plain callableRef) {
		callableRef.obj = callableRef.getDesignator().obj;
	}

	@Override
	public void visit(CallableRef_Applied callableRef) {
		callableRef.obj = callableRef.getDesignator().obj;
	}

	@Override
	public void visit(TypeArgumentList_Type typeArgumentList) {
		typeArgumentList.typearguments = new TypeArguments(List.of(typeArgumentList.getType().struct));
	}

	@Override
	public void visit(TypeArgumentList_List typeArgumentList) {
		typeArgumentList.typearguments = typeArgumentList.getTypeArgumentList().typearguments
                .append(typeArgumentList.getType().struct);
	}

	// TODO: Add reserved keywords (this, ...)

	@Override
	public void visit(Factor_DesignatorCall factor) {
		factor.struct = validateCall(factor.getCallableRef(), factor.getCallPars().struct, factor);
	}

	@Override
	public void visit(Factor_NewArray factor) {
		var elementType = factor.getType().struct;
		tryMarkGenericParameterArrayElement(elementType);

		if (elementType == null || elementType.equals(Tab.noType)) {
			factor.struct = Tab.noType;
		}
		else if (!factor.getExpr().struct.equals(Tab.intType)) {
			report_error("Array creation with a non-integer size value", factor);
			factor.struct = Tab.noType;
		}
		else if (elementType.equals(TabUtils.setType)) {
			factor.struct = elementType;
		}
		else {
			factor.struct = new Struct(Struct.Array, elementType);
		}
	}

	@Override
	public void visit(Factor_NewObject factor) {
		var objectType = factor.getType().struct;

		if (objectType == null || objectType.equals(Tab.noType)) {
			factor.struct = Tab.noType;
		}
		else if (objectType.getKind() != Struct.Class) {
			report_error("Attempt to create an object of a non-class type", factor);
			factor.struct = Tab.noType;
		}
		else {
			factor.struct = objectType;
			if (objectType instanceof GenericTypeApplicationStruct application) {
				monomorphizationPlanner.registerTypeUse(factor, application.getDeclaration(), application.getTypeArguments(),
                        getEnclosingGenericDeclaration());
			}
		}
	}

	/*
	 * Struct object is used to transfer information on argument types
	 * - kind field will be set to `Array`
	 * - implementedInterfaceList field will hold the types of arguments
	 */

	@Override
	public void visit(ActPars_Expr actPars_Expr) {
		var exprStruct = actPars_Expr.getExpr().struct;

		if (exprStruct.equals(Tab.noType)) {
			actPars_Expr.struct = Tab.noType;
		}
		else {
			Struct parsStruct = new Struct(Struct.Array);
			parsStruct.addImplementedInterface(exprStruct);
			actPars_Expr.struct = parsStruct;
		}
	}

	@Override
	public void visit(ActPars_ExprList actPars) {
		if (actPars.getActPars().struct.equals(Tab.noType) ||
				actPars.getExpr().struct.equals(Tab.noType)) {
			actPars.struct = Tab.noType;
		} else {
			actPars.struct = actPars.getActPars().struct;
			actPars.struct.addImplementedInterface(actPars.getExpr().struct);
		}
	}

	@Override
	public void visit(CallPars_ActPars callPars) {
		callPars.struct = callPars.getActPars().struct;
	}

	@Override
	public void visit(CallPars_Empty callPars) {
		callPars.struct = new Struct(Struct.Array);
	}

	@Override
	public void visit(Expr_NonTern expr) {
		expr.struct = expr.getExprNonTern().struct;
	}

	@Override
	public void visit(Expr_Tern expr) {
		expr.struct = expr.getExprTern().struct;
	}

	@Override
	public void visit(ExprTern expr) {
		var trueStruct = expr.getExpr().struct;
		var falseStruct = expr.getExpr1().struct;

		if (!TabUtils.compatibleWith(trueStruct, falseStruct)) {
			report_error("Ternary operator expressions must be of compatible types", expr);
			expr.struct = Tab.noType;
		}
		else if (trueStruct.equals(falseStruct)) {
			expr.struct = trueStruct;
		}
		else {
			// If one of the expressions is null, we want to choose the ref type
			expr.struct = trueStruct.equals(Tab.nullType) ? falseStruct : trueStruct;
		}
	}

	@Override
	public void visit(ExprNonTern_ExprList expr) {
		expr.struct = expr.getExprList().struct;
	}

	@Override
	public void visit(ExprNonTern_Map expr) {
        var rightObj = expr.getDesignator().obj;

        if ((rightObj.getKind() != Obj.Var && rightObj.getKind() != Obj.Fld) ||
                rightObj.getType().getKind() != Struct.Array ||
                !rightObj.getType().getElemType().equals(Tab.intType)) {
            report_error("Right operand of 'map' operator must be an integer array", expr);
            expr.struct = Tab.noType;
            return;
        }

        var arguments = new Struct(Struct.Array);
        arguments.addImplementedInterface(Tab.intType);

        // Validate the call the same way we do for regualr methods
        var resultType = validateCall(expr.getCallableRef(), arguments, expr);
        if (resultType.equals(Tab.noType)) {
            expr.struct = Tab.noType;
        }
        else if (!resultType.equals(Tab.intType)) {
            report_error("Left operand of 'map' operator must be method with integer return type", expr);
            expr.struct = Tab.noType;
        }
        else {
            expr.struct = Tab.intType;
        }
	}

	@Override
	public void visit(ExprList_Term exprList) {
		exprList.struct = exprList.getTerm().struct;
	}

	@Override
	public void visit(ExprList_SubTerm exprList) {
		if (!exprList.getTerm().struct.equals(Tab.intType)) {
			report_error("Negation of a non-integer value", exprList);
			exprList.struct = Tab.noType;
		}
		else {
			exprList.struct = Tab.intType;
		}
	}

	@Override
	public void visit(ExprList_AddopTerm exprList) {
		if (!exprList.getTerm().struct.equals(Tab.intType) ||
				!exprList.getExprList().struct.equals(Tab.intType)) {
			report_error("Addition of non-integer values", exprList);
			exprList.struct = Tab.noType;
		}
		else {
			exprList.struct = Tab.intType;
		}
	}

	@Override
	public void visit(Term_Factor term) {
		term.struct = term.getFactor().struct;
	}

	@Override
	public void visit(Term_MulopFactor term) {
		if (!term.getFactor().struct.equals(Tab.intType) ||
				!term.getTerm().struct.equals(Tab.intType)) {
			report_error("Multiplication of non-integer values", term);
			term.struct = Tab.noType;
		}
		else {
			term.struct = Tab.intType;
		}
	}

	@Override
	public void visit(CondFact_Expr condFact) {
		condFact.struct = condFact.getExprNonTern().struct;
	}

	@Override
	public void visit(CondFact_RelopExpr condFact) {
		var firstOperandStruct = condFact.getExprNonTern().struct;
		var secondOperandStruct = condFact.getExprNonTern1().struct;

		if ((TabUtils.isRefType(firstOperandStruct) || TabUtils.isRefType(secondOperandStruct) ||
				firstOperandStruct instanceof GenericParameterStruct ||
				secondOperandStruct instanceof GenericParameterStruct) &&
				!(condFact.getRelop() instanceof Relop_Equal) &&
				!(condFact.getRelop() instanceof Relop_NotEqual)) {
			report_error(
					"Only '==' and '!=' relational operators can be used with class and array variables",
					condFact);
			condFact.struct = Tab.noType;
		}
		else if (!TabUtils.compatibleWith(firstOperandStruct, secondOperandStruct)) {
			report_error("Attemp to compare variables of non-compatible types", condFact);
			condFact.struct = Tab.noType;
		}
		else {
			// Not important, since it is not used in CondTerm
			condFact.struct = TabUtils.boolType;
		}
	}

	@Override
	public void visit(CondTermList_CondFact condTerm) {
		if (!condTerm.getCondFact().struct.equals(TabUtils.boolType)) {
			report_error("Logical operators cannot be applied to non-boolean operators", condTerm);
		}
	}

	@Override
	public void visit(DesignatorStatement_AssignExpr designatorStatement) {
		var designatorObj = designatorStatement.getDesignator().obj;

		if (designatorObj.getKind() != Obj.Var && designatorObj.getKind() != Obj.Elem &&
				designatorObj.getKind() != Obj.Fld) {
			report_error(
					String.format("Assignment to a non-variable '%s'",
							designatorObj.getName()),
					designatorStatement);
		}
		else if (!TabUtils.assignableTo(designatorObj.getType(),
				designatorStatement.getExpr().struct)) {
			report_error(
					String.format("Assignment of a value with incopatible type to '%s'",
							designatorObj.getName()),
					designatorStatement);
		}
	}

	@Override
	public void visit(DesignatorStatement_Inc designatorStatement) {
		var designatorObj = designatorStatement.getDesignator().obj;

		if (designatorObj.equals(Tab.noObj)) {
			return;
		}
		else if (designatorObj.getKind() != Obj.Var && designatorObj.getKind() != Obj.Elem &&
				designatorObj.getKind() != Obj.Fld) {
			report_error(
					String.format("Attempt to increment a non-variable '%s'",
							designatorObj.getName()),
					designatorStatement);
		}
		else if (!designatorObj.getType().equals(Tab.intType)) {
			report_error(
					String.format("Attempt to increment a non-integer variable '%s'",
							designatorObj.getName()),
					designatorStatement);
		}
	}

	@Override
	public void visit(DesignatorStatement_Dec designatorStatement) {
		var designatorObj = designatorStatement.getDesignator().obj;

		if (designatorObj.equals(Tab.noObj)) {
			return;
		}
		else if (designatorObj.getKind() != Obj.Var && designatorObj.getKind() != Obj.Elem &&
				designatorObj.getKind() != Obj.Fld) {
			report_error(
					String.format("Attempt to decrement a non-variable '%s'",
							designatorObj.getName()),
					designatorStatement);
		}
		else if (!designatorObj.getType().equals(Tab.intType)) {
			report_error(
					String.format("Attempt to decrement a non-integer variable '%s'",
							designatorObj.getName()),
					designatorStatement);
		}
	}

	@Override
	public void visit(DesignatorStatement_Call designatorStatement) {
		validateCall(designatorStatement.getCallableRef(),
				designatorStatement.getCallPars().struct, designatorStatement);
	}

	@Override
	public void visit(DesignatorStatement_AssignSetop designatorStatement) {
		var resultObj = designatorStatement.getDesignator().obj;
		var firstOperandObj = designatorStatement.getDesignator1().obj;
		var secondOperandObj = designatorStatement.getDesignator2().obj;

		if (!firstOperandObj.getType().equals(TabUtils.setType) ||
				!secondOperandObj.getType().equals(TabUtils.setType)) {
			report_error("Both operands of the set operator must be of set type", designatorStatement);
		}
		else if (!resultObj.getType().equals(TabUtils.setType)) {
			report_error(
					"Result of the set operator can only be assigned to a varible of set type",
					designatorStatement);
		}
	}

	@Override
	public void visit(DoToken doToken) {
		loopCount++;
	}

	@Override
	public void visit(Statement_DoWhileTrue statement) {
		loopCount--;
	}

	@Override
	public void visit(Statement_DoWhileCondition statement) {
		loopCount--;
	}

	@Override
	public void visit(Statement_DoWhileConditionWithDesignatorStatement statement) {
		loopCount--;
	}

	@Override
	public void visit(ForPostStatement_Epsilon forPostStatement) {
		loopCount++;
	}

	@Override
	public void visit(ForPostStatement_DesignatorStatement forPostStatement) {
		loopCount++;
	}

	@Override
	public void visit(Statement_For statement) {
		loopCount--;
	}

	@Override
	public void visit(Statement_Break statement) {
		if (loopCount == 0) {
			report_error("Break statement can only be used inside of the loop", statement);
		}
	}

	@Override
	public void visit(Statement_Continue statement) {
		if (loopCount == 0) {
			report_error("Continue statement can only be used inside of the loop", statement);
		}
	}

	@Override
	public void visit(Statement_Read statement) {
		var statementObj = statement.getDesignator().obj;

		if (statementObj.getKind() != Obj.Var && statementObj.getKind() != Obj.Elem &&
				statementObj.getKind() != Obj.Fld) {
			report_error(
					String.format("Attemp to read into a non variable '%s'",
							statementObj.getName()),
					statement);
		}
		else if (!statementObj.getType().equals(Tab.intType) &&
				!statementObj.getType().equals(Tab.charType) &&
				!statementObj.getType().equals(TabUtils.boolType)) {
			report_error(
					String.format("Attemp to read into a variable '%s' of a non-compatible type",
							statementObj.getName()),
					statement);
		}
	}

	@Override
	public void visit(Statement_PrintExprWithNum statement) {
		if (!validatePrintStatementExpr(statement.getExpr().struct)) {
			report_error("Attemp to print a variable of a non-compatible type", statement);
		}
	}

	@Override
	public void visit(Statement_PrintExpr statement) {
		if (!validatePrintStatementExpr(statement.getExpr().struct)) {
			report_error("Attemp to print a variable of a non-compatible type", statement);
		}
	}

	@Override
	public void visit(Statement_ReturnExpr statement) {
		if (currentMethod != null && !TabUtils.assignableTo(currentMethod.getType(), statement.getExpr().struct)) {
			report_error(
					String.format("Type of return value doesn't match the return type of method '%s'",
							currentMethod.getName()),
					statement);
		}
		else {
			hasMethodReturned = true;
		}
	}

	@Override
	public void visit(Statement_ReturnVoid statement) {
		if (currentMethod != null && !currentMethod.getType().equals(Tab.noType)) {
			report_error(
					String.format("Non-void method '%s' has to return a value",
							currentMethod.getName()),
					statement);
		}
		else {
			hasMethodReturned = true;
		}
	}

    private Obj completeClassDeclaration() {
        var completedClass = currentClass;
        currentClass.getType().setMembers(Tab.currentScope.getLocals());
        Tab.closeScope();
        currentClass = null;

        revalidateGenericTypeApplications(completedClass);
        clearGenericTypeState();
        return completedClass;
    }

    private Obj completeInterfaceDeclaration() {
        var completedInterface = currentInterface;
        currentInterface.getType().setMembers(Tab.currentScope.getLocals());
        Tab.closeScope();
        currentInterface = null;

        revalidateGenericTypeApplications(completedInterface);
        clearGenericTypeState();
        return completedInterface;
    }

    private void revalidateGenericTypeApplications(Obj completedType) {
        if (!(completedType instanceof GenericTypeObj)) return;

		for (var type : genericTypeApplicationsToRevalidate) {
			try {
				var application = (GenericTypeApplicationStruct)type.struct;
				application.getDeclaration().validateAndCreateSubstitution(application.getTypeArguments());
			}
			catch (IllegalArgumentException exception) {
				report_error(exception.getMessage(), type);
			}
		}
    }

    private void clearGenericTypeState() {
        popGenericParameterScope();
        genericTypeApplicationsToRevalidate.clear();
    }

	private void constAssign(String name, Struct type, int value, SyntaxNode node) {
		var constObj = getSymbolObj(name);

		if (constObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), node);
		}
		// Constants can only be of int, char, and bool values, so equals() is sufficient
		else if (!currentType.equals(type)) {
			report_error("Assignment of incompatible types", node);
		}
		else {
			Obj newConstObj = Tab.insert(Obj.Con, name, type);
			newConstObj.setAdr(value);
		}
	}

	private Obj createGenericParameter(String name, Struct constraint, SyntaxNode node) {
        var existingParameter = findGenericParameter(name);
        if (existingParameter != null) {
            report_error(String.format("Multiple definitions of generic parameter '%s'", name), node);
            return existingParameter;
        }

		Obj parameter;
		try {
			parameter = TabUtils.createGenericParameter(name, constraint);
		}
		catch (IllegalArgumentException exception) {
			if (constraint != Tab.noType) report_error(exception.getMessage(), node);
			parameter = TabUtils.createGenericParameter(name);
		}

		getCurrentGenericParameters().add(parameter);
		return parameter;
	}

	private Struct validateCall(CallableRef callableRef, Struct argsStruct, SyntaxNode node) {
		var method = callableRef.obj;
		if (method.equals(Tab.noObj)) return Tab.noType;

		if (method.getKind() != Obj.Meth) {
			report_error(String.format("Attemp to call a non-method '%s'", method.getName()), node);
			return Tab.noType;
		}
		if (argsStruct.equals(Tab.noType)) {
			report_error("Invalid call parameters", node);
			return Tab.noType;
		}

		if (callableRef instanceof CallableRef_Plain) {
			if (method instanceof GenericMethodObj) {
				report_error(String.format("Generic method '%s' requires explicit type arguments", method.getName()), node);
				return Tab.noType;
			}
			if (!isCallableWith(method, argsStruct)) {
				reportCallParameterMismatch(method, node);
				return Tab.noType;
			}
			return method.getType();
		}

		if (!(method instanceof GenericMethodObj genericMethod)) {
			report_error(String.format("Method '%s' does not declare generic parameters", method.getName()), node);
			return Tab.noType;
		}

		var appliedRef = (CallableRef_Applied)callableRef;
		try {
            // Find the type arguments applied to the class or interface that declares this method (if they exist),
            // so they can be combined with the method's own type arguments
			var methodTypeArguments = appliedRef.getTypeArgumentList().typearguments.types();
			var ownerApplication = resolveGenericMethodOwnerApplication(genericMethod, appliedRef.getDesignator());
			var ownerTypeArguments = ownerApplication == null ? List.<Struct>of() : ownerApplication.getTypeArguments();
			var ownerSubstitution = ownerApplication == null ? Map.<GenericParameterStruct, Struct>of() : ownerApplication.getSubstitution();
			var substitution = genericMethod.validateAndCreateSubstitution(methodTypeArguments, ownerSubstitution);
			if (!isCallableWith(genericMethod, argsStruct, substitution)) {
				reportCallParameterMismatch(method, node);
				return Tab.noType;
			}
			monomorphizationPlanner.registerMethodUse(appliedRef, genericMethod, ownerTypeArguments, methodTypeArguments, getEnclosingGenericDeclaration());
			return GenericTypeUtils.substituteType(genericMethod.getType(), substitution);
		}
		catch (IllegalArgumentException exception) {
			report_error(exception.getMessage(), node);
			return Tab.noType;
		}
	}

	private void reportCallParameterMismatch(Obj method, SyntaxNode node) {
		report_error(String.format("Passed arguments do not match the parameters of the method '%s'", method.getName()), node);
	}

    private GenericObj getEnclosingGenericDeclaration() {
        if (currentMethod instanceof GenericMethodObj genericMethod) return genericMethod;
		if (currentClass instanceof GenericTypeObj genericType) return genericType;
		return currentInterface instanceof GenericTypeObj genericType ? genericType : null;
    }

    // A generic-aware symbol type lookup. Should be used instead of 'Tab.find()'
    private Obj findTypeSymbol(String name) {
        var genericParameter = findGenericParameter(name);
        return genericParameter != null ? genericParameter : Tab.find(name);
    }

	private GenericTypeApplicationStruct resolveGenericMethodOwnerApplication(GenericMethodObj method, Designator designator) {
		if (!(method.getOwner() instanceof GenericTypeObj genericOwner))
			return null;

		Struct receiverType;
		if (designator instanceof Designator_MemberAccess memberAccess) {
			receiverType = memberAccess.getDesignator().obj.getType();
		}
		else {
			var currentOwner = currentClass != null ? currentClass : currentInterface;
			if (currentOwner instanceof GenericTypeObj currentGenericOwner) {
                receiverType = GenericTypeUtils.createOpenApplication(currentGenericOwner);
            }
			else if (currentOwner != null) {
                receiverType = currentOwner.getType();
            }
			else {
                throw new IllegalArgumentException("A generic member method requires a receiver");
            }
		}

		var ownerApplication = GenericTypeUtils.findGenericTypeApplication(receiverType, genericOwner);
		if (ownerApplication == null)
			throw new IllegalArgumentException("Cannot resolve the generic method's declaring type from the receiver type");
		return ownerApplication;
	}

	private List<Obj> getCurrentGenericParameters() {
		if (genericParameterScopes.isEmpty())
			throw new IllegalStateException("No generic parameter scope is active");
		return genericParameterScopes.peek();
	}

	private List<Obj> getEnclosingGenericParameters() {
		return genericParameterScopes.stream().skip(1).flatMap(List::stream).toList();
	}

    private void popGenericParameterScope() {
        if (!genericParameterScopes.isEmpty()) genericParameterScopes.pop();
    }

    private Obj findGenericParameter(String name) {
        for (var scope : genericParameterScopes) {
            for (var parameter : scope) {
                if (parameter.getName().equals(name)) return parameter;
            }
        }
        return null;
    }

    private boolean isGenericParameterDefined(String name) {
        return findGenericParameter(name) != null;
    }

	private Obj getSymbolObj(String symbolName) {
		Obj tempObj;

		if (Tab.currentScope.equals(programScope)) {
			// If name is inside of top (program) scope, check universe scope as well
			var returnedObj = Tab.find(symbolName);
			tempObj = returnedObj.equals(Tab.noObj) ? null : returnedObj;
		}
		else {
			tempObj = Tab.currentScope.findSymbol(symbolName);
		}

		return tempObj;
	}
}
