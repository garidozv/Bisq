package rs.ac.bg.etf.pp1;

import java_cup.internal_error;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.structure.HashTableDataStructure;

public final class SymbolTableUtils {
	
	
	
	public enum MethodTypes {
		REGULAR(-2), // Global method
		CLASS_REGULAR(-3), // Regular class method
		INTERFACE_REGULAR(-4), // Interface method with body
		INTERFACE_NOT_IMPLEMENTED(-5), // Interface method without body
		CLASS_INHERITED(-6), // Class method inherited from another class
		INTERFACE_INHERITED(-7), // Class method inherited from interface
		OVERRIDDEN(-8); // Overridden class method
		
		public final int value;
		
		private MethodTypes(int val) {
			value = val;
		}
	}
	
	// Enum is used for sets
	public static final Struct setType = new Struct(Struct.Enum);	
	public static final Struct boolType = new Struct(Struct.Bool);
	
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
		
		// Does not support multilevel inheritance (we only check inherited class of src)
		if (dest.getKind() == Struct.Class && src.getKind() == Struct.Class &&
				src.getElemType() != null && src.getElemType() == dest) {
			return true;
		}
		
		return false;
	}
	
	// TODO: Add support for interface and set types
	public static String getTypeName(Struct type) {
		StringBuilder strBuilder = new StringBuilder(); 
		
		if (type == null) {
			return "Invalid type";
		}
		
		switch (type.getKind()) {
		case Struct.None:
			return "notype";
		case Struct.Int:
			return "int";
		case Struct.Char:
			return "char";
		case Struct.Bool:
			return "bool";
		case Struct.Array:
			strBuilder.append("Arr of ");
			
			switch (type.getElemType().getKind()) {
			case Struct.None:
				strBuilder.append("notype");
				break;
			case Struct.Int:
				strBuilder.append("int");
				break;
			case Struct.Char:
				strBuilder.append("char");
				break;
			case Struct.Class:
				strBuilder.append("Class");
				break;
			}
			
			return strBuilder.toString();
		case Struct.Class:
			strBuilder.append("Class [ ");
			for (Obj obj : type.getMembers()) {
				strBuilder.append(getTypeName(obj.getType()))
					.append(" ");
			}
			strBuilder.append("]");
			
			return strBuilder.toString();
		default:
			return "Unknown type";
		}
	}
}
