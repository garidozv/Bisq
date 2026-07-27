package rs.ac.bg.etf.pp1;

import java.io.StringReader;

import rs.ac.bg.etf.pp1.ast.Program;
import rs.ac.bg.etf.pp1.symbolTable.TabUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

abstract class CompilerTestBase {

    protected static AnalysisResult analyzeProgram(String source) throws Exception {
        TabUtils.init();
        var parser = new MJParser(new Yylex(new StringReader(source)));
        parser.loggingEnabled = false;
        var parsed = parser.parse();
        var program = assertInstanceOf(Program.class, parsed.value);
        assertFalse(parser.errorDetected);

        var analyzer = new SemanticAnalyzer();
        program.traverseBottomUp(analyzer);
        return new AnalysisResult(program, analyzer);
    }

    protected record AnalysisResult(Program program, SemanticAnalyzer analyzer) {}
}