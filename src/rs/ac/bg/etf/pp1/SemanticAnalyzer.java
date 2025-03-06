package rs.ac.bg.etf.pp1;

import javax.print.attribute.standard.JobKOctets;
import javax.swing.plaf.synth.SynthProgressBarUI;

import org.apache.log4j.Logger;

import java_cup.internal_error;
import rs.ac.bg.etf.pp1.ast.ConstAssign;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Bool;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Char;
import rs.ac.bg.etf.pp1.ast.ConstAssign_Num;
import rs.ac.bg.etf.pp1.ast.ConstDecl;
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
import rs.ac.bg.etf.pp1.ast.VarName;
import rs.ac.bg.etf.pp1.ast.VarName_Array;
import rs.ac.bg.etf.pp1.ast.VarName_NonArray;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.ac.bg.etf.pp1.StructUtils;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	private Obj programObj = null;
	private Struct currentType = null;
	private Obj currentMethod;
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
					StructUtils.getTypeName(type), StructUtils.getTypeName(currentType)), node);
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
		constAssign(constAssignBool.getI1(), StructUtils.boolType, constAssignBool.getB2() ? 1 : 0, constAssignBool);
	}
	
	@Override
	public void visit(ConstAssign_Char constAssignChar) {
		constAssign(constAssignChar.getI1(), Tab.charType, (int)constAssignChar.getC2(), constAssignChar);
	}
	
	// Variable declarations
	
	@Override
	public void visit(VarName_NonArray varName) {
		// Maybe universe scope should be checked as well?
		Obj tempObj = Tab.currentScope.findSymbol(varName.getI1());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", varName.getI1()), varName);
		} 
		
		tempObj = Tab.insert(Obj.Var, varName.getI1(), currentType);
	}
	
	@Override
	public void visit(VarName_Array varName) {
		// Maybe universe scope should be checked as well?
		Obj tempObj = Tab.currentScope.findSymbol(varName.getI1());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", varName.getI1()), varName);
		} 
		
		Struct arrayType = new Struct(Struct.Array, currentType);
		tempObj = Tab.insert(Obj.Var, varName.getI1(), arrayType);
	}
	
	// Method declarations

	@Override
	public void visit(MethodName methodName) {
		Obj tempObj = Tab.currentScope.findSymbol(methodName.getI1());
		
		if (tempObj != null) {
			report_error(String.format("Multiple definitions of the name '%s'", methodName.getI1()), methodName);
		}
		else {
			currentMethod = Tab.insert(Obj.Meth, methodName.getI1(), currentType);
			Tab.openScope();
		}
	}
	
	@Override
	public void visit(MethodReturnType_Void returnType) {
		currentType = Tab.noType;
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		// TODO: Do not use Tab.noType for invalid types, so that the 'main not defined' error
		// can be reported even if the main return type is invalid (right now it won't do that)
		if (currentMethod.getName().equalsIgnoreCase("main") &&
			currentMethod.getType() == Tab.noType && paramCount == 0) {
			hasMain = true;
		}
		
		currentMethod.setLevel(paramCount);
		Tab.chainLocalSymbols(currentMethod);
		Tab.closeScope();
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
}
