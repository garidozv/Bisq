package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

public class TabUtils {
	
	// Enum is used for sets
	public static final Struct setType = new Struct(Struct.Enum);	
	public static final Struct boolType = new Struct(Struct.Bool);
	
	public static enum MethodTypes {
		GLOBAL(-2), 
		LOCAL(-3), 
		LOCAL_UNIMPLEMENTED(-4);
		
		public final int value;
		
		private MethodTypes(int val) {
			value = val;
		}
	}
	
	public static void init() {
		Tab.init();
		
		Tab.find("ord").setFpPos(MethodTypes.GLOBAL.value);
		Tab.find("chr").setFpPos(MethodTypes.GLOBAL.value);
		Tab.find("len").setFpPos(MethodTypes.GLOBAL.value);
		
		// Add missing types to universe scope
		var boolTypeObj = Tab.insert(Obj.Type, "bool", TabUtils.boolType);
		boolTypeObj.setAdr(-1);
		boolTypeObj.setLevel(-1);
		
		setType.setElementType(Tab.intType);
		var setTypeObj = Tab.insert(Obj.Type, "set", TabUtils.setType);
		setTypeObj.setAdr(-1);
		setTypeObj.setLevel(-1);
		
		// Add pre-defined methods to universe scope
		insertAddMethodSymbol();
		insertAddAllMethodSymbol();
	}
	
	public static Obj createDummyObj(int kind, int adr, boolean global) {
		var obj = new Obj(kind, null, Tab.noType);
		obj.setAdr(adr);
		obj.setLevel(global ? 0 : 1);
		
		return obj;
	}
	
	public static boolean assignableTo(Struct dest, Struct src) {
		if (src.assignableTo(dest)) return true;
		
		if (dest.getKind() == Struct.Interface && src.getKind() == Struct.Class &&
				src.getImplementedInterfaces().contains(dest)) {
			return true;
		}
		
		if (dest.getKind() == Struct.Class && src.getKind() == Struct.Class) {
			var baseType = src.getElemType();
			while (baseType != null) {
				if (baseType == dest) return true;
				baseType = baseType.getElemType();
			}
		}
		
		return false;
	}
	
	private static void insertAddMethodSymbol() {
		var methodObj = Tab.insert(Obj.Meth, "add", Tab.noType);
		Tab.openScope();
		
		Tab.insert(Obj.Var, "a", TabUtils.setType)
			.setLevel(1);

		Tab.insert(Obj.Var, "b", Tab.intType)
			.setLevel(1);
		
		Tab.insert(Obj.Var, "i", Tab.intType)
			.setLevel(1);
		
		methodObj.setAdr(0);
		methodObj.setLevel(2);
		methodObj.setFpPos(MethodTypes.GLOBAL.value);
		Tab.chainLocalSymbols(methodObj);
		Tab.closeScope();
	}
	
	private static void insertAddAllMethodSymbol() {
		var methodObj = Tab.insert(Obj.Meth, "addAll", Tab.noType);
		Tab.openScope();
		
		Tab.insert(Obj.Var, "a", TabUtils.setType)
			.setLevel(1);

		Tab.insert(Obj.Var, "b", new Struct(Struct.Array, Tab.intType))
			.setLevel(1);
		
		Tab.insert(Obj.Var, "i", Tab.intType)
			.setLevel(1);
		
		methodObj.setAdr(0);
		methodObj.setLevel(2);
		methodObj.setFpPos(MethodTypes.GLOBAL.value);
		Tab.chainLocalSymbols(methodObj);
		Tab.closeScope();
	}
}
