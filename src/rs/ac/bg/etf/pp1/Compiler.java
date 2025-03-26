package rs.ac.bg.etf.pp1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import java_cup.runtime.Symbol;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.util.Log4JUtils;

public class Compiler {

	static {
		DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}
	
	public static void main(String[] args) throws Exception {
		Logger log = Logger.getLogger(Compiler.class);
		Reader br = null;
		
		try {
			File sourceCode = new File(args[0]);
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
			
			TabUtils.init();
			
			// Semantic analysis
			SemanticAnalyzer semAnalyzer = new SemanticAnalyzer();
			programNode.traverseBottomUp(semAnalyzer);
			
			Tab.dump(new ExtendedDumpSymbolTableVisitor());
			System.out.println("===============================================================");
			
			
			if (!parser.errorDetected && semAnalyzer.passed()) {
				File objFile = new File("test/program.obj");
				if(objFile.exists()) objFile.delete();
				
				// Code generation
				CodeGenerator codeGenerator = new CodeGenerator();
				codeGenerator.setDataSize(semAnalyzer.getnVars());
				programNode.traverseBottomUp(codeGenerator);
				Code.dataSize = codeGenerator.getDataSize();
				Code.mainPc = codeGenerator.getStartPc();
				Code.write(new FileOutputStream(objFile));

				log.info("Program successfully generated!");
			} else {
				log.info("Program parsing was unsuccessful!");
			}
		} 
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}
	}
}
