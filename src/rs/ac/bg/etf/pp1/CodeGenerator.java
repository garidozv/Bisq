package rs.ac.bg.etf.pp1;

import java.awt.BufferCapabilities.FlipContents;
import java.beans.MethodDescriptor;
import java.io.Console;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import java_cup.internal_error;
import rs.ac.bg.etf.pp1.ast.Type;
import rs.ac.bg.etf.pp1.SymbolTableUtils.MethodTypes;
import rs.ac.bg.etf.pp1.ast.Addop_Add;
import rs.ac.bg.etf.pp1.ast.Addop_Sub;
import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.CondFact_Expr;
import rs.ac.bg.etf.pp1.ast.CondFact_RelopExpr;
import rs.ac.bg.etf.pp1.ast.CondTerm;
import rs.ac.bg.etf.pp1.ast.CondTermList;
import rs.ac.bg.etf.pp1.ast.Condition;
import rs.ac.bg.etf.pp1.ast.ConditionList;
import rs.ac.bg.etf.pp1.ast.ConditionList_CondTerm;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignExpr;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignSetop;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Call;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Dec;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Inc;
import rs.ac.bg.etf.pp1.ast.Designator_ArrayAccess;
import rs.ac.bg.etf.pp1.ast.Designator_Ident;
import rs.ac.bg.etf.pp1.ast.Designator_MemberAccess;
import rs.ac.bg.etf.pp1.ast.DoToken;
import rs.ac.bg.etf.pp1.ast.ElseToken;
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
import rs.ac.bg.etf.pp1.ast.MethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodName;
import rs.ac.bg.etf.pp1.ast.Mulop_Div;
import rs.ac.bg.etf.pp1.ast.Mulop_Mod;
import rs.ac.bg.etf.pp1.ast.Mulop_Mul;
import rs.ac.bg.etf.pp1.ast.ProgramDeclList;
import rs.ac.bg.etf.pp1.ast.ProgramDeclarations;
import rs.ac.bg.etf.pp1.ast.Relop;
import rs.ac.bg.etf.pp1.ast.Relop_Equal;
import rs.ac.bg.etf.pp1.ast.Relop_Greater;
import rs.ac.bg.etf.pp1.ast.Relop_GreaterOrEqual;
import rs.ac.bg.etf.pp1.ast.Relop_Lesser;
import rs.ac.bg.etf.pp1.ast.Relop_LesserOrEqual;
import rs.ac.bg.etf.pp1.ast.Relop_NotEqual;
import rs.ac.bg.etf.pp1.ast.Statement_Break;
import rs.ac.bg.etf.pp1.ast.Statement_Continue;
import rs.ac.bg.etf.pp1.ast.Statement_DoWhileCondition;
import rs.ac.bg.etf.pp1.ast.Statement_DoWhileConditionWithDesignatorStatement;
import rs.ac.bg.etf.pp1.ast.Statement_DoWhileTrue;
import rs.ac.bg.etf.pp1.ast.Statement_If;
import rs.ac.bg.etf.pp1.ast.Statement_IfElse;
import rs.ac.bg.etf.pp1.ast.Statement_PrintExpr;
import rs.ac.bg.etf.pp1.ast.Statement_PrintExprWithNum;
import rs.ac.bg.etf.pp1.ast.Statement_Read;
import rs.ac.bg.etf.pp1.ast.Statement_ReturnExpr;
import rs.ac.bg.etf.pp1.ast.Statement_ReturnVoid;
import rs.ac.bg.etf.pp1.ast.Term_Factor;
import rs.ac.bg.etf.pp1.ast.Term_MulopFactor;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.ast.WhileToken;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	
	private final static int VarSize = 4;
	
	private int mainJumpAddr;
	private int dataSize;
	private int startPc;
	
	private List<Obj> classTypes = new ArrayList<Obj>();
	private HashMap<Struct, Integer> virtualTableAddressMap = new HashMap<Struct, Integer>();
	private Obj currentClassDesignator = null;
	
	// Fields used for conditions and conditional statements
	private LinkedList<Integer> condTermTerminationJumps = new LinkedList<Integer>();
	private LinkedList<Integer> thenBlockJumps = new LinkedList<Integer>();
	private Stack<LinkedList<Integer>> continueJumpsStack = new Stack<LinkedList<Integer>>();
	private Stack<LinkedList<Integer>> breakJumpsStack = new Stack<LinkedList<Integer>>();
	private Stack<Integer> elseJumpStack = new Stack<Integer>();
	private Stack<Integer> doAddrStack = new Stack<Integer>();
	
	private void generatePreDefinedMethods() {
		// chr(n)
		Obj chrObj = Tab.find("chr");
		chrObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);
		
		// ord(c)
		Obj ordObj = Tab.find("ord");
		chrObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.exit);
		Code.put(Code.return_);
		
		// len(arr)
		Obj lenObj = Tab.find("len");
		chrObj.setAdr(Code.pc);
		Code.put(Code.enter);
		Code.put(1);
		Code.put(1);
		Code.put(Code.load_n);
		Code.put(Code.arraylength);
		Code.put(Code.exit);
		Code.put(Code.return_);
		
		// TODO: set methods
	}
	
	public CodeGenerator() {
		generatePreDefinedMethods();
	}
	
	public int getStartPc() {
		return startPc;
	}

	public int getDataSize() {
		return dataSize;
	}

	public void setDataSize(int dataSize) {
		this.dataSize = dataSize;
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
		// Parameters are already on expression stack
		Obj methodObj = factor.getDesignator().obj;
		if (methodObj.getFpPos() == MethodTypes.GLOBAL.value) {
			// Global method
			int offset = methodObj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		}
		else {
			// Class method
			Struct classType = methodObj.getLocalSymbols().stream().findFirst().get().getType();
			if (currentClassDesignator == null) {
				Code.put(Code.load_n + 0);
			}
			else {
				Code.load(currentClassDesignator);
			}
			Code.put(Code.getfield);
			Code.put2(0);
			Code.put(Code.invokevirtual);
			for (char c : methodObj.getName().toCharArray()) {
				Code.put4(c);
			}
			Code.put4(-1);
		}
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
		Code.put2(factor.getType().struct.getNumberOfFields() * VarSize);
		
		// Set virtual table pointer
		Code.put(Code.dup);
		Code.loadConst(virtualTableAddressMap.get(factor.getType().struct));
		Code.put(Code.putfield);
		Code.put2(0);
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
	
	@Override
	public void visit(Designator_Ident designator) {
		if (designator.getI1().equals("this") || designator.obj.getKind() == Obj.Fld ||
				designator.obj.getKind() == Obj.Meth && designator.obj.getFpPos() != MethodTypes.GLOBAL.value) {
			// Get 'this' argument
			currentClassDesignator = null;
			Code.put(Code.load_n + 0);
		}
		else {
			currentClassDesignator = designator.obj;
		}
		
		if (designator.obj.getType().getKind() == Struct.Array && 
			!(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
			Code.load(designator.obj);
		}
	}

	@Override
	public void visit(Designator_MemberAccess designator) {
		if (!designator.getDesignator().obj.getName().equals("this")) {
			// If field or class method, load it (object addr is already loaded)
			Code.load(designator.getDesignator().obj);
		}

		if (designator.obj.getType().getKind() == Struct.Array &&
			!(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
			Code.load(designator.obj);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_Call designatorStatement) {
		// Parameters are already on expression stack
		Obj methodObj = designatorStatement.getDesignator().obj;
		if (methodObj.getFpPos() == MethodTypes.GLOBAL.value) {
			// Global method
			int offset = methodObj.getAdr() - Code.pc;
			Code.put(Code.call);
			Code.put2(offset);
		}
		else {
			// Class method
			Struct classType = methodObj.getLocalSymbols().stream().findFirst().get().getType();
			Code.loadConst(virtualTableAddressMap.get(classType));
			Code.put(Code.invokevirtual);
			for (char c : methodObj.getName().toCharArray()) {
				Code.put4(c);
			}
			Code.put4(-1);
		}
		
		
		// If method returns a value it's never used, so we have to clean the expression stack
		if (!methodObj.getType().equals(Tab.noType)) {
			Code.put(Code.pop);
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
	
	// TODO: Add support for sets to print methods
	
	@Override
	public void visit(Statement_PrintExpr statement) {
		Code.loadConst(0);
		if (statement.getExpr().struct.equals(Tab.charType)) {
			Code.put(Code.bprint);
		}
		else {
			Code.put(Code.print);
		}
	}
	
	@Override
	public void visit(Statement_PrintExprWithNum statement) {
		Code.loadConst(statement.getN2());
		if (statement.getExpr().struct.equals(Tab.charType)) {
			Code.put(Code.bprint);
		}
		else {
			Code.put(Code.print);
		}
	}
	
	@Override
	public void visit(Statement_Read statement) {
		if (statement.getDesignator().obj.getType().equals(Tab.charType)) {
			Code.put(Code.bread);
		}
		else {
			Code.put(Code.read);
		}
		Code.store(statement.getDesignator().obj);
	}
	
	@Override
	public void visit(MethodName methodName) {
		methodName.obj.setAdr(Code.pc);
		
		if (methodName.obj.getName().equalsIgnoreCase("main") &&
				methodName.obj.getType().equals(Tab.noType) && methodName.obj.getLevel() == 0) {
			// Main method - patch the jump to main method
			Code.fixup(mainJumpAddr + 1);
		}
		
		Code.put(Code.enter);
		Code.put(methodName.obj.getLevel());
		Code.put(methodName.obj.getLocalSymbols().size());
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(Statement_ReturnVoid statement) {
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(Statement_ReturnExpr statement) {
		// Return value is already on expression stack
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	@Override
	public void visit(ClassDecl_Derived classDecl) {
		classTypes.add(classDecl.obj);
	}
	
	@Override
	public void visit(ClassDecl_NonDerived classDecl) {
		classTypes.add(classDecl.obj);
	}
	
	@Override
	public void visit(ProgramDeclarations programDeclarations) {
		// Initialize virtual tables
		startPc = Code.pc;
		
		for (Obj classType : classTypes) {
			generateVirtualTable(classType);
		}
		/*
		 * After initializing virtual tables, we have to jump to the main method
		 * Since we don't know the address of main method at this point
		 * we will add a jmp instruction, which we will patch later on
		 */
		mainJumpAddr = Code.pc; 
		Code.put(Code.jmp);
		Code.pc += 2; // 2 bytes for missing argument
	}
	
	private void addToStaticMemory(int value) {
		Code.loadConst(value);
		Code.put(Code.putstatic);
		Code.put2(dataSize++);
	}
	
	private void generateVirtualTable(Obj classObj) {
		List<Obj> methods = classObj.getType().getMembers()
				.stream().filter(obj -> obj.getKind() == Obj.Meth).toList();
		
		if (methods.size() == 0) return;
		
		// Set start address of the virtual table
		virtualTableAddressMap.put(classObj.getType(), dataSize);
		
		for (Obj method : methods) {
			for (char c : method.getName().toCharArray()) {
				addToStaticMemory(c);
			}
			addToStaticMemory(-1);
			addToStaticMemory(method.getAdr());
		}
		
		// End of virtual table marker
		addToStaticMemory(-2);
	}
	
	// Conditions
	
	/*
	 * Condition consists of CondTerms, and if one of them is true, the whole condition is true
	 * We will 'terminate' CondTerm if we come across a CondFact that is not true, and each CondTerm 
	 * will end with an unconditional jump to the 'then' block, because, if we reach the end of the CondTerm
	 * that means that the CondTerm is true, hence the whole Condition is true
	 * When we say 'terminate' a CondTerm, it means unconditionally jumping on to the next CondTerm if there is one,
	 * and if not, jumping to the end of the Condition
	 * The Condition will end with unconditional jump to the 'else' block, because, if we reach the end, that
	 * means that none of the CondTerms in the Condition were true, and that the else block should be executed
	 * Else block will end with an unconditional jump to the end of the statement
	 * If there is no else block, we will just treat the statements end as the else block
	 * 
	 * When we reach the end of the CondTerm, we will add a false jump to 'then' block, and then patch all of
	 * the 'termination' jumps in the CondTerm itself
	 * The address of the false jump will be remembered, so that it can be patched later on, when we reach the
	 * 'then' block
	 * 
	 * Example:
	 * 
	 * Cond: X && Y || (1) Z || (2) P || (3) Q && R (4)
	 * 
	 * if Cond then ... else ... end
	 * 
	 * 			jnX 1
	 * 			jnY 1
	 * 			jmp then
	 * 1:		jnZ 2
	 * 			jmp then
	 * 2:		jnP 3
	 * 			jmp then
	 * 3:		jnQ 4
	 * 			jnR 4
	 * 			jmp then
	 * 4:		jmp else
	 * then:	...
	 * 			...
	 * 			jmp end
	 * else:	...
	 * 			...
	 * end:		...
	 * 
	 */
	
	private static int getRelop(Relop relop) {
		if (relop instanceof Relop_Equal) {
			return Code.eq;
		}
		else if (relop instanceof Relop_NotEqual) {
			return Code.ne;
		}
		else if (relop instanceof Relop_Greater) {
			return Code.gt;
		}
		else if (relop instanceof Relop_GreaterOrEqual) {
			return Code.ge;
		}
		else if (relop instanceof Relop_Lesser) {
			return Code.lt;
		}
		else if (relop instanceof Relop_LesserOrEqual) {
			return Code.le;
		}
		else {
			return -1;
		}
	}
	
	@Override
	public void visit(CondFact_Expr condFact) {
		Code.loadConst(0);
		Code.putFalseJump(Code.ne, 0);
		condTermTerminationJumps.add(Code.pc - 2);
	}
	
	@Override
	public void visit(CondFact_RelopExpr condFact) {
		Code.putFalseJump(getRelop(condFact.getRelop()), 0);
		condTermTerminationJumps.add(Code.pc - 2);
	}
	
	@Override
	public void visit(CondTerm condTerm) {
		Code.putJump(0);
		thenBlockJumps.add(Code.pc - 2);
		
		while (condTermTerminationJumps.size() > 0) {
			Code.fixup(condTermTerminationJumps.remove());
		}
	}
		
	@Override
	public void visit(Condition condition) {
		Code.putJump(0);
		elseJumpStack.push(Code.pc - 2);
		
		while (thenBlockJumps.size() > 0) {
			Code.fixup(thenBlockJumps.remove());
		}
	}
	
	@Override
	public void visit(ElseToken elseToken) {
		Code.putJump(0);
		Code.fixup(elseJumpStack.pop());
		elseJumpStack.push(Code.pc - 2);
	}
	
	@Override
	public void visit(Statement_If statement) {
		Code.fixup(elseJumpStack.pop());
	}
	
	@Override
	public void visit(Statement_IfElse statement) {
		Code.fixup(elseJumpStack.pop());
	}
	
	@Override
	public void visit(DoToken doToken) {
		doAddrStack.push(Code.pc);
		continueJumpsStack.push(new LinkedList<Integer>());
		breakJumpsStack.push(new LinkedList<Integer>());
	}
	
	@Override
	public void visit(WhileToken whileToken) {
		LinkedList<Integer> continueJumps = continueJumpsStack.pop();
		while (continueJumps.size() > 0) {
			Code.fixup(continueJumps.remove());
		}
	}
	
	@Override
	public void visit(Statement_DoWhileTrue statement) {
		Code.putJump(doAddrStack.pop());
		LinkedList<Integer> breakJumps = breakJumpsStack.pop();
		while (breakJumps.size() > 0) {
			Code.fixup(breakJumps.remove());
		}
	}
	
	@Override
	public void visit(Statement_DoWhileCondition statement) {
		Code.putJump(doAddrStack.pop());
		Code.fixup(elseJumpStack.pop());
		LinkedList<Integer> breakJumps = breakJumpsStack.pop();
		while (breakJumps.size() > 0) {
			Code.fixup(breakJumps.remove());
		}
	}
	
	@Override
	public void visit(Statement_DoWhileConditionWithDesignatorStatement statement) {
		Code.putJump(doAddrStack.pop());
		Code.fixup(elseJumpStack.pop());
		LinkedList<Integer> breakJumps = breakJumpsStack.pop();
		while (breakJumps.size() > 0) {
			Code.fixup(breakJumps.remove());
		}
	}
	
	@Override
	public void visit(Statement_Continue statement) {
		Code.putJump(0);
		continueJumpsStack.peek().add(Code.pc - 2);
	}
	
	@Override
	public void visit(Statement_Break statement) {
		Code.putJump(0);
		breakJumpsStack.peek().add(Code.pc - 2);
	}
}
