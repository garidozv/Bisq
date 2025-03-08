package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.rmi.server.LoaderHandler;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.ac.bg.etf.pp1.ast.Program;

public class Compiler {

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	
	public static void main(String[] args) throws Exception {
		
		Logger log = Logger.getLogger(Compiler.class);
		
		Reader br = null;
		try {
			File sourceCode = new File("test/program.mj");
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			// Lexical analysis
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			// Syntactic analysis
			MJParser parser = new MJParser(lexer);
			//parser.loggingEnabled = true;
	        Symbol rootNode = parser.parse();  

	        Program programNode = (Program)(rootNode.value); 
			
	        
			// Log syntax tree
			log.info(programNode.toString(""));
			log.info("===================================");
			
			
	        // Symbol table initialization
			Tab.init();
			
			Obj boolTypeObj = Tab.insert(Obj.Type, "bool", SymbolTableUtils.boolType);
			boolTypeObj.setAdr(-1);
			boolTypeObj.setLevel(-1);
			
			// TODO: Add rest of the missing types and methods
			
			// Semantic analysis
			SemanticAnalyzer semAnalyzer = new SemanticAnalyzer();
			programNode.traverseBottomUp(semAnalyzer);
			
			Tab.dump(new UpdatedDumpSymbolTableVisitor());
			log.info("===================================");
			
			
			if (!parser.errorDetected && semAnalyzer.passed()) {
				log.info("Program successfully parsed!");
			} else {
				log.info("Program parsing was unsuccessful!");
			}
		} 
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}

	}
	
	
}
