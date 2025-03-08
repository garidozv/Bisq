package rs.ac.bg.etf.pp1;

import java_cup.internal_error;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

public final class SymbolTableUtils {
	
	public enum MethodTypes {
		REGULAR(-2),
		INHERITED(-3),
		NOT_IMPLEMENTED(-4);
		
		public final int value;
		
		private MethodTypes(int val) {
			value = val;
		}
	}
	
	public static final Struct boolType = new Struct(Struct.Bool);
	
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
