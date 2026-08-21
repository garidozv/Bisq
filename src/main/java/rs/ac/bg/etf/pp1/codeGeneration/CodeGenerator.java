package rs.ac.bg.etf.pp1.codeGeneration;

import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.codeGeneration.generics.GenericSpecialization;
import rs.ac.bg.etf.pp1.codeGeneration.generics.MonomorphizationPlan;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils.MethodTypes;
import rs.ac.bg.etf.pp1.symbolTable.generics.GenericTypeApplicationStruct;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.*;

import static rs.ac.bg.etf.pp1.symbolTable.TabUtils.THIS_VARIABLE_NAME;

public class CodeGenerator extends VisitorAdaptor {

    private final static int VarSize = 4;

    private static int setPrintMethodAddr;
    private static int unionMethodAddr;

    private int mainJumpAddr;
    private int dataSize;
    private int startPc;

    // Fields used for initialization of virtual tables
    private final List<Obj> classTypes = new ArrayList<>();
    private final HashMap<Struct, Integer> virtualTableAddressMap = new HashMap<>();

    // Fields used for conditions and conditional statements
    private final LinkedList<Integer> condTermSkipJumps = new LinkedList<>();
    private final LinkedList<Integer> thenBlockJumps = new LinkedList<>();
    private final Stack<LinkedList<Integer>> continueJumpsStack = new Stack<>();
    private final Stack<LinkedList<Integer>> breakJumpsStack = new Stack<>();
    private final Stack<Integer> elseJumpStack = new Stack<>();
    private final Stack<Integer> doAddrStack = new Stack<>();
    private final Stack<Integer> forAddrStack = new Stack<>();
    private int forConditionStartAddr = 0;

    private final MonomorphizationPlan monomorphizationPlan;
    private GenericSpecialization<?> currentSpecialization;

    public CodeGenerator(MonomorphizationPlan monomorphizationPlan) {
        this.monomorphizationPlan = monomorphizationPlan;
        generatePreDefinedMethods();
    }

    /**
     * Generates the code for the given method body and method object inside the specified specialization.
     *
     * @param specialization The specialization context used to resolve generic types and symbols while generating the method.
     */
    public void generateMethod(StatementList body, Obj method, GenericSpecialization<?> specialization) {
        currentSpecialization = specialization;
        try {
            beginMethod(method);
            body.traverseBottomUp(this);
            endMethod(method);
        } finally {
            currentSpecialization = null;
        }
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
        // If set is empty, return immediately
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
        // If condition 'i == s[0]' is met, jump to the end of the loop
        CodeUtils.putConditionalJumpRelative(Code.eq, 17);
        // Load value of ' ' and 0 as second argument and print it
        Code.loadConst((int) ' ');
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
        // Initialize 'i' to 0
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
        // If condition 'i > s[0]' is met, jump to the end of the loop
        CodeUtils.putConditionalJumpRelative(Code.gt, 12);
        // Load condition values 's[i]' and 'num'
        Code.load(s);
        Code.load(i);
        Code.put(Code.aload);
        Code.load(num);
        // If condition 's[i] == num' is not met, jump to the start of the loop
        CodeUtils.putConditionalJumpRelative(Code.ne, -15);
        // If condition is met, exit the method
        CodeUtils.putMethodExit();
        // Load condition values 'i' and 'len(s)' (end of the loop)
        Code.load(i);
        Code.load(s);
        Code.put(Code.arraylength);
        // If condition is not, jump to the end of the statement
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
        // if condition 'i >= len(arr)' is met, jump to end of the loop
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
        return switch (relop) {
            case Relop_Equal _ -> Code.eq;
            case Relop_NotEqual _ -> Code.ne;
            case Relop_Greater _ -> Code.gt;
            case Relop_GreaterOrEqual _ -> Code.ge;
            case Relop_Lesser _ -> Code.lt;
            case Relop_LesserOrEqual _ -> Code.le;
            case null, default -> -1;
        };
    }

    private static Designator getCallableDesignator(CallableRef callableRef) {
        if (callableRef instanceof CallableRef_Plain plain) return plain.getDesignator();
        return ((CallableRef_Applied) callableRef).getDesignator();
    }

    private static int calculateVirtualTableSize(Obj classObj) {
        var methodEntriesSize = classObj.getType().getMembers().stream()
                .filter(obj -> obj.getKind() == Obj.Meth)
                .mapToInt(method -> method.getName().length() + 2)
                .sum();
        return methodEntriesSize == 0 ? 0 : methodEntriesSize + 1;
    }

    /**
     * Registers a concrete class and reserves its virtual table range in static memory.
     * This must be called for every concrete class before any class definition is generated.
     */
    void registerClass(Obj classType) {
        classTypes.add(classType);
        virtualTableAddressMap.put(classType.getType(), dataSize);
        dataSize += calculateVirtualTableSize(classType);
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
        var designatorObj = resolveObject(factor.getDesignator().obj);
        if (designatorObj.getType().getKind() != Struct.Array) {
            Code.load(designatorObj);
        }
    }

    @Override
    public void visit(Factor_DesignatorCall factor) {
        generateMethodCall(factor.getCallableRef());
    }

    @Override
    public void visit(Factor_NewArray factor) {
        var typeStruct = resolveType(factor.struct);

        if (typeStruct.equals(TabUtils.setType)) {
            /*
             * If a set is being created, allocate an array of n + 1 length
             * The first element of the set array is used to store the current
             * number of elements in the set
             */
            Code.loadConst(1);
            Code.put(Code.add);
        }

        Code.put(Code.newarray);

        if (typeStruct.getElemType().equals(Tab.charType)) {
            Code.put(0);
        } else {
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
        var objectType = factor.struct instanceof GenericTypeApplicationStruct
                ? monomorphizationPlan.getTargetSpecialization(factor, currentSpecialization).getGeneratedObject().getType()
                : resolveType(factor.getType().struct);

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
        } else if (term.getMulop() instanceof Mulop_Div) {
            Code.put(Code.div);
        } else if (term.getMulop() instanceof Mulop_Mod) {
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
        } else if (exprList.getAddop() instanceof Addop_Sub) {
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
    public void visit(ExprNonTern_Map expr) {
        var methodDesignator = getCallableDesignator(expr.getCallableRef());
        var methodObj = resolveCallable(expr.getCallableRef());
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
            methodDesignator.traverseBottomUp(this);
        }

        // Load the method argument 'arr[i]'
        Code.load(arr);
        Code.load(i);
        Code.put(Code.aload);
        // Generate method call code
        generateMethodCall(expr.getCallableRef());
        // Add the return value to local sum
        Code.put(Code.add);
        // Load condition values 'i' and 'len(arr) - 1'
        Code.load(i);
        Code.load(arr);
        Code.put(Code.arraylength);
        CodeUtils.putOpConst(Code.sub, 1);
        // If condition 'i < len(arr) - 1' is met, jump to loop statement
        CodeUtils.putConditionalJumpRelative(Code.lt, 6);
        // Condition isn't met, jump to the end of the loop
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
        var designatorObj = designator.obj.getKind() == Obj.Meth
                ? designator.obj
                : resolveObject(designator.obj);

        if (designator.getI1().equals(THIS_VARIABLE_NAME) || designatorObj.getKind() == Obj.Fld ||
                designatorObj.getKind() == Obj.Meth && designatorObj.getFpPos() != MethodTypes.GLOBAL.value) {
            Code.put(Code.load_n);
        }

        if (designatorObj.getKind() != Obj.Meth && designatorObj.getType().getKind() == Struct.Array &&
                !(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
            Code.load(designatorObj);
        }
    }

    @Override
    public void visit(Designator_MemberAccess designator) {
        var objectDesignatorObj = resolveObject(designator.getDesignator().obj);

        if (!objectDesignatorObj.getName().equals(THIS_VARIABLE_NAME)) {
            // If field or class method, load it (object addr is already loaded)
            Code.load(objectDesignatorObj);
        }

        if (designator.obj.getKind() != Obj.Meth) {
            var memberDesignatorObj = resolveObject(designator.obj);
            if (memberDesignatorObj.getType().getKind() == Struct.Array &&
                    !(designator.getParent() instanceof DesignatorStatement_AssignExpr)) {
                Code.load(memberDesignatorObj);
            }
        }
    }

    @Override
    public void visit(DesignatorStatement_Call designatorStatement) {
        var methodObj = resolveCallable(designatorStatement.getCallableRef());
        generateMethodCall(designatorStatement.getCallableRef());

        // If method returns a value, it's never used, so we have to clean the expression stack
        if (!methodObj.getType().equals(Tab.noType)) {
            Code.put(Code.pop);
        }
    }

    @Override
    public void visit(DesignatorStatement_AssignExpr designatorStatement) {
        Code.store(resolveObject(designatorStatement.getDesignator().obj));
    }

    @Override
    public void visit(DesignatorStatement_Inc designatorStatement) {
        generateUnitUpdate(designatorStatement.getDesignator().obj, Code.add);
    }

    @Override
    public void visit(DesignatorStatement_Dec designatorStatement) {
        generateUnitUpdate(designatorStatement.getDesignator().obj, Code.sub);
    }

    @Override
    public void visit(DesignatorStatement_AssignSetop designatorStatement) {
        Code.load(resolveObject(designatorStatement.getDesignator().obj));
        Code.load(resolveObject(designatorStatement.getDesignator1().obj));
        Code.load(resolveObject(designatorStatement.getDesignator2().obj));

        CodeUtils.putCall(unionMethodAddr);
    }

    @Override
    public void visit(Statement_PrintExpr statement) {
        generatePrintStatement(resolveType(statement.getExpr().struct), 0);
    }

    @Override
    public void visit(Statement_PrintExprWithNum statement) {
        generatePrintStatement(resolveType(statement.getExpr().struct), statement.getN2());
    }

    @Override
    public void visit(Statement_Read statement) {
        var statementObj = resolveObject(statement.getDesignator().obj);

        if (statementObj.getType().equals(Tab.charType)) {
            Code.put(Code.bread);
        } else {
            Code.put(Code.read);
        }

        Code.store(statementObj);
    }

    @Override
    public void visit(MethodName methodName) {
        beginMethod(resolveObject(methodName.obj));
    }

    private void beginMethod(Obj methodObj) {
        methodObj.setAdr(Code.pc);

        if (methodObj.getName().equalsIgnoreCase("main") &&
                methodObj.getFpPos() == MethodTypes.GLOBAL.value &&
                methodObj.getType().equals(Tab.noType) && methodObj.getLevel() == 0) {
            // Main method - patch the jump to main method
            Code.fixup(mainJumpAddr + 1);
        }

        CodeUtils.putMethodEnter(methodObj.getLevel(), methodObj.getLocalSymbols().size());
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        endMethod(resolveObject(methodDecl.obj));
    }

    private void endMethod(Obj methodObj) {
        if (methodObj.getType().equals(Tab.noType)) {
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

    public void emitProgramInitialization() {
        // Initialize virtual tables
        startPc = Code.pc;

        for (var classType : classTypes) {
            populateVirtualTable(classType);
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
     * - Condition consists of CondTerms, and if one of them is true, the whole condition is true
     * - We will 'skip' CondTerm if we come across a CondFact that is not true, and each
     * CondTerm will end with an unconditional jump to the 'then' block, because, if we reach the
     * end of the CondTerm, that means that the CondTerm is true, hence the whole Condition is true
     * - When we say 'skip' a CondTerm, it means unconditionally jumping on to the next
     * CondTerm if there is one, and if not, jumping to the end of the Condition
     * - The Condition will end with an unconditional jump to the 'else' block, because, if we reach
     * the end, that means that none of the CondTerms in the Condition were true, and that the
     * else block should be executed
     * - Then the block will end with an unconditional jump to the end of the statement
     * - If there is no else block, we will just treat the statement's end as the else block
     * - When we reach the end of the CondTerm, we will add a false jump to 'then' block,
     * and then patch all the 'skip' jumps in the CondTerm itself
     * - The address of the false jump will be remembered so that it can be patched later on
     * when we reach the 'then' block
     *
     * -This explanation describes the way if-then-else statements are handled in addition to
     * Condition handling. Loops would be handled similarly, the only difference being what
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

        while (!condTermSkipJumps.isEmpty()) {
            Code.fixup(condTermSkipJumps.remove());
        }
    }

    @Override
    public void visit(Condition_Valid condition) {
        Code.putJump(0);

        elseJumpStack.push(Code.pc - 2);

        while (!thenBlockJumps.isEmpty()) {
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
    public void visit(ExprTern expr) {
        Code.fixup(elseJumpStack.pop());
    }

    @Override
    public void visit(ColToken qMark) {
        Code.putJump(0);
        Code.fixup(elseJumpStack.pop());
        elseJumpStack.push(Code.pc - 2);
    }

    @Override
    public void visit(DoToken doToken) {
        doAddrStack.push(Code.pc);

        continueJumpsStack.push(new LinkedList<>());

        breakJumpsStack.push(new LinkedList<>());
    }

    @Override
    public void visit(WhileToken whileToken) {
        LinkedList<Integer> continueJumps = continueJumpsStack.pop();
        while (!continueJumps.isEmpty()) {
            Code.fixup(continueJumps.remove());
        }
    }

    @Override
    public void visit(Statement_DoWhileTrue statement) {
        Code.putJump(doAddrStack.pop());

        LinkedList<Integer> breakJumps = breakJumpsStack.pop();
        while (!breakJumps.isEmpty()) {
            Code.fixup(breakJumps.remove());
        }
    }

    @Override
    public void visit(Statement_DoWhileCondition statement) {
        Code.putJump(doAddrStack.pop());
        Code.fixup(elseJumpStack.pop());

        LinkedList<Integer> breakJumps = breakJumpsStack.pop();
        while (!breakJumps.isEmpty()) {
            Code.fixup(breakJumps.remove());
        }
    }

    @Override
    public void visit(Statement_DoWhileConditionWithDesignatorStatement statement) {
        Code.putJump(doAddrStack.pop());
        Code.fixup(elseJumpStack.pop());

        LinkedList<Integer> breakJumps = breakJumpsStack.pop();
        while (!breakJumps.isEmpty()) {
            Code.fixup(breakJumps.remove());
        }
    }

    @Override
    public void visit(ForInitializer_DesignatorStatement initializer) {
        initializeForLoop();
    }

    @Override
    public void visit(ForInitializer_Epsilon initializer) {
        initializeForLoop();
    }

    @Override
    public void visit(ForPostStatement_DesignatorStatement statement) {
        handleForLoopPostStatement();
    }

    @Override
    public void visit(ForPostStatement_Epsilon statement) {
        handleForLoopPostStatement();
    }

    @Override
    public void visit(ForCondition_Condition statement) {
        handleForLoopCondition();
    }

    @Override
    public void visit(ForCondition_Epsilon statement) {
        elseJumpStack.push(-1);
        handleForLoopCondition();
    }

    @Override
    public void visit(Statement_For statement) {
        LinkedList<Integer> continueJumps = continueJumpsStack.pop();
        while (!continueJumps.isEmpty()) {
            Code.fixup(continueJumps.remove());
        }

        Code.putJump(forAddrStack.pop());

        var elseAddr = elseJumpStack.pop();
        if (elseAddr != -1) {
            Code.fixup(elseAddr);
        }

        LinkedList<Integer> breakJumps = breakJumpsStack.pop();
        while (!breakJumps.isEmpty()) {
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

    private Struct resolveType(Struct type) {
        return currentSpecialization == null ? type : currentSpecialization.resolveType(type);
    }

    private Obj resolveObject(Obj object) {
        return currentSpecialization == null ? object : currentSpecialization.resolveObject(object);
    }

    private Obj resolveCallable(CallableRef callableRef) {
        if (callableRef instanceof CallableRef_Applied applied)
            return monomorphizationPlan.getTargetSpecialization(applied, currentSpecialization).getGeneratedObject();
        return resolveObject(callableRef.obj);
    }

    private void generateUnitUpdate(Obj designatorObj, int operation) {
        var designatorStatementObj = resolveObject(designatorObj);

        if (designatorStatementObj.getKind() == Obj.Fld) {
            Code.put(Code.dup);
        } else if (designatorStatementObj.getKind() == Obj.Elem) {
            Code.put(Code.dup2);
        }

        Code.load(designatorStatementObj);
        Code.loadConst(1);
        Code.put(operation);
        Code.store(designatorStatementObj);
    }

    private void generatePrintStatement(Struct objType, int width) {
        if (objType.equals(TabUtils.setType)) {
            // Call set print method
            CodeUtils.putCall(setPrintMethodAddr);
        } else {
            Code.loadConst(width);

            if (objType.equals(Tab.charType)) {
                Code.put(Code.bprint);
            } else {
                Code.put(Code.print);
            }
        }
    }

    private void generateMethodCall(CallableRef callableRef) {
        var designator = getCallableDesignator(callableRef);
        var methodObj = resolveCallable(callableRef);

        if (methodObj.getFpPos() == MethodTypes.GLOBAL.value) {
            // Global method
            CodeUtils.putCall(methodObj.getAdr());
        } else {
            // Member method
            /*
             * Object address is buried on the expression stack under the method arguments,
             * So there is no way to retrieve it other than generating the code to retrieve it again
             */
            designator.traverseBottomUp(this);

            Code.put(Code.getfield);
            Code.put2(0);
            Code.put(Code.invokevirtual);
            for (var c : methodObj.getName().toCharArray()) {
                Code.put4(c);
            }
            Code.put4(-1);
        }
    }

    private void putInStaticMemory(int value, int address) {
        Code.loadConst(value);
        Code.put(Code.putstatic);
        Code.put2(address);
    }

    private void populateVirtualTable(Obj classObj) {
        var methodObjArray = classObj.getType().getMembers().stream()
                .filter(obj -> obj.getKind() == Obj.Meth)
                .toArray(Obj[]::new);
        if (methodObjArray.length == 0) return;

        var address = virtualTableAddressMap.get(classObj.getType());

        for (var methodObj : methodObjArray) {
            for (char c : methodObj.getName().toCharArray()) {
                putInStaticMemory(c, address++);
            }
            putInStaticMemory(-1, address++);
            putInStaticMemory(methodObj.getAdr(), address++);
        }

        // End of virtual table marker
        putInStaticMemory(-2, address);
    }

    private void handleForLoopPostStatement() {
        Code.putJump(forConditionStartAddr);
        Code.fixup(forAddrStack.peek() - 2);
    }

    private void handleForLoopCondition() {
        Code.putJump(0);
        forAddrStack.push(Code.pc);
    }

    private void initializeForLoop() {
        forConditionStartAddr = Code.pc;
        continueJumpsStack.push(new LinkedList<>());
        breakJumpsStack.push(new LinkedList<>());
    }
}
