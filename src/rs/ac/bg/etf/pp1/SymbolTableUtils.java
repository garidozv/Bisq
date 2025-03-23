package rs.ac.bg.etf.pp1;

import java.lang.classfile.Signature.BaseTypeSig;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

public final class SymbolTableUtils {
	
	// Enum is used for sets
	public static final Struct setType = new Struct(Struct.Enum);	
	public static final Struct boolType = new Struct(Struct.Bool);
	
	public enum MethodTypes {
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
		
		Obj boolTypeObj = Tab.insert(Obj.Type, "bool", SymbolTableUtils.boolType);
		boolTypeObj.setAdr(-1);
		boolTypeObj.setLevel(-1);
		
		Obj setTypeObj = Tab.insert(Obj.Type, "set", SymbolTableUtils.setType);
		setTypeObj.setAdr(-1);
		setTypeObj.setLevel(-1);
		
		Obj addMethodObj = Tab.insert(Obj.Meth, "add", Tab.noType);
		HashTableDataStructure addMethodLocals = new HashTableDataStructure();
		Obj addFirstParam = new Obj(Obj.Var, "a", SymbolTableUtils.setType);
		addFirstParam.setAdr(0);
		addFirstParam.setLevel(1);
		addMethodLocals.insertKey(addFirstParam);
		Obj addSecondParam = new Obj(Obj.Var, "b", Tab.intType);
		addSecondParam.setAdr(1);
		addSecondParam.setLevel(1);
		addMethodLocals.insertKey(addSecondParam);
		addMethodObj.setLocals(addMethodLocals);
		addMethodObj.setAdr(0);
		addMethodObj.setLevel(2);
		
		Obj addAllMethodObj = Tab.insert(Obj.Meth, "addAll", Tab.noType);
		HashTableDataStructure addAllMethodLocals = new HashTableDataStructure();
		Obj addAllFirstParam = new Obj(Obj.Var, "a", SymbolTableUtils.setType);
		addAllFirstParam.setAdr(0);
		addAllFirstParam.setLevel(1);
		addAllMethodLocals.insertKey(addAllFirstParam);
		Obj addAllSecondParam = new Obj(Obj.Var, "b", new Struct(Struct.Array, Tab.intType));
		addAllSecondParam.setAdr(1);
		addAllSecondParam.setLevel(1);
		addAllMethodLocals.insertKey(addAllSecondParam);
		addAllMethodObj.setLocals(addAllMethodLocals);
		addAllMethodObj.setAdr(0);
		addAllMethodObj.setLevel(2);
	}
	
	public static boolean assignableTo(Struct dest, Struct src) {
		if (src.assignableTo(dest)) return true;
		
		if (dest.getKind() == Struct.Interface && src.getKind() == Struct.Class &&
				src.getImplementedInterfaces().contains(dest)) {
			return true;
		}
		
		if (dest.getKind() == Struct.Class && src.getKind() == Struct.Class) {
			Struct baseType = src.getElemType();
			while (baseType != null) {
				if (baseType == dest) return true;
				baseType = baseType.getElemType();
			}
		}
		
		return false;
	}
}
