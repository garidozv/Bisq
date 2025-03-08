package rs.ac.bg.etf.pp1;

import javax.print.attribute.standard.JobKOctets;
import javax.swing.plaf.synth.SynthProgressBarUI;

import org.apache.log4j.Logger;

import java_cup.internal_error;
import rs.ac.bg.etf.pp1.SymbolTableUtils.ClassMethodTypes;
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
					tempObj.getFpPos() == ClassMethodTypes.REGULAR.value) {
				report_error(String.format("Multiple definitions of the name '%s'", methodName.getI1()), methodName);
			} 
			else if (tempObj.getFpPos() == ClassMethodTypes.CLASS_INHERITED.value) {
				// Method overriding
				
				// TODO: This way, return type cannot be overridden because you cannot set Obj's Type
				currentMethod = tempObj;
				currentMethod.setFpPos(ClassMethodTypes.REGULAR.value);
				Tab.openScope();
				Tab.insert(Obj.Var, "this", currentClass.getType());
			}
			// TODO: Handle interface methods
		}
		else {
			currentMethod = Tab.insert(Obj.Meth, methodName.getI1(), currentType);
			currentMethod.setFpPos(ClassMethodTypes.REGULAR.value);
			Tab.openScope();
			
			if (currentClass != null) {
				// Class method
				Tab.insert(Obj.Var, "this", currentClass.getType());
			}
		}
	}
	
	@Override
	public void visit(MethodReturnType_Void returnType) {
		currentType = Tab.noType;
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		paramCount = 0;
		if (currentMethod == null) return;
		
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
		// TODO: Add support for interfaces
		if (currentType.getKind() != Struct.Class) {
			report_error("Attemp to extends a non-class type", extendedClassName);
		} 
		else {
			currentClass.getType().setElementType(currentType);
			
			// Add extend class fields
			for (Obj member: currentType.getMembers()) {
				if (member.getKind() == Obj.Meth) {
					continue;
				}
				
				Tab.insert(Obj.Fld, member.getName(), member.getType());
			}
		}
	}
	
	@Override
	public void visit(ClassDecl_Derived classDecl) {
		if (currentClass == null) return;
		
		// TODO: Check if all interface methods were defined
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
				methodObj.setFpPos(ClassMethodTypes.CLASS_INHERITED.value);
				
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
	}
}
