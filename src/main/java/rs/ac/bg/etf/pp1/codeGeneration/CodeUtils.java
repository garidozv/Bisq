package rs.ac.bg.etf.pp1.codeGeneration;

import rs.etf.pp1.mj.runtime.Code;

public final class CodeUtils {

    public static void putCall(int methodAddr) {
        var offset = methodAddr - Code.pc;

        Code.put(Code.call);
        Code.put2(offset);
    }

    public static int putMethodEnter(int paramCnt, int localCnt) {
        var methodAddr = Code.pc;

        Code.put(Code.enter);
        Code.put(paramCnt);
        Code.put(localCnt);

        return methodAddr;
    }

    public static void putMethodExit() {
        Code.put(Code.exit);
        Code.put(Code.return_);
    }

    public static void putConditionalJumpRelative(int op, int offset) {
        Code.put(Code.jcc + op);
        Code.put2(offset);
    }

    public static void putJumpRelative(int offset) {
        Code.put(Code.jmp);
        Code.put2(offset);
    }

    public static void putOpConst(int op, int n) {
        Code.loadConst(n);
        Code.put(op);
    }
}
