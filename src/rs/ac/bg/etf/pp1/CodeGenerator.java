package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import rs.ac.bg.etf.pp1.TabUtils.MethodTypes;
import rs.ac.bg.etf.pp1.ast.Addop_Add;
import rs.ac.bg.etf.pp1.ast.Addop_Sub;
import rs.ac.bg.etf.pp1.ast.ClassDecl_Derived;
import rs.ac.bg.etf.pp1.ast.ClassDecl_NonDerived;
import rs.ac.bg.etf.pp1.ast.CondFact_Expr;
import rs.ac.bg.etf.pp1.ast.CondFact_RelopExpr;
import rs.ac.bg.etf.pp1.ast.CondTerm;
import rs.ac.bg.etf.pp1.ast.Condition_Valid;
import rs.ac.bg.etf.pp1.ast.Designator;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignExpr;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_AssignSetop;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Call;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Dec;
import rs.ac.bg.etf.pp1.ast.DesignatorStatement_Inc;
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
import rs.ac.bg.etf.pp1.ast.Factor_NewArray;
import rs.ac.bg.etf.pp1.ast.Factor_NewObject;
import rs.ac.bg.etf.pp1.ast.Factor_NumConst;
import rs.ac.bg.etf.pp1.ast.MethodDecl;
import rs.ac.bg.etf.pp1.ast.MethodName;
import rs.ac.bg.etf.pp1.ast.Mulop_Div;
import rs.ac.bg.etf.pp1.ast.Mulop_Mod;
import rs.ac.bg.etf.pp1.ast.Mulop_Mul;
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
import rs.ac.bg.etf.pp1.ast.Term_MulopFactor;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.ac.bg.etf.pp1.ast.WhileToken;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	
	private final static int VarSize = 4;

	private static int setPrintMethodAddr;
	private static int unionMethodAddr;
	
	private int mainJumpAddr;
	private int dataSize;
	private int startPc;
	
	// Fields used for initialization of virtual tables
	private List<Obj> classTypes = new ArrayList<Obj>();
	private HashMap<Struct, Integer> virtualTableAddressMap = new HashMap<>();
	
	// Fields used for conditions and conditional statements
	private LinkedList<Integer> condTermSkipJumps = new LinkedList<>();
	private LinkedList<Integer> thenBlockJumps = new LinkedList<>();
	private Stack<LinkedList<Integer>> continueJumpsStack = new Stack<>();
	private Stack<LinkedList<Integer>> breakJumpsStack = new Stack<>();
	private Stack<Integer> elseJumpStack = new Stack<>();
	private Stack<Integer> doAddrStack = new Stack<>();
	
	public CodeGenerator() {
		generatePreDefinedMethods();
	}
	
	private static void generateChrMethod() {
		Obj chrObj = Tab.find("chr");
		chrObj.setAdr(CodeUtils.putMethodEnter(1, 1));
		Code.put(Code.load_n);
		CodeUtils.putMethodExit();
	}
	
	private static void generateOrdMethod() {
		Obj ordObj = Tab.find("ord");
		ordObj.setAdr(CodeUtils.putMethodEnter(1, 1));
		Code.put(Code.load_n);
		CodeUtils.putMethodExit();
	}
	
	private static void generateLenMethod() {
		Obj lenObj = Tab.find("len");
		lenObj.setAdr(CodeUtils.putMethodEnter(1, 1));
		Code.put(Code.load_n);
		Code.put(Code.arraylength);
		CodeUtils.putMethodExit();
	}
	
	/* 
	 * mJ code would look something like this:
	 * ---------------------------------------
	 * i = 1;
	 *
	 * if (s[0] == 0) return;
	 *
	 * do {
	 *    print(s[i]);
	 *    if (i == s[0]) break;
	 *    print(' ');
	 *    i++;
	 * } while();
	 * ---------------------------------------
	 */
	private static void generateSetPrintMethod() {
		var s = TabUtils.createDummyObj(Obj.Var, 0, false);
		var i = TabUtils.createDummyObj(Obj.Var, 1, false);

		setPrintMethodAddr = CodeUtils.putMethodEnter(1, 2);
		// Initialize 'i' to 1
		Code.loadConst(1);
		Code.store(i);
		// If set is empty return immediately
		Code.load(s);
		Code.loadConst(0);
		Code.put(Code.aload);
		Code.loadConst(0);
		CodeUtils.putConditionalJumpRelative(Code.ne, 5);
		CodeUtils.putMethodExit();
		// Load element to be printed 's[i]' (start of the loop)
		Code.load(s);
		Code.load(i);
		Code.put(Code.aload);
		// Load 0 as second print argument and print the element
		Code.put(Code.const_n);
		Code.put(Code.print);
		// Load condition values 'i' and 's[0]'
		Code.load(i);
		Code.load(s);
		Code.loadConst(0);
		Code.put(Code.aload);
		// If condition 'i == s[0]' is met jump to the end of the loop
		CodeUtils.putConditionalJumpRelative(Code.eq, 17);
		// Load value of ' ' and 0 as second argument and print it
		Code.loadConst((int)' ');
		Code.loadConst(0);
		Code.put(Code.bprint);
		// Increment 'i'
		Code.load(i);
		CodeUtils.putOpConst(Code.add, 1);
		Code.store(i);
		// Jump to the start of the loop
		CodeUtils.putJumpRelative(-23);
		// End of the loop
		CodeUtils.putMethodExit();
	}
	
	/* 
	 * mJ code would look something like this:
	 * ---------------------------------------
	 * i = 0;
	 *	
	 * do {
	 *    i++;
	 *    if (i > s[0]) break;
	 *    if (s[i] == num) return;
	 * } while ();
	 * 
	 * if (i < len(s)) {
	 *     s[i] = num;
	 *     s[0]++;
	 * }
	 * ---------------------------------------
	 */
	private static void generateAddMethod() {
		var s = TabUtils.createDummyObj(Obj.Var, 0, false);
		var num = TabUtils.createDummyObj(Obj.Var, 1, false);
		var i = TabUtils.createDummyObj(Obj.Var, 2, false);
		
		Obj addMethodObj = Tab.find("add");
		addMethodObj.setAdr(CodeUtils.putMethodEnter(2, 3));
		// Initalize 'i' to 0
		Code.loadConst(0);
		Code.store(i);
		// Increment 'i' (Start of the loop)
		Code.load(i);
		CodeUtils.putOpConst(Code.add, 1);
		Code.store(i);
		// Load condition values 'i' and 's[0]'
		Code.load(i);
		Code.load(s);
		Code.loadConst(0);
		Code.put(Code.aload);
		// If condition 'i > s[0]' is met jump to the end of the loop
		CodeUtils.putConditionalJumpRelative(Code.gt, 12);
		// Load condition values 's[i]' and 'num'
		Code.load(s);
		Code.load(i);
		Code.put(Code.aload);
		Code.load(num);
		// If condition 's[i] == num' is not met jump to the start of the loop
		CodeUtils.putConditionalJumpRelative(Code.ne, -15);
		// If condition is met, exit the method
		CodeUtils.putMethodExit();
		// Load condition values 'i' and 'len(s)' (end of the loop)
		Code.load(i);
		Code.load(s);
		Code.put(Code.arraylength);
		// If condition is not jump to the end of the statement
		CodeUtils.putConditionalJumpRelative(Code.ge, 14);
		// Add 'num' to set 's' at index 'i'
		Code.load(s);
		Code.load(i);
		Code.load(num);
		Code.put(Code.astore);
		// Increment set counter 's[0]'
		Code.load(s);
		Code.loadConst(0);
		Code.put(Code.dup2);
		Code.put(Code.aload);
		CodeUtils.putOpConst(Code.add, 1);
		Code.put(Code.astore);
		// End of the statement
		CodeUtils.putMethodExit();
	}
	
	/* 
	 * mJ code would look something like this:
	 * ---------------------------------------
	 * i = 0;
	 * 
	 * do {
	 *    if (i >= len(arr)) break;
	 *    add(s, arr[i]);
	 *    i++;
	 * } while ();
	 * ---------------------------------------
	 */
	private static void generateAddAllMethod() {
		var addMethodObj = Tab.find("add");
		var s = TabUtils.createDummyObj(Obj.Var, 0, false);
		var arr = TabUtils.createDummyObj(Obj.Var, 1, false);
		var i = TabUtils.createDummyObj(Obj.Var, 2, false);
		
		Obj addAllMethodObj = Tab.find("addAll");
		addAllMethodObj.setAdr(CodeUtils.putMethodEnter(2, 3));
		// Initialize 'i' to 0
		Code.loadConst(0);
		Code.store(i);
		// Load condition values 'i' and 'len(arr)' (start of the loop)
		Code.load(i);
		Code.load(arr);
		Code.put(Code.arraylength);
		// if condition 'i >= len(arr)' is met jump to end of the loop
		CodeUtils.putConditionalJumpRelative(Code.ge, 17);
		// Load method arguments 's' and 'arr[i]'
		Code.load(s);
		Code.load(arr);
		Code.load(i);
		Code.put(Code.aload);
		// Generate 'add' method call
		CodeUtils.putCall(addMethodObj.getAdr());
		// Increment 'i'
		Code.load(i);
		CodeUtils.putOpConst(Code.add, 1);
		Code.store(i);
		// Jump to the start of the loop
		CodeUtils.putJumpRelative(-17);
		// End of the loop
		CodeUtils.putMethodExit();
	}
	
	/* 
	 * mJ code would look something like this:
	 * ---------------------------------------
	 * s1[0] = 0;
	 * 
	 * i = 1;
	 * do {
	 *    add(s1, s2[i]);
	 * } while (i < s2[0], i++);
	 * 
	 * i = 1;
	 * do {
	 *    add(s1, s3[i]);
	 * } while (i < s3[0], i++);
	 * ---------------------------------------
	 */
	private static void generateUnionMethod() {
		var addMethodObj = Tab.find("add");
		var s1 = TabUtils.createDummyObj(Obj.Var, 0, false);
		var s2 = TabUtils.createDummyObj(Obj.Var, 1, false);
		var s3 = TabUtils.createDummyObj(Obj.Var, 2, false);
		var i = TabUtils.createDummyObj(Obj.Var, 3, false);
		
		unionMethodAddr = CodeUtils.putMethodEnter(3, 4);
		// Set 's1' element counter to 0
		Code.load(s1);
		Code.loadConst(0);
		Code.put(Code.dup);
		Code.put(Code.astore);
		// Initialize 'i' to 1
		Code.loadConst(1);
		Code.store(i);
		// Load method arguments 's1' and 's2[i]' (start of the loop)
		Code.load(s1);
		Code.load(s2);
		Code.load(i);
		Code.put(Code.aload);
		// Generate 'add' method call
		CodeUtils.putCall(addMethodObj.getAdr());
		// Load condition values 'i' and 's2[0]'
		Code.load(i);
		Code.load(s2);
		Code.loadConst(0);
		Code.put(Code.aload);
		// If condition 'i < s2[0]' is not met jump to end of the loop statement
		CodeUtils.putConditionalJumpRelative(Code.ge, 10);
		// Increment 'i' (loop statement)
		Code.load(i);
		CodeUtils.putOpConst(Code.add, 1);
		Code.store(i);
		// Jump to the start of the loop
		CodeUtils.putJumpRelative(-18);
		// Initialize 'i' to 1 (end of the loop statement)
		Code.loadConst(1);
		Code.store(i);
		// Load method arguments 's1' and 's3[i]' (start of the loop)
		Code.load(s1);
		Code.load(s3);
		Code.load(i);
		Code.put(Code.aload);
		// Generate 'add' method call
		CodeUtils.putCall(addMethodObj.getAdr());
		// Load condition values 'i' and 's3[0]'
		Code.load(i);
		Code.load(s3);
		Code.loadConst(0);
		Code.put(Code.aload);
		// If condition 'i < s3[0]' is not met jump to end of the loop statement
		CodeUtils.putConditionalJumpRelative(Code.ge, 10);
		// Increment 'i' (loop statement)
		Code.load(i);
		CodeUtils.putOpConst(Code.add, 1);
		Code.store(i);
		// Jump to the start of the loop
		CodeUtils.putJumpRelative(-18);
		// End of the loop statement
		CodeUtils.putMethodExit();
	}
	
	private static void generatePreDefinedMethods() {
		generateChrMethod();
		generateOrdMethod();
		generateLenMethod();
		generateSetPrintMethod();
		generateAddMethod();
		generateAddAllMethod();
		generateUnionMethod();
	}
	
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
		generateMethodCall(factor.getDesignator());
	}
	
	@Override
	public void visit(Factor_NewArray factor) {
		var typeStruct = factor.struct;
		
		if (typeStruct.equals(TabUtils.setType)) {
			/*
			 * If a set is being created allocate an array of n + 1 length
			 * The first element of the set array is used to store the current
			 * number of elements in the set
			 */
			Code.loadConst(1);
			Code.put(Code.add);
		}
		
		Code.put(Code.newarray);
		
		if (typeStruct.getElemType().equals(Tab.charType)) {
			Code.put(0);
		}
		else {
			Code.put(1);
		}
		
		if (typeStruct.equals(TabUtils.setType)) {
			// Initialize the set element count to 0
			Code.put(Code.dup);
			Code.loadConst(0);
			Code.loadConst(0);
			Code.put(Code.astore);
		}
	}
	
	@Override
	public void visit(Factor_NewObject factor) {
		var objectType = factor.getType().struct;
		
		Code.put(Code.new_);
		Code.put2(objectType.getNumberOfFields() * VarSize);
		
		// Set virtual table pointer address
		Code.put(Code.dup);
		Code.loadConst(virtualTableAddressMap.get(objectType));
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
	
	/*
	 * Generates the code for the 'map' operator
	 * Pre-generated method cannot be used, because method called can vary,
	 * and because method pointers are not supported
	 * 
	 * mJ code would look something like this:
	 * ---------------------------------------
	 * i = 0; sum = 0;
	 * 
	 * do {
	 *    sum = sum + func(arr[i]);
	 * } 
	 * while (i < len(arr) - 1, i++);
	 * ---------------------------------------
	 */
	
	@Override
	public void visit(Expr_Map expr) {
		
		var methodObj = expr.getDesignator().obj;
		// Create dummy objects for global temporary variables that are used
		var arr = TabUtils.createDummyObj(Obj.Var, 0, true);
		var i = TabUtils.createDummyObj(Obj.Var, 1, true);
		
		// Store array ('arr') address in first temporary variable
		Code.store(arr);
		
		if (methodObj.getFpPos() != MethodTypes.GLOBAL.value) {
			// Pop the object address generated for the method designator
			Code.put(Code.pop);
		}
		
		// Initialize the counter ('i') stored in second temporary variable to 0
		Code.loadConst(0);
		Code.store(i);
		// Initialize the sum variable that will be stored on expression stack to 0
		Code.loadConst(0);
		
		var loopStartAddr = Code.pc;
		
		if (methodObj.getFpPos() != MethodTypes.GLOBAL.value) {
			// Generate the object address for 'this' parameter
			expr.getDesignator().traverseBottomUp(this);
		}
		
		// Load the method argument 'arr[i]'
		Code.load(arr);
		Code.load(i);
		Code.put(Code.aload);
		// Generate method call code
		generateMethodCall(expr.getDesignator());
		// Add the return value to local sum
		Code.put(Code.add);
		// Load condition values 'i' and 'len(arr) - 1'
		Code.load(i);
		Code.load(arr);
		Code.put(Code.arraylength);
		CodeUtils.putOpConst(Code.sub, 1);
		// If condition 'i < len(arr) - 1' is met jump to loop statement
		CodeUtils.putConditionalJumpRelative(Code.lt, 6);
		// Condition not met, jump to the end of the loop
		CodeUtils.putJumpRelative(14);
		// Increment 'i' (loop statement)
		Code.load(i);
		CodeUtils.putOpConst(Code.add, 1);
		Code.store(i);
		// Jump to the start of the loop
		Code.putJump(loopStartAddr);
		// End of the loop
		// Sum is on the expression stack
	}
	
	@Override
	public void visit(Designator_Ident designator) {
		var designatorObj = designator.obj;
		
		if (designator.getI1().equals("this") || designatorObj.getKind() == Obj.Fld ||
				designatorObj.getKind() == Obj.Meth && designatorObj.getFpPos() != MethodTypes.GLOBAL.value) {
			Code.put(Code.load_n);
		}
		
		if (designatorObj.getType().getKind() == Struct.Array && 
			!(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
			Code.load(designatorObj);
		}
	}

	@Override
	public void visit(Designator_MemberAccess designator) {
		var objectDesignatorObj = designator.getDesignator().obj;
		var memberDesignatorObj = designator.obj;
		
		if (!objectDesignatorObj.getName().equals("this")) {
			// If field or class method, load it (object addr is already loaded)
			Code.load(objectDesignatorObj);
		}

		if (memberDesignatorObj.getType().getKind() == Struct.Array &&
			!(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
			Code.load(memberDesignatorObj);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_Call designatorStatement) {
		generateMethodCall(designatorStatement.getDesignator());
		
		// If method returns a value it's never used, so we have to clean the expression stack
		if (!designatorStatement.getDesignator().obj.getType().equals(Tab.noType)) {
			Code.put(Code.pop);
		}
	}
	
	@Override
	public void visit(DesignatorStatement_AssignExpr designatorStatement) {
		Code.store(designatorStatement.getDesignator().obj);
	}
	
	@Override
	public void visit(DesignatorStatement_Inc designatorStatement) {
		var designatorStatementObj = designatorStatement.getDesignator().obj;
		
		if (designatorStatementObj.getKind() == Obj.Fld) {
			Code.put(Code.dup);
		}
		else if (designatorStatementObj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
		}
		
		Code.load(designatorStatementObj);
		Code.loadConst(1);
		Code.put(Code.add);
		Code.store(designatorStatementObj);
	}
	
	@Override
	public void visit(DesignatorStatement_Dec designatorStatement) {
		var designatorStatementObj = designatorStatement.getDesignator().obj;
		
		if (designatorStatementObj.getKind() == Obj.Fld) {
			Code.put(Code.dup);
		}
		else if (designatorStatementObj.getKind() == Obj.Elem) {
			Code.put(Code.dup2);
		}
		
		Code.load(designatorStatementObj);
		Code.loadConst(1);
		Code.put(Code.sub);
		Code.store(designatorStatementObj);
	}
	
	@Override
	public void visit(DesignatorStatement_AssignSetop designatorStatement) {
		Code.load(designatorStatement.getDesignator().obj);
		Code.load(designatorStatement.getDesignator1().obj);
		Code.load(designatorStatement.getDesignator2().obj);
		
		CodeUtils.putCall(unionMethodAddr);
	}
	
	@Override
	public void visit(Statement_PrintExpr statement) {
		generatePrintStatement(statement.getExpr().struct, 0);
	}
	
	@Override
	public void visit(Statement_PrintExprWithNum statement) {
		generatePrintStatement(statement.getExpr().struct, statement.getN2());
	}
	
	@Override
	public void visit(Statement_Read statement) {
		var statementObj = statement.getDesignator().obj;
		
		if (statementObj.getType().equals(Tab.charType)) {
			Code.put(Code.bread);
		}
		else {
			Code.put(Code.read);
		}
		
		Code.store(statementObj);
	}
	
	@Override
	public void visit(MethodName methodName) {
		var methodNameObj = methodName.obj;
		
		methodNameObj.setAdr(Code.pc);
		
		if (methodNameObj.getName().equalsIgnoreCase("main") &&
				methodNameObj.getFpPos() == MethodTypes.GLOBAL.value &&
				methodNameObj.getType().equals(Tab.noType) && methodNameObj.getLevel() == 0) {
			// Main method - patch the jump to main method
			Code.fixup(mainJumpAddr + 1);
		}
		
		CodeUtils.putMethodEnter(methodNameObj.getLevel(), methodNameObj.getLocalSymbols().size());
	}
	
	@Override
	public void visit(MethodDecl methodDecl) {
		if (methodDecl.obj.getType().equals(Tab.noType)) {
			// Generate implicit return for void methods
			CodeUtils.putMethodExit();
		} else {
			// Generate run-time error if execution reaches the end of the method body without returning
			Code.put(Code.trap);
			Code.put(-1);
		}
	}
	
	@Override
	public void visit(Statement_ReturnVoid statement) {
		CodeUtils.putMethodExit();
	}
	
	@Override
	public void visit(Statement_ReturnExpr statement) {
		CodeUtils.putMethodExit();
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
		
		for (var classType : classTypes) {
			generateVirtualTable(classType);
		}
		
		/*
		 * After initializing virtual tables, we have to jump to the main method
		 * Since we don't know the address of main method at this point
		 * we will add a jmp instruction, which we will patch later on
		 */
		mainJumpAddr = Code.pc;
		Code.putJump(0);
	}
	
	// Conditions
	
	/*
	 * -Condition consists of CondTerms, and if one of them is true, the whole condition is true
	 * -We will 'skip' CondTerm if we come across a CondFact that is not true, and each 
	 * CondTerm will end with an unconditional jump to the 'then' block, because, if we reach the
	 * end of the CondTerm that means that the CondTerm is true, hence the whole Condition is true
	 * -When we say 'skip' a CondTerm, it means unconditionally jumping on to the next 
	 * CondTerm if there is one, and if not, jumping to the end of the Condition
	 * -The Condition will end with unconditional jump to the 'else' block, because, if we reach
	 * the end, that means that none of the CondTerms in the Condition were true, and that the 
	 * else block should be executed
	 * -Else block will end with an unconditional jump to the end of the statement
	 * -If there is no else block, we will just treat the statements end as the else block
	 * -When we reach the end of the CondTerm, we will add a false jump to 'then' block,
	 * and then patch all of the 'skip' jumps in the CondTerm itself
	 * -The address of the false jump will be remembered, so that it can be patched later on,
	 * when we reach the 'then' block
	 * 
	 * -This explanation describes the way if-then-else statements are handled in addition to
	 * Condition handling. Loops, would be handled similarly, the only difference being what
	 * 'then' and 'else' represent, and what addresses are known at the time Condition code
	 * is generated
	 * 
	 * -Example:
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
	 */
	
	@Override
	public void visit(CondFact_Expr condFact) {
		Code.loadConst(0);
		Code.putFalseJump(Code.ne, 0);
		
		condTermSkipJumps.add(Code.pc - 2);
	}
	
	@Override
	public void visit(CondFact_RelopExpr condFact) {
		Code.putFalseJump(getRelop(condFact.getRelop()), 0);
		
		condTermSkipJumps.add(Code.pc - 2);
	}
	
	@Override
	public void visit(CondTerm condTerm) {
		Code.putJump(0);
		
		thenBlockJumps.add(Code.pc - 2);
		
		while (condTermSkipJumps.size() > 0) {
			Code.fixup(condTermSkipJumps.remove());
		}
	}
		
	@Override
	public void visit(Condition_Valid condition) {
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
	
	private void generatePrintStatement(Struct objType, int width) {
		if (objType.equals(TabUtils.setType)) {
			// Call set print method
			CodeUtils.putCall(setPrintMethodAddr);
		}
		else {
			Code.loadConst(width);
			
			if (objType.equals(Tab.charType)) {
				Code.put(Code.bprint);
			}
			else {
				Code.put(Code.print);
			}
		}
	}
	
	private void generateMethodCall(Designator designator) {
		var methodObj = designator.obj;
		
		if (methodObj.getFpPos() == MethodTypes.GLOBAL.value) {
			// Global method
			CodeUtils.putCall(methodObj.getAdr());
		}
		else {
			// Member method
			/*
			 * Object address is buried on the expressions stack under the method arguments
			 * So there is no way to retrieve it other than generating the code to retrieve it again
			 */
			designator.traverseBottomUp(this);
			
			Code.put(Code.getfield);
			Code.put2(0);
			Code.put(Code.invokevirtual);
			for (char c : methodObj.getName().toCharArray()) {
				Code.put4(c);
			}
			Code.put4(-1);
		}
	}
	
	private void addToStaticMemory(int value) {
		Code.loadConst(value);
		Code.put(Code.putstatic);
		Code.put2(dataSize++);
	}
	
	private void generateVirtualTable(Obj classObj) {
		var methodObjArray = classObj.getType().getMembers().stream()
				.filter(obj -> obj.getKind() == Obj.Meth)
				.toArray(Obj[]::new);
		
		if (methodObjArray.length == 0) return;
		
		// Set start address of the virtual table
		virtualTableAddressMap.put(classObj.getType(), dataSize);
		
		for (var methodObj : methodObjArray) {
			for (char c : methodObj.getName().toCharArray()) {
				addToStaticMemory(c);
			}
			addToStaticMemory(-1);
			addToStaticMemory(methodObj .getAdr());
		}
		
		// End of virtual table marker
		addToStaticMemory(-2);
	}
}
