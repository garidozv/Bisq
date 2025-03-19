package rs.ac.bg.etf.pp1;

import java.nio.charset.CharacterCodingException;

import java_cup.internal_error;
import rs.ac.bg.etf.pp1.ast.Addop_Add;
import rs.ac.bg.etf.pp1.ast.Addop_Sub;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignExpr;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignSetop;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Dec;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Inc;
import rs.ac.bg.etf.pp1.ast.Designator_ArrayAccess;
import rs.ac.bg.etf.pp1.ast.Designator_Ident;
import rs.ac.bg.etf.pp1.ast.Designator_MemberAccess;
import rs.ac.bg.etf.pp1.ast.ExprList_AddopTerm;
import rs.ac.bg.etf.pp1.ast.ExprList_SubTerm;
import rs.ac.bg.etf.pp1.ast.Expr_Map;
import rs.ac.bg.etf.pp1.ast.Factor_BoolConst;
import rs.ac.bg.etf.pp1.ast.Factor_CharConst;
import rs.ac.bg.etf.pp1.ast.Factor_Designator;
import rs.ac.bg.etf.pp1.ast.Factor_DesignatorCall;
import rs.ac.bg.etf.pp1.ast.Factor_Expr;
import rs.ac.bg.etf.pp1.ast.Factor_NewArray;
import rs.ac.bg.etf.pp1.ast.Factor_NewObject;
import rs.ac.bg.etf.pp1.ast.Factor_NumConst;
import rs.ac.bg.etf.pp1.ast.Mulop_Div;
import rs.ac.bg.etf.pp1.ast.Mulop_Mod;
import rs.ac.bg.etf.pp1.ast.Mulop_Mul;
import rs.ac.bg.etf.pp1.ast.Term_Factor;
import rs.ac.bg.etf.pp1.ast.Term_MulopFactor;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {

	private int mainPc;
	
	public int getMainPc(){
		return mainPc;
	}
	
	
	@Override
	public void visit(Factor_NumConst factor) {
		Code.loadConst(factor.getN1());
	}
	
	@Override
	public void visit(Factor_CharConst factor) {
		Code.loadConst(factor.getC1());
	}
	
	@Override
	public void visit(Factor_BoolConst factor) {
		Code.loadConst(factor.getB1() ? 1 : 0);
	}
	
	@Override
	public void visit(Factor_Designator factor) {
		if (factor.getDesignator().obj.getType().getKind() != Struct.Array) {
			Code.load(factor.getDesignator().obj);
		}
	}
	
	@Override
	public void visit(Factor_DesignatorCall factor) {
		// TODO
	}
	
	@Override
	public void visit(Factor_NewArray factor) {
		// n is already on stack
		Code.put(Code.newarray);
		if (factor.getType().struct.equals(Tab.charType)) {
			Code.put(0);
		}
		else {
			Code.put(1);
		}
	}
	
	@Override
	public void visit(Factor_NewObject factor) {
		Code.put(Code.new_);
		Code.put2(factor.getType().struct.getNumberOfFields() * 4);
	}
	
	@Override
	public void visit(Term_MulopFactor term) {
		if (term.getMulop() instanceof Mulop_Mul) {
			Code.put(Code.mul);
		}
		else if (term.getMulop() instanceof Mulop_Div) {
			Code.put(Code.div);
		}
		else if (term.getMulop() instanceof Mulop_Mod) {
			Code.put(Code.rem);
		}
	}
	
	@Override
	public void visit(ExprList_SubTerm exprList) {
		Code.put(Code.neg);
	}
	
	@Override
	public void visit(ExprList_AddopTerm exprList) {
		if (exprList.getAddop() instanceof Addop_Add) {
			Code.put(Code.add);
		}
		else if (exprList.getAddop() instanceof Addop_Sub) {
			Code.put(Code.sub);
		}
	}
	
	@Override
	public void visit(Expr_Map expr) {
		// TODO
	}
	
	/*
	 * class A { int a[] }
	 * int b, c[]; A d, e[]; // locals (doesn't matter)
	 * 
	 * 1.	b	Factor_Designator -> Designator_Ident
	 * 		load b_addr
	 * 
	 * 2.	c[3]	Factor_Designator -> Designator_ArrayAccess -> Designator_Ident + Expr
	 * 		load c_addr
	 * 		const_3
	 * 		aload
	 * 
	 * 3.	d.a[2]	Factor_Designator -> Designator_ArrayAccess -> 
	 * 				{Designator_MemberAccess -> Designator_Ident + IDENT} + Expr
	 * 		load d_addr
	 * 		getfield a_addr
	 * 		const_2
	 * 		aload
	 * 
	 * 4.	e[3].a[1]	Factor_Designator -> Designator_ArrayAccess -> 
	 * 					{Designator_MemberAccess -> {Designator_ArrayAccess -> Designator_Ident + Expr} + IDENT} + Expr
	 * 		load e_addr
	 * 		const_3
	 * 		aload
	 * 		load a_addr
	 * 	 	const_1
	 * 		aload
	 */
	
	@Override
	public void visit(Designator_Ident designator) {
		if (designator.obj.getType().getKind() == Struct.Array && 
			!(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
			Code.load(designator.obj);
		}
	}

	@Override
	public void visit(Designator_MemberAccess designator) {
		Code.load(designator.getDesignator().obj);
		
		if (designator.obj.getType().getKind() == Struct.Array &&
			!(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
			Code.load(designator.obj);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_AssignExpr designatorStatement) {
		Code.store(designatorStatement.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_Inc designatorStatement) {
		if (designatorStatement.getDesignator().obj.getKind() == Obj.Fld) {
			Code.put(Code.dup);
		}
		else if (designatorStatement.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
		}
		
		Code.load(designatorStatement.getDesignator().obj);
		Code.loadConst(1);
		Code.put(Code.add);
		Code.store(designatorStatement.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_Dec designatorStatement) {
		if (designatorStatement.getDesignator().obj.getKind() == Obj.Fld) {
			Code.put(Code.dup);
		}
		else if (designatorStatement.getDesignator().obj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
		}
		
		Code.load(designatorStatement.getDesignator().obj);
		Code.loadConst(1);
		Code.put(Code.sub);
		Code.store(designatorStatement.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_AssignSetop designatorStatement) {
		// TODO
	}
}
