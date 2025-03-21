package rs.ac.bg.etf.pp1;

import java.lang.reflect.Member;
import java.security.PublicKey;
import java.security.cert.CertificateNotYetValidException;
import java.util.Collection;

import javax.print.attribute.standard.JobKOctets;
import javax.swing.plaf.synth.SynthProgressBarUI;

import org.apache.log4j.Logger;

import java_cup.internal_error;
import rs.ac.bg.etf.pp1.SymbolTableUtils.MethodTypes;
import rs.ac.bg.etf.pp1.ast.ActPars_Expr;
import rs.ac.bg.etf.pp1.ast.ActPars_ExprList;
import rs.ac.bg.etf.pp1.ast.CallPars_ActPars;
import rs.ac.bg.etf.pp1.ast.CallPars_Empty;
import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.ClassFields;
import rs.ac.bg.etf.pp1.ast.ClassName;
import rs.ac.bg.etf.pp1.ast.CondFact_Expr;
import rs.ac.bg.etf.pp1.ast.CondFact_RelopExpr;
import rs.ac.bg.etf.pp1.ast.CondTerm_AndCondFact;
import rs.ac.bg.etf.pp1.ast.CondTerm_CondFact;
import rs.ac.bg.etf.pp1.ast.ConstAssign;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Bool;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Char;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Num;
import rs.ac.bg.etf.pp1.ast.ConstDecl;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignExpr;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignSetop;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Call;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Dec;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Inc;
import rs.ac.bg.etf.pp1.ast.Designator_ArrayAccess;
import rs.ac.bg.etf.pp1.ast.Designator_Ident;
import rs.ac.bg.etf.pp1.ast.Designator_MemberAccess;
import rs.ac.bg.etf.pp1.ast.ExprList_AddopTerm;
import rs.ac.bg.etf.pp1.ast.ExprList_SubTerm;
import rs.ac.bg.etf.pp1.ast.ExprList_Term;
import rs.ac.bg.etf.pp1.ast.Expr_ExprList;
import rs.ac.bg.etf.pp1.ast.Expr_Map;
import rs.ac.bg.etf.pp1.ast.ExtendedClassName;
import rs.ac.bg.etf.pp1.ast.Factor_BoolConst;
import rs.ac.bg.etf.pp1.ast.Factor_CharConst;
import rs.ac.bg.etf.pp1.ast.Factor_Designator;
import rs.ac.bg.etf.pp1.ast.Factor_DesignatorCall;
import rs.ac.bg.etf.pp1.ast.Factor_Expr;
import rs.ac.bg.etf.pp1.ast.Factor_NewArray;
import rs.ac.bg.etf.pp1.ast.Factor_NewObject;
import rs.ac.bg.etf.pp1.ast.Factor_NumConst;
import rs.ac.bg.etf.pp1.ast.FormParVar;
import rs.ac.bg.etf.pp1.ast.FormParVar_Array;
import rs.ac.bg.etf.pp1.ast.FormParVar_NonArray;
import rs.ac.bg.etf.pp1.ast.InterfaceBody_MethodSignature;
import rs.ac.bg.etf.pp1.ast.InterfaceDecl;
import rs.ac.bg.etf.pp1.ast.InterfaceName;
import rs.ac.bg.etf.pp1.ast.MethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodName;
import rs.ac.bg.etf.pp1.ast.MethodReturnType_Void;
import rs.ac.bg.etf.pp1.ast.MethodSignature_NoPars;
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
import rs.ac.bg.etf.pp1.ast.Type;
import rs.ac.bg.etf.pp1.ast.VarDeclList_Epsilon;
import rs.ac.bg.etf.pp1.ast.VarDeclList_VarDecl;
import rs.ac.bg.etf.pp1.ast.VarName;
import rs.ac.bg.etf.pp1.ast.VarName_Array;
import rs.ac.bg.etf.pp1.ast.VarName_NonArray;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;
import rs.etf.pp1.symboltable.structure.SymbolDataStructure;
import rs.ac.bg.etf.pp1.ast.Statement_DoWhileTrue;
import rs.ac.bg.etf.pp1.ast.Statement_PrintExpr;
import rs.ac.bg.etf.pp1.ast.Statement_PrintExprWithNum;
import rs.ac.bg.etf.pp1.ast.Statement_Read;
import rs.ac.bg.etf.pp1.ast.Statement_ReturnExpr;
import rs.ac.bg.etf.pp1.ast.Statement_ReturnVoid;
import rs.ac.bg.etf.pp1.ast.DoToken;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private final static String VritualMethodTableName = "__vtp";
	
	private Obj programObj = null;
	private Struct currentType = null;
	private Obj currentMethod = null;
	private Obj currentClass = null;
	private Obj currentInterface = null;
	private int paramCount = 0;	
	private int loopCount = 0;
	private boolean methodReturned = false;
	private boolean hasMain = false;
	
	boolean errorDetected = false;
	int nVars = 0;
	
	private Logger log = Logger.getLogger(getClass());

	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0) {
			msg.append(" on line ").append(line);
		}
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0) {
			msg.append(" on line ").append(line);
		}
		log.info(msg.toString());
	}
	
	public boolean passed(){
    	return !errorDetected;
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
	}
	
	@Override
	public void visit(Type type) {
		Obj tempObj = Tab.find(type.getI1());
		currentType = Tab.noType;
		
		if (tempObj == Tab.noObj) {
			report_error(String.format("Use of undefined data type '%s'", type.getI1()), type);
		}
		else if (tempObj.getKind() != Obj.Type) {
			report_error(String.format("Use of invalid data type '%s'", type.getI1()), type);
		} 
		else {
			currentType = tempObj.getType();
		}
		
		type.struct = currentType;
	}
	
	// TODO: Check for multilevel inheritance?
	
	// Constant declarations
	
	private void constAssign(String name, Struct type, int value, SyntaxNode node) {
		// Maybe universe scope should be checked as well?
		Obj tempObj = Tab.currentScope.findSymbol(name);
		
		if (tempObj != null) {
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
	
	@Override
	public void visit(ConstAssign_Num constAssignNum) {
		constAssign(constAssignNum.getI1(), Tab.intType, constAssignNum.getN2(), constAssignNum);
	}
	
	@Override
	public void visit(ConstAssign_Bool constAssignBool) {
		constAssign(constAssignBool.getI1(), SymbolTableUtils.boolType, constAssignBool.getB2() ? 1 : 0, constAssignBool);
	}
	
	@Override
	public void visit(ConstAssign_Char constAssignChar) {
		constAssign(constAssignChar.getI1(), Tab.charType, (int)constAssignChar.getC2(), constAssignChar);
	}
	
	// Variable declarations
	
	/*
	 * VarDecl rule is used following places:
	 * - Global variable declarations
	 * - Local variable declarations (in methods)
	 * - Class field declarations
	 * 
	 * The last one is field, and not a variable, so we have to handle
	 * that case differently.
	 * 
	 * TODO: Check if this is the expected behavior
	 * You are not allowed to redefine a filed inside of derived class
	 */
	
	@Override
	public void visit(VarName_NonArray varName) {
		// Maybe universe scope should be checked as well?
		Obj tempObj = Tab.currentScope.findSymbol(varName.getI1());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", varName.getI1()), varName);
		} 
		else if (currentInterface != null) {
			tempObj = Tab.insert(Obj.Var, varName.getI1(), currentType);
			// Increment 'adr' by one to make things a bit easier later on
			tempObj.setAdr(tempObj.getAdr() + 1);
		}
		else if (currentClass != null && currentMethod == null) {
			// if currentMethod is not null, we are inside of a class method definition
			tempObj = Tab.insert(Obj.Fld, varName.getI1(), currentType);
		}
		else {
			tempObj = Tab.insert(Obj.Var, varName.getI1(), currentType);
		}
	}
	
	@Override
	public void visit(VarName_Array varName) {
		// Maybe universe scope should be checked as well?
		Obj tempObj = Tab.currentScope.findSymbol(varName.getI1());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", varName.getI1()), varName);
		} 
		else if (currentType.equals(SymbolTableUtils.setType)) {
			report_error("Array of sets is not supported", varName);
		}
		else if (currentInterface != null) {
			Struct arrayType = new Struct(Struct.Array, currentType);
			tempObj = Tab.insert(Obj.Var, varName.getI1(), arrayType);
			// Increment 'adr' by one to make things a bit easier later on
			tempObj.setAdr(tempObj.getAdr() + 1);
		}
		else if (currentClass != null && currentMethod == null) {
			// if currentMethod is not null, we are inside of a class method definition
			Struct arrayType = new Struct(Struct.Array, currentType);
			tempObj = Tab.insert(Obj.Fld, varName.getI1(), arrayType);
		}
		else {
			Struct arrayType = new Struct(Struct.Array, currentType);
			tempObj = Tab.insert(Obj.Var, varName.getI1(), arrayType);
		}
	}
	
	// Method declarations

	@Override
	public void visit(MethodName methodName) {
		Obj tempObj = Tab.currentScope.findSymbol(methodName.getI1());
		
		if (tempObj != null) {
			if (tempObj.getKind() != Obj.Meth || currentClass == null || 
					(tempObj.getFpPos() != MethodTypes.CLASS_INHERITED.value && 
					tempObj.getFpPos() != MethodTypes.INTERFACE_INHERITED.value)) {
				report_error(String.format("Multiple definitions of the name '%s'", methodName.getI1()), methodName);
			}
			else {
				// Method overriding
				
				if (!currentType.equals(tempObj.getType())) {
					report_error(String.format("Attemp to override a return type for method '%s'", methodName.getI1()), methodName);
				}
				else {
					currentMethod = tempObj;
					if (tempObj.getFpPos() == MethodTypes.CLASS_INHERITED.value) {
						currentMethod.setFpPos(MethodTypes.OVERRIDDEN.value);
					}
					Tab.openScope();
					Obj thisParamObj = Tab.insert(Obj.Var, "this", currentClass.getType());
					thisParamObj.setFpPos(paramCount++);
				}
			}
		}
		else {
			currentMethod = Tab.insert(Obj.Meth, methodName.getI1(), currentType);
			
			if (currentInterface != null) {
				currentMethod.setFpPos(MethodTypes.INTERFACE_REGULAR.value);
			}
			else if (currentClass != null) {
				currentMethod.setFpPos(MethodTypes.CLASS_REGULAR.value);
			}
			else {
				currentMethod.setFpPos(MethodTypes.REGULAR.value);
			}
			
			Tab.openScope();
			
			if (currentClass != null) {
				// Class method
				Obj thisParamObj = Tab.insert(Obj.Var, "this", currentClass.getType());
				thisParamObj.setFpPos(paramCount++);
			}
			
			if (currentInterface != null) {
				paramCount++;
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
		if (currentMethod == null) {
			paramCount = 0;
			return;
		}
		
		// TODO: Do not use Tab.noType for invalid types, so that the 'main not defined' error
		// can be reported even if the main return type is invalid (right now it won't do that)
		
		/*
		 *  Main method must be global, so we check the level, which is set to 0 if the object
		 *  belongs to top scope upon its creation
		 */
		if (currentMethod.getLevel() == 0 && currentMethod.getName().equalsIgnoreCase("main") &&
			currentMethod.getType() == Tab.noType && paramCount == 0) {
			hasMain = true;
		}
		
		if (!currentMethod.getType().equals(Tab.noType) && !methodReturned) {
			report_error(String.format(
					"Non-void method '%s' must contain a return statement", currentMethod.getName()), methodDecl);
		}
		
		// Do this even if no return method is detected, so that the analyzing can continue correctly
		currentMethod.setLevel(paramCount);
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		
		if (currentMethod.getFpPos() == MethodTypes.OVERRIDDEN.value) {
			// Check if it was overridden correctly
			Obj baseMethod = currentClass.getType().getElemType().getMembersTable().searchKey(currentMethod.getName());
			
			if (baseMethod.getLevel() != currentMethod.getLevel()) {
				report_error(String.format(
						"Overriden method '%s' must have the same signature as the base method", 
						currentMethod.getName()), methodDecl);
			}
			else {
				Obj[] baseMethodLocals = baseMethod.getLocalSymbols().toArray(new Obj[baseMethod.getLocalSymbols().size()]);
				Obj[] currentMethodLocals = currentMethod.getLocalSymbols().toArray(new Obj[currentMethod.getLocalSymbols().size()]);
				for (int i = 1; i < currentMethod.getLevel(); i++) {
					if (!baseMethodLocals[i].getType().equals(currentMethodLocals[i].getType())) {
						report_error(String.format(
								"Parameters of overridden method '%s' must not be changed", currentMethod.getName()), methodDecl);
						break;
					}
				}
			}
		}
		
		if (currentMethod.getFpPos() == MethodTypes.CLASS_INHERITED.value ||
				currentMethod.getFpPos() == MethodTypes.OVERRIDDEN.value) {
			// Class inherited methods become regular class methods and can be overridden again in derived class
			currentMethod.setFpPos(MethodTypes.CLASS_REGULAR.value);
		}
		else if (currentMethod.getFpPos() == MethodTypes.INTERFACE_INHERITED.value) {
			// They stay interface_inherited, they can be redefined in derived classes
		}
		
		currentMethod = null;
		paramCount = 0;
		methodReturned = false;
	}
	
	@Override
	public void visit(FormParVar_NonArray formParVar) {	
		Obj tempObj = Tab.currentScope.findSymbol(formParVar.getI2());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", formParVar.getI2()), formParVar);
		}
		else {
			tempObj = Tab.insert(Obj.Var, formParVar.getI2(), currentType);
			tempObj.setFpPos(paramCount++);
			
			if (currentInterface != null) tempObj.setAdr(tempObj.getAdr() + 1); 
		}
	}
	
	@Override
	public void visit(FormParVar_Array formParVar) {
		Obj tempObj = Tab.currentScope.findSymbol(formParVar.getI2());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", formParVar.getI2()), formParVar);
		}
		else if (currentType.equals(SymbolTableUtils.setType)) {
			report_error("Array of sets is not supported", formParVar);
		}
		else {
			Struct arrayType = new Struct(Struct.Array, currentType);
			tempObj = Tab.insert(Obj.Var, formParVar.getI2(), arrayType);
			tempObj.setFpPos(paramCount++);
			
			if (currentInterface != null) tempObj.setAdr(tempObj.getAdr() + 1); 
		}
	}
	
	// Class declarations
	
	// TODO: Add support for constructors
	
	@Override
	public void visit(ClassName className) {
		// TODO: Classes are types, maybe their names should be treated differently
		Obj tempObj = Tab.currentScope.findSymbol(className.getI1());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", className.getI1()), className);
		} 
		else {
			currentType = new Struct(Struct.Class);
			currentClass = Tab.insert(Obj.Type, className.getI1(), currentType);
			Tab.openScope();
			Tab.insert(Obj.Fld, SemanticAnalyzer.VritualMethodTableName, Tab.intType);	// Virtual method table address
		}
	}
	
	@Override
	public void visit(ExtendedClassName extendedClassName) {
		if (currentType == null || currentClass == null) return;
		
		if (currentType.getKind() == Struct.Class) {
			currentClass.getType().setElementType(currentType);
			
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
		} 
		else {
			report_error("Only class and interface types can be extended", extendedClassName);
		}
	}
	
	@Override
	public void visit(ClassDecl_Derived classDecl) {
		if (currentClass == null) return;
		
		// Check if interface has been implemented correctly
		for (Struct interfaceSruct : currentClass.getType().getImplementedInterfaces()) {
			for (Obj member: interfaceSruct.getMembers()) {
				Obj methodObj = Tab.currentScope.findSymbol(member.getName());
				
				if (methodObj == null) {
					report_error(String.format("Interface method '%s' must be implemented", member.getName()), classDecl);
				} 
				else if (methodObj.getLevel() != member.getLevel()) {
					report_error(String.format("Interface method '%s' signature must not be changed", member.getName()), classDecl);
				}
				else {
					int cnt = member.getLevel() - 1;
					if (cnt > 0) {
						for (Obj param : member.getLocalSymbols()) {
							if (!methodObj.getLocalSymbols().contains(param)) {
								report_error(String.format("Signature of the interface method '%s' must not be changed", member.getName()), classDecl);
								break;
							}
							
							if (--cnt == 0) break;
						}
					}
					methodObj.setFpPos(MethodTypes.INTERFACE_INHERITED.value);
				}
			}
		}
		
		currentClass.getType().setMembers(Tab.currentScope.getLocals());
		Tab.closeScope();
		
		// Set method types for class methods that were inherited but not overridden to regular		
		for (Obj member : currentClass.getType().getMembers()) {
			if (member.getKind() == Obj.Meth && member.getFpPos() == MethodTypes.CLASS_INHERITED.value) {
				member.setFpPos(MethodTypes.CLASS_REGULAR.value);
			}
		}
		
		classDecl.obj = currentClass;
		currentClass = null;
	}
	
	@Override
	public void visit(ClassDecl_NonDerived classDecl) {
		if (currentClass == null) return;
		
		currentClass.getType().setMembers(Tab.currentScope.getLocals());
		Tab.closeScope();
		
		// Set method types for class methods that were inherited but not overridden to regular		
		for (Obj member : currentClass.getType().getMembers()) {
			if (member.getKind() == Obj.Meth && member.getFpPos() == MethodTypes.CLASS_INHERITED.value) {
				member.setFpPos(MethodTypes.CLASS_REGULAR.value);
			}
		}
		
		classDecl.obj = currentClass;
		currentClass = null;
	}
	
	@Override
	public void visit(ClassFields classFields) {
		if (currentClass == null) return;
		
		Struct extendedType = currentClass.getType().getElemType();
		
		if (extendedType != null) {
			// Copy all of the methods from the extended class
			for (Obj member: extendedType.getMembers()) {
				if (member.getKind() == Obj.Fld) {
					continue;
				}
				
				// Create copy of method object
				Obj methodObj = Tab.insert(Obj.Meth, member.getName(), member.getType());
				methodObj.setLevel(member.getLevel());
				methodObj.setFpPos(MethodTypes.CLASS_INHERITED.value);
				
				// Copy method parameters, and change the type of 'this' parameter to the type of the current class
				SymbolDataStructure copiedLocals = new HashTableDataStructure();
				for (Obj local : member.getLocalSymbols()) {
					Struct localType = local.getName() == "this" ? currentClass.getType() : local.getType();
					Obj paramCopy = new Obj(local.getKind(), local.getName(), localType);
					paramCopy.setAdr(local.getAdr());
					paramCopy.setLevel(local.getLevel());
					
					copiedLocals.insertKey(paramCopy);
				}
				
				methodObj.setLocals(copiedLocals);
			}
		}
		
		for (Struct interfaceSruct : currentClass.getType().getImplementedInterfaces()) {
			// Add default interface method, and include 'this' parameter
			for (Obj member: interfaceSruct.getMembers()) {
				if (member.getFpPos() != MethodTypes.INTERFACE_REGULAR.value) {
					continue;
				}
				
				// Create copy of method object
				Obj methodObj = Tab.insert(Obj.Meth, member.getName(), member.getType());
				methodObj.setLevel(member.getLevel()); 
				methodObj.setFpPos(MethodTypes.INTERFACE_INHERITED.value);
				
				// Copy method parameters, and change the type of 'this' parameter to the type of the current class
				SymbolDataStructure copiedLocals = new HashTableDataStructure();
				
				Obj thisParamObj = new Obj(Obj.Var, "this", currentClass.getType());
				thisParamObj.setAdr(0);
				copiedLocals.insertKey(thisParamObj);
				
				for (Obj local : member.getLocalSymbols()) {
					Obj paramCopy = new Obj(local.getKind(), local.getName(), local.getType());
					paramCopy.setAdr(local.getAdr());
					paramCopy.setLevel(local.getLevel());
					
					copiedLocals.insertKey(paramCopy);
				}
				
				methodObj.setLocals(copiedLocals);
			}
		}
		
	}
	
	// Interface declarations
	
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
	public void visit(InterfaceName interfaceName) {
		// TODO: Interfaces are types, maybe their names should be treated differently
		Obj tempObj = Tab.currentScope.findSymbol(interfaceName.getI1());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", interfaceName.getI1()), interfaceName);
		}
		else {
			currentType = new Struct(Struct.Interface);
			currentInterface = Tab.insert(Obj.Type, interfaceName.getI1(), currentType);
			Tab.openScope();
		}
	}
	
	@Override
	public void visit(InterfaceDecl interfaceDecl) {
		if (currentInterface == null) return;
		
		currentInterface.getType().setMembers(Tab.currentScope.getLocals());
		currentInterface = null;
		Tab.closeScope();
	}
	
	@Override
	public void visit(InterfaceBody_MethodSignature methodSignature) {
		if (currentMethod == null) {
			paramCount = 0;
			return;
		}
		
		currentMethod.setFpPos(MethodTypes.INTERFACE_NOT_IMPLEMENTED.value);
		currentMethod.setLevel(paramCount);
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		currentMethod = null;
		paramCount = 0;
	}
	
	// Designator 
	
	@Override
	public void visit(Designator_Ident designator) {
		Obj currentObj = Tab.find(designator.getI1());
		
		if (designator.getI1().equals("this") && currentClass != null) {
			designator.obj = new Obj(Obj.Var, "this", currentClass.getType());
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
		Obj currentObj = designator.getDesignator().obj;
		
		if (designator.getI2().equals(SemanticAnalyzer.VritualMethodTableName)) {
			report_error("Access to compiler generate fields is not allowed", designator);
			designator.obj = Tab.noObj;
		}
		else if (currentObj == Tab.noObj) {
			// Error will already be reported
			// report_error("Access to undefined variable ", designator);
			designator.obj = Tab.noObj;
		}
		else if (currentObj.getKind() != Obj.Elem && currentObj.getKind() != Obj.Fld &&
				currentObj.getKind() != Obj.Var && currentObj.getKind() != Obj.Meth ||
				(currentObj.getType().getKind() != Struct.Class && currentObj.getType().getKind() != Struct.Interface)) {
			report_error(String.format("Access to a memeber of a non-complex type", designator.getI2()), designator);
			designator.obj = Tab.noObj;
		}
		else {
			Obj memberObj = null;
			
			if (currentObj.getName().equals("this")) {
				/*
				 * If accessing the class member via 'this' reference, we cannot use the type, since
				 * it is not completed yet (scope is still open, and members have not been set)
				 * So we need to access the class scope, which is one scope up, since we are in the
				 * method scope at the moment
				 */
				memberObj = Tab.currentScope.getOuter().findSymbol(designator.getI2());
			}
			else {
				memberObj = currentObj.getType().getMembersTable().searchKey(designator.getI2());
			}
			
			if(memberObj == null || memberObj.getKind() != Obj.Fld && memberObj.getKind() != Obj.Meth) {
				report_error(String.format("Access to an undefined class member '%s'", designator.getI2()), designator);
				designator.obj = Tab.noObj;
			}
			else {
				designator.obj = memberObj;
			}
		}
	}
	
	@Override
	public void visit(Designator_ArrayAccess designator) {
		Obj currentObj = designator.getDesignator().obj;
		
		if (currentObj == Tab.noObj) {
			// Error will already be reported
			// report_error("Access to undefined variable ", designator);
			designator.obj = Tab.noObj;
		}
		else if (currentObj.getKind() != Obj.Var && currentObj.getKind() != Obj.Fld ||
				currentObj.getType().getKind() != Struct.Array) {
			report_error("Access to an invalid array variable", designator);
			designator.obj = Tab.noObj;
		}
		else if (!designator.getExpr().struct.equals(Tab.intType)) {
			report_error("Array indexing with a non-integer value", designator);
			designator.obj = Tab.noObj;
		}
		else {
			designator.obj = new Obj(Obj.Elem, 
					String.format("%s[ind]", currentObj.getName()), currentObj.getType().getElemType());
		}
	}
	
	@Override
	public void visit(Factor_BoolConst factor) {
		factor.struct = SymbolTableUtils.boolType;
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
	
	// TODO: Add reserved keywords (this, ...)
	
	@Override
	public void visit(Factor_DesignatorCall factor) {
		if (factor.getDesignator().obj.equals(Tab.noObj)) {
			// Error will already be reported
			factor.struct = Tab.noType;
		}
		else if (factor.getDesignator().obj.getKind() != Obj.Meth) {
			report_error(String.format("Attemp to call a non-method '%s'", factor.getDesignator().obj.getName()), factor);
			factor.struct = Tab.noType;
		} 
		else if (factor.getCallPars().struct.equals(Tab.noType)) {
			report_error("Invalid call parameters", factor);
			factor.struct = Tab.noType;
		}
		/*
		else if (currentMethod != null && factor.getDesignator().obj.equals(currentMethod)) {
			report_error("Direct recursion is not allowed", factor);
			factor.struct = Tab.noType;
		}
		*/
		else {
			// Check parameters, but be careful because of 'this' in class methods
			
			int paramCount = factor.getDesignator().obj.getLevel();
			Obj[] params = factor.getDesignator().obj.getLocalSymbols()
					.toArray(new Obj[factor.getDesignator().obj.getLocalSymbols().size()]);
			/*
			 * Also check for INTERFACE_REGULAR and INTERFACE_NOT_IMPLEMENTED because method can be called
			 * on object which has interface type at compile time
			 */
			boolean hasThisParam = (factor.getDesignator().obj.getFpPos() == MethodTypes.CLASS_REGULAR.value ||
					factor.getDesignator().obj.getFpPos() == MethodTypes.INTERFACE_INHERITED.value ||
					factor.getDesignator().obj.getFpPos() == MethodTypes.INTERFACE_REGULAR.value ||
					factor.getDesignator().obj.getFpPos() == MethodTypes.INTERFACE_NOT_IMPLEMENTED.value);
			
			paramCount -= (hasThisParam ? 1 : 0);
			
			Struct[] args = factor.getCallPars().struct.getImplementedInterfaces()
					.toArray(new Struct[factor.getCallPars().struct.getImplementedInterfaces().size()]);
			
			if (paramCount != args.length) {
				report_error(String.format(
						"Number of arguments doesn't match the number of parameters for method '%s'", 
						factor.getDesignator().obj.getName()), factor);
				factor.struct = Tab.noType;
			} 
			else {
				boolean valid = true;
				for (int i = 0; i < paramCount; i++) {
					if (!SymbolTableUtils.assignableTo(params[i + (hasThisParam ? 1 : 0)].getType(), args[i])) {
						valid = false;
						break;
					}
				}
				
				if (!valid) {
					report_error(String.format(
							"Argument types do not match the parameter types for method '%s'", 
							factor.getDesignator().obj.getName()), factor);
					factor.struct = Tab.noType;
				}
				else {
					factor.struct = factor.getDesignator().obj.getType();
				}
			}			
		}
	}
	
	@Override
	public void visit(Factor_NewArray factor) {
		if (currentType == null) {
			// Error will already be reported
			factor.struct = Tab.noType;
		}
		else if (!factor.getExpr().struct.equals(Tab.intType)) {
			report_error("Array creation with a non-integer size value", factor);
			factor.struct = Tab.noType;
		}
		else if (currentType.equals(SymbolTableUtils.setType)) {
			factor.struct = currentType;
		}
		else {
			factor.struct = new Struct(Struct.Array, currentType);
		}
	}
	
	@Override
	public void visit(Factor_NewObject factor) {
		if (currentType == null) {
			// Error will already be reported
			factor.struct = Tab.noType;
		}
		else if (currentType.getKind() != Struct.Class) {
			report_error("Attempt to create an object of a non-class type", factor);
			factor.struct = Tab.noType;
		}
		else {
			factor.struct = currentType;
		}
	}
	
	/*
	 * Struct object is used to transfer information on argument types
	 * - kind field will be set to `Array`
	 * - implementedInterfaceList field will hold the types of arguments
	 */
	
	@Override
	public void visit(ActPars_Expr actPars_Expr) {
		if (actPars_Expr.getExpr().struct.equals(Tab.noType)) {
			// Error will already be reported
			actPars_Expr.struct = Tab.noType;
		}
		else {
			Struct parsStruct = new Struct(Struct.Array);
			parsStruct.addImplementedInterface(actPars_Expr.getExpr().struct);
			actPars_Expr.struct = parsStruct;
		}
	}
	
	@Override
	public void visit(ActPars_ExprList actPars) {
		if (actPars.getActPars().struct.equals(Tab.noType) || actPars.getExpr().struct.equals(Tab.noType)) {
			// Error will already be reported
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
	
	// Expr
	
	@Override
	public void visit(Expr_ExprList expr) {
		expr.struct = expr.getExprList().struct;
	}
	
	@Override
	public void visit(Expr_Map expr) {
		Obj leftObj = expr.getDesignator().obj, righObj = expr.getDesignator1().obj;
		
		if (righObj.getKind() != Obj.Var && righObj.getKind() != Obj.Fld || 
				righObj.getType().getKind() != Struct.Array || !righObj.getType().getElemType().equals(Tab.intType)) {
			report_error("Right operand of 'map' operator must be an integer array", expr);
			expr.struct = Tab.noType;
			
		}
		else if (leftObj.getKind() != Obj.Meth || !leftObj.getType().equals(Tab.intType)) {
			report_error("Left operand of 'map' operator must be method with integer return type", expr);
			expr.struct = Tab.noType;
		}
		else {
			int paramCount = leftObj.getLevel();
			Obj[] params = leftObj.getLocalSymbols()
					.toArray(new Obj[leftObj.getLocalSymbols().size()]);
			boolean hasThisParam = (leftObj.getFpPos() == MethodTypes.CLASS_REGULAR.value ||
					leftObj.getFpPos() == MethodTypes.INTERFACE_INHERITED.value ||
					leftObj.getFpPos() == MethodTypes.INTERFACE_REGULAR.value ||
					leftObj.getFpPos() == MethodTypes.INTERFACE_NOT_IMPLEMENTED.value);
			
			if (!(!hasThisParam && paramCount == 1 && params[0].getType().equals(Tab.intType)) &&
					!(hasThisParam && paramCount == 2 && params[1].getType().equals(Tab.intType))) {
				report_error("Left operand of 'map' operator must be method with a single integer parameter", expr);
				expr.struct = Tab.noType;
			}
			else {
				expr.struct = Tab.intType;
			}
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
		if (!exprList.getTerm().struct.equals(Tab.intType) || !exprList.getExprList().struct.equals(Tab.intType)) {
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
		if (!term.getFactor().struct.equals(Tab.intType) || !term.getTerm().struct.equals(Tab.intType)) {
			report_error("Multiplication of non-integer values", term);
			term.struct = Tab.noType;
		}
		else {
			term.struct = Tab.intType;
		}
	}
	
	// Conditions
	
	@Override
	public void visit(CondFact_Expr condFact) {
		condFact.struct = condFact.getExpr().struct;
	}
	
	@Override
	public void visit(CondFact_RelopExpr condFact) {
		if ((condFact.getExpr().struct.isRefType() || condFact.getExpr1().struct.isRefType()) &&
				!(condFact.getRelop() instanceof Relop_Equal || condFact.getRelop() instanceof Relop_NotEqual)) {
			report_error("Only equality and non-equality relational operators can be used with class and array variables", condFact);
			condFact.struct = Tab.noType;
		} 
		else if (!condFact.getExpr().struct.compatibleWith(condFact.getExpr1().struct)) {
			report_error("Attemp to compare variables of non-compatible types", condFact);
			condFact.struct = Tab.noType;
		} 
		else {
			// Not important, since it is not used in CondTerm
			condFact.struct = SymbolTableUtils.boolType;
		}
	}
	
	@Override
	public void visit(CondTerm_CondFact condTerm) {
		if (!condTerm.getCondFact().struct.equals(SymbolTableUtils.boolType)) {
			report_error("Logical operators cannot be applied to non-boolean operators", condTerm);
		}
	}
	
	// Statements
	
	@Override
	public void visit(DesignatorStatement_AssignExpr designatorStatement) {
		Obj designatorObj = designatorStatement.getDesignator().obj;
		
		if (designatorObj.getKind() != Obj.Var && designatorObj.getKind() != Obj.Elem &&
				designatorObj.getKind() != Obj.Fld) {
			report_error(String.format("Assignment to a non-variable '%s'", designatorObj.getName()), designatorStatement);
		}
		else if (!SymbolTableUtils.assignableTo(designatorObj.getType(), designatorStatement.getExpr().struct)) {
			report_error(String.format("Assignment of a value with incopatible type to '%s'", designatorObj.getName()), designatorStatement);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_Inc designatorStatement) {
		Obj designatorObj = designatorStatement.getDesignator().obj;
		
		if (designatorObj.getKind() != Obj.Var && designatorObj.getKind() != Obj.Elem &&
				designatorObj.getKind() != Obj.Fld) {
			report_error(String.format(
					"Attempt to increment a non-variable '%s'", designatorObj.getName()), designatorStatement);
		}
		else if (!designatorObj.getType().equals(Tab.intType)) {
			report_error(String.format(
					"Attempt to increment a non-integer variable '%s'", designatorObj.getName()), designatorStatement);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_Dec designatorStatement) {
		Obj designatorObj = designatorStatement.getDesignator().obj;
		
		if (designatorObj.getKind() != Obj.Var && designatorObj.getKind() != Obj.Elem &&
				designatorObj.getKind() != Obj.Fld) {
			report_error(String.format(
					"Attempt to decrement a non-variable '%s'", designatorObj.getName()), designatorStatement);
		}
		else if (!designatorObj.getType().equals(Tab.intType)) {
			report_error(String.format(
					"Attempt to decrement a non-integer variable '%s'", designatorObj.getName()), designatorStatement);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_Call designatorStatement) {
		Obj designatorObj = designatorStatement.getDesignator().obj;
		
		if (designatorObj.equals(Tab.noObj)) {
			// Error will already be reported
		}
		else if (designatorObj.getKind() != Obj.Meth) {
			report_error(String.format(
					"Attemp to call a non-method '%s'", designatorObj.getName()), designatorStatement);
		} 
		else if (designatorStatement.getCallPars().struct.equals(Tab.noType)) {
			report_error("Invalid call parameters", designatorStatement);
		}
		/*
		else if (currentMethod != null && factor.getDesignator().obj.equals(currentMethod)) {
			report_error("Direct recursion is not allowed", factor);
			factor.struct = Tab.noType;
		}
		*/
		else {
			// Check parameters, but be careful because of 'this' in class methods
			
			int paramCount = designatorObj.getLevel();
			Obj[] params = designatorObj.getLocalSymbols()
					.toArray(new Obj[designatorObj.getLocalSymbols().size()]);
			boolean hasThisParam = (designatorObj.getFpPos() == MethodTypes.CLASS_REGULAR.value ||
					designatorObj.getFpPos() == MethodTypes.INTERFACE_INHERITED.value ||
					designatorObj.getFpPos() == MethodTypes.INTERFACE_REGULAR.value ||
					designatorObj.getFpPos() == MethodTypes.INTERFACE_NOT_IMPLEMENTED.value);
			
			paramCount -= (hasThisParam ? 1 : 0);
			
			Struct[] args = designatorStatement.getCallPars().struct.getImplementedInterfaces()
					.toArray(new Struct[designatorStatement.getCallPars().struct.getImplementedInterfaces().size()]);
			
			if (paramCount != args.length) {
				report_error(String.format(
						"Number of arguments doesn't match the number of parameters for method '%s'", 
						designatorObj.getName()), designatorStatement);
			} 
			else {
				boolean valid = true;
				for (int i = 0; i < paramCount; i++) {
					if (!SymbolTableUtils.assignableTo(params[i + (hasThisParam ? 1 : 0)].getType(), args[i])) {
						valid = false;
						break;
					}
				}
				
				if (!valid) {
					report_error(String.format(
							"Argument types do not match the parameter types for method '%s'", 
							designatorObj.getName()), designatorStatement);
				}
			}			
		}
	}
	
	@Override
	public void visit(DesignatorStatement_AssignSetop designatorStatement) {
		if (!designatorStatement.getDesignator1().obj.getType().equals(SymbolTableUtils.setType) ||
				!designatorStatement.getDesignator2().obj.getType().equals(SymbolTableUtils.setType)) {
			report_error("Both operands of the set operator must be of set type", designatorStatement);
		}
		else if (!designatorStatement.getDesignator().obj.getType().equals(SymbolTableUtils.setType)) {
			report_error("Result of the set operator can only be assigned to a varible of set type", designatorStatement);
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
		Obj statementObj = statement.getDesignator().obj;
		
		if (statementObj.getKind() != Obj.Var && statementObj.getKind() != Obj.Elem &&
				statementObj.getKind() != Obj.Fld) {
			report_error(String.format("Attemp to read into a non variable '%s'", statementObj.getName()), statement);
		}
		else if (!statementObj.getType().equals(Tab.intType) && !statementObj.getType().equals(Tab.charType)
				&& !statementObj.getType().equals(SymbolTableUtils.boolType)) {
			report_error(String.format(
					"Attemp to read into a variable '%s' of a non-compatible type", statementObj.getName()), statement);
		}
	}
	
	@Override
	public void visit(Statement_PrintExprWithNum statement) {
		Struct exprStruct = statement.getExpr().struct;
		
		if (!exprStruct.equals(Tab.intType) && !exprStruct.equals(Tab.charType) &&
				!exprStruct.equals(SymbolTableUtils.boolType) && !exprStruct.equals(SymbolTableUtils.setType)) {
			report_error("Attemp to print a variable of a non-compatible type", statement);
		}
	}
	
	@Override
	public void visit(Statement_PrintExpr statement) {
		Struct exprStruct = statement.getExpr().struct;
		
		if (!exprStruct.equals(Tab.intType) && !exprStruct.equals(Tab.charType) &&
				!exprStruct.equals(SymbolTableUtils.boolType) && !exprStruct.equals(SymbolTableUtils.setType)) {
			report_error("Attemp to print a variable of a non-compatible type", statement);
		}
	}
	
	@Override
	public void visit(Statement_ReturnExpr statement) {
		if (currentMethod != null && !currentMethod.getType().equals(statement.getExpr().struct)) {
			report_error(String.format(
					"Type of return value doesn't match the return type of method '%s'", currentMethod.getName()), statement);
		}
		else {
			methodReturned = true;
		}
	}
	
	@Override
	public void visit(Statement_ReturnVoid statement) {
		if (currentMethod != null && !currentMethod.getType().equals(Tab.noType)) {
			report_error(String.format("Non-void method '%s' has to return a value", currentMethod.getName()), statement);
		}
		else {
			methodReturned = true;
		}
	}
}
