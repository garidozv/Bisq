package rs.ac.bg.etf.pp1;

import java.io.ObjectOutput;

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
			output.append("Class ").append(structToVisit.getNumberOfFields()).append(" [");
			boolean isPrevMethod = false;
			currentIndent.append(indent);
			for (Obj obj : structToVisit.getMembers()) {
				if (!isPrevMethod) output.append('\n');
				output.append(currentIndent.toString());
				obj.accept(this);
				if (obj.getKind() == Obj.Meth) isPrevMethod = true;
				else isPrevMethod = false;
			}
			output.append("]");
			previousIndentationLevel();
			break;
		case Struct.Interface:
			output.append("Interface ").append(structToVisit.getNumberOfFields()).append(" [");
			currentIndent.append(indent);
			isPrevMethod = false;
			for (Obj obj : structToVisit.getMembers()) {
				if (!isPrevMethod) output.append('\n');
				output.append(currentIndent.toString());
				obj.accept(this);
				if (obj.getKind() == Obj.Meth) isPrevMethod = true;
				else isPrevMethod = false;
			}
			output.append("]");
			previousIndentationLevel();
			break;
		}
	}
}
