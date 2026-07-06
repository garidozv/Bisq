package rs.ac.bg.etf.pp1;

import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.DumpSymbolTableVisitor;

public class ExtendedDumpSymbolTableVisitor extends DumpSymbolTableVisitor {
	
	@Override
	public void visitStructNode(Struct structToVisit) {
		switch (structToVisit.getKind()) {
			case Struct.None:
				output.append("notype");
				break;
			case Struct.Int:
				output.append("int");
				break;
			case Struct.Char:
				output.append("char");
				break;
			case Struct.Bool:
				output.append("bool");
				break;
			case Struct.Array:
				output.append("Arr of ");
				
				switch (structToVisit.getElemType().getKind()) {
				case Struct.None:
					output.append("notype");
					break;
				case Struct.Int:
					output.append("int");
					break;
				case Struct.Char:
					output.append("char");
					break;
				case Struct.Class:
					output.append("Class");
					break;
				case Struct.Interface:
					output.append("Interface");
					break;
				}
				break;
			case Struct.Enum:
				output.append("Set");
				break;
			case Struct.Class:
				output.append("Class");
				printMembers(structToVisit);
				break;
			case Struct.Interface:
				output.append("Interface ");
				printMembers(structToVisit);
				break;
		}
	}
	
	@Override
	public void visitObjNode(Obj objToVisit) {
		switch (objToVisit.getKind()) {
			case Obj.Con:  output.append("Con "); break;
			case Obj.Var:  output.append("Var "); break;
			case Obj.Type: output.append("Type "); break;
			case Obj.Meth: output.append("Meth "); break;
			case Obj.Fld:  output.append("Fld "); break;
			case Obj.Prog: output.append("Prog "); break;
		}
		
		output.append(objToVisit.getName())
			.append(": ");
		
		if ((Obj.Var == objToVisit.getKind()) && "this".equalsIgnoreCase(objToVisit.getName())) {
			output.append("");
		}
		else if (objToVisit.getKind() != Obj.Type && objToVisit.getType().getKind() == Struct.Class) {
			// Member information will only be printed out for type definitions
			output.append("Class");
		}
		else if (objToVisit.getKind() != Obj.Type && objToVisit.getType().getKind() == Struct.Interface) {
			// Member information will only be printed out for type definitions
			output.append("Interface");
		}
		else {
			objToVisit.getType().accept(this);
		}	
		
		output.append(", ")
			.append(objToVisit.getAdr())
			.append(", ")
			.append(objToVisit.getLevel() + " ");
				
		if (objToVisit.getKind() == Obj.Prog || objToVisit.getKind() == Obj.Meth) {
			output.append("\n");
			nextIndentationLevel();
		}
		

		for (Obj o : objToVisit.getLocalSymbols()) {
			output.append(currentIndent.toString());
			o.accept(this);
			output.append("\n");
		}
		
		if (objToVisit.getKind() == Obj.Prog || objToVisit.getKind() == Obj.Meth) {
			previousIndentationLevel();
		}		
	}
	
	private void printMembers(Struct complexTypeStruct) {
		output.append(" [");
		currentIndent.append(indent);
		
		// Methods are printed with a new line at the end, so no additional new line is necessary
		var isPrevMethod = false;
		
		for (var obj : complexTypeStruct.getMembers()) {
			if (!isPrevMethod) output.append('\n');
			
			output.append(currentIndent.toString());
			obj.accept(this);
			
			if (obj.getKind() == Obj.Meth) isPrevMethod = true;
			else isPrevMethod = false;
		}
		
		output.append("]");
		previousIndentationLevel();
	}
}
