package rs.ac.bg.etf.pp1;

import java_cup.runtime.Symbol;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.codeGeneration.CodeGenerationController;
import rs.ac.bg.etf.pp1.codeGeneration.CodeGenerator;
import rs.ac.bg.etf.pp1.symbolTable.ExtendedDumpSymbolTableVisitor;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;
import rs.ac.bg.etf.pp1.util.Log4JUtils;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;

import java.io.*;

public class Compiler {

    static {
        DOMConfigurator.configure(Log4JUtils.instance().findLoggerConfigFile());
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
    }

    static void main(String[] args) {
        try {
            compile(args);
        } catch (Exception e) {
            Logger.getLogger(Compiler.class).error(e.getMessage());
            System.exit(1);
        }
    }

    private static void compile(String[] args) throws Exception {
        Logger log = Logger.getLogger(Compiler.class);
        Reader br = null;

        try {
            if (args.length < 2) {
                throw new IllegalArgumentException("Usage: Compiler <source-file> <output-file>");
            }

            File sourceCode = new File(args[0]);
            if (!sourceCode.exists()) {
                throw new IllegalArgumentException("Source file not found: " + sourceCode.getAbsolutePath());
            }

            // Lexical analysis
            br = new BufferedReader(new FileReader(sourceCode));
            Yylex lexer = new Yylex(br);

            // Syntactic analysis
            MJParser parser = new MJParser(lexer);
            parser.loggingEnabled = false;
            Symbol rootNode = parser.parse();

            if (parser.errorDetected || rootNode == null || !(rootNode.value instanceof Program)) {
                throw new IllegalStateException("Program parsing was unsuccessful");
            }

            Program programNode = (Program) (rootNode.value);

            TabUtils.init();

            // Semantic analysis
            SemanticAnalyzer semAnalyzer = new SemanticAnalyzer();
            programNode.traverseBottomUp(semAnalyzer);

            Tab.dump(new ExtendedDumpSymbolTableVisitor());
            System.out.println("===============================================================");


            if (!parser.errorDetected && semAnalyzer.passed()) {
                File outputFile = new File(args[1]);
                if (outputFile.exists()) {
                    if (!outputFile.delete()) {
                        throw new IllegalStateException("Could not delete existing output file: " + outputFile.getAbsolutePath());
                    }
                }

                // Code generation
                var monomorphizationPlan = semAnalyzer.createMonomorphizationPlan();
                CodeGenerator codeGenerator = new CodeGenerator(monomorphizationPlan);
                codeGenerator.setDataSize(semAnalyzer.getNVars());
                new CodeGenerationController(codeGenerator, monomorphizationPlan).generate(programNode);
                Code.dataSize = codeGenerator.getDataSize();
                Code.mainPc = codeGenerator.getStartPc();
                Code.write(new FileOutputStream(outputFile));

                log.info("Program successfully generated!");
            } else {
                throw new IllegalStateException("Program semantic analysis was unsuccessful");
            }
        } finally {
            if (br != null) try {
                br.close();
            } catch (IOException e1) {
                log.error(e1.getMessage(), e1);
            }
        }
    }
}
