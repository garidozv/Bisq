package rs.ac.bg.etf.pp1;

import java.security.PublicKey;
import java.util.Collection;

import javax.print.attribute.standard.JobKOctets;
import javax.swing.plaf.synth.SynthProgressBarUI;

import org.apache.log4j.Logger;

import java_cup.internal_error;
import rs.ac.bg.etf.pp1.SymbolTableUtils.MethodTypes;
import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.ClassFields;
import rs.ac.bg.etf.pp1.ast.ClassName;
import rs.ac.bg.etf.pp1.ast.ConstAssign;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Bool;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Char;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Num;
import rs.ac.bg.etf.pp1.ast.ConstDecl;
import rs.ac.bg.etf.pp1.ast.ExtendedClassName;
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
import rs.ac.bg.etf.pp1.ast.SyntaxNode;
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

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private Obj programObj = null;
	private Struct currentType = null;
	private Obj currentMethod = null;
	private Obj currentClass = null;
	private Obj currentInterface = null;
	private int paramCount = 0;	
	
	boolean errorDetected = false;
	private boolean hasMain = false;
	
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
		Tab.chainLocalSymbols(programObj);
		Tab.closeScope();
		
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
	}
	
	// TODO: Modify declarations to work with class and interface types
	// TODO: Check for multilevel inheritance?
	
	// Constant declarations
	
	private void constAssign(String name, Struct type, int value, SyntaxNode node) {
		// Maybe universe scope should be checked as well?
		Obj tempObj = Tab.currentScope.findSymbol(name);
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", name), node);
		} 
		else if (!currentType.equals(type)) {
			report_error(String.format(
					"Assignment of incompatible types: '%s' to '%s'", 
					SymbolTableUtils.getTypeName(type), SymbolTableUtils.getTypeName(currentType)), node);
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
					tempObj.getFpPos() == MethodTypes.REGULAR.value) {
				report_error(String.format("Multiple definitions of the name '%s'", methodName.getI1()), methodName);
			} 
			else if (tempObj.getFpPos() == MethodTypes.INHERITED.value) {
				// Method overriding
				
				// TODO: This way, return type cannot be overridden because you cannot set Obj's Type
				currentMethod = tempObj;
				currentMethod.setFpPos(MethodTypes.REGULAR.value);
				Tab.openScope();
				Obj thisParamObj = Tab.insert(Obj.Var, "this", currentClass.getType());
				thisParamObj.setFpPos(paramCount++);
			}
			// TODO: Handle interface methods
		}
		else {
			currentMethod = Tab.insert(Obj.Meth, methodName.getI1(), currentType);
			currentMethod.setFpPos(MethodTypes.REGULAR.value);
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
		
		currentMethod.setLevel(paramCount);
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		currentMethod = null;
		paramCount = 0;
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
		else {
			Struct arrayType = new Struct(Struct.Array, currentType);
			tempObj = Tab.insert(Obj.Var, formParVar.getI2(), arrayType);
			tempObj.setFpPos(paramCount++);
			
			if (currentInterface != null) tempObj.setAdr(tempObj.getAdr() + 1); 
		}
	}
	
	// Class declarations
	
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
		}
	}
	
	@Override
	public void visit(ExtendedClassName extendedClassName) {
		if (currentType == null || currentClass == null) return;
		
		if (currentType.getKind() == Struct.Class) {
			currentClass.getType().setElementType(currentType);
			
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
					report_error(String.format("Interface method %s must be implemented", member.getName()), classDecl);
				} 
				else if (methodObj.getLevel() != member.getLevel()) {
					report_error(String.format("Interface method %s' signature must not be changed", member.getName()), classDecl);
				}
				else {
					int cnt = member.getLevel() - 1;
					for (Obj param : member.getLocalSymbols()) {
						if (!methodObj.getLocalSymbols().contains(param)) {
							report_error(String.format("Interface method %s' signature must not be changed", member.getName()), classDecl);
							break;
						}
						
						if (--cnt == 0) break;
					}
				}
			}
		}
		
		currentClass.getType().setMembers(Tab.currentScope.getLocals());
		Tab.closeScope();
		currentClass = null;
	}
	
	@Override
	public void visit(ClassDecl_NonDerived classDecl) {
		if (currentClass == null) return;
		
		currentClass.getType().setMembers(Tab.currentScope.getLocals());
		Tab.closeScope();
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
				methodObj.setFpPos(MethodTypes.INHERITED.value);
				
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
				if (member.getFpPos() != MethodTypes.REGULAR.value) {
					continue;
				}
				
				// Create copy of method object
				Obj methodObj = Tab.insert(Obj.Meth, member.getName(), member.getType());
				methodObj.setLevel(member.getLevel()); 
				methodObj.setFpPos(MethodTypes.INHERITED.value);
				
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
	 * TODO: Check: Method locals are not part of the signature
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
		
		currentMethod.setFpPos(MethodTypes.NOT_IMPLEMENTED.value);
		currentMethod.setLevel(paramCount);
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
		currentMethod = null;
		paramCount = 0;
	}
	
}
