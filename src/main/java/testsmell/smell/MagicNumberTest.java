package testsmell.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import testsmell.AbstractSmell;
import testsmell.TestMethod;
import testsmell.Util;
import thresholds.Thresholds;

import java.io.FileNotFoundException;

public class MagicNumberTest extends AbstractSmell {

    public MagicNumberTest(Thresholds thresholds) {
        super(thresholds);
    }

    /**
     * Checks of 'MagicNumberTest' smell
     */
    @Override
    public String getSmellName() {
        return "Magic Number Test";
    }

    /**
     * Analyze the test file for test methods that have magic numbers in as parameters in the assert methods
     */
    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit, CompilationUnit productionFileCompilationUnit, String testFileName, String productionFileName) throws FileNotFoundException {
        MagicNumberTest.ClassVisitor classVisitor;
        classVisitor = new MagicNumberTest.ClassVisitor();
        classVisitor.visit(testFileCompilationUnit, null);
    }

    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private MethodDeclaration currentMethod = null;
        private MagicNumberTest magicNumberTest;
        TestMethod testMethod;
        private int magicCount = 0;

        // examine all methods in the test class
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            if (Util.isValidTestMethod(n)) {
                currentMethod = n;
                testMethod = new TestMethod(n.getNameAsString());
                testMethod.setSmell(false); //default value is false (i.e. no smell)
                super.visit(n, arg);
                testMethod.setSmell(magicCount >= thresholds.getMagicNumberTest());
                if(testMethod.isSmelly()){
                    putSmellyElement(n.getName().toString());
                    addScore(magicCount);
                }
                testMethod.addDataItem("MagicNumberCount", String.valueOf(magicCount));
                smellyElementsSet.add(testMethod);
                //reset values for next method
                currentMethod = null;
                magicCount = 0;
            }
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (currentMethod != null && isAssertMethod(n.getNameAsString())) {
                for (Expression argument : n.getArguments()) {
                    countMagicNumbers(argument);
                }
            }
        }

        private boolean isAssertMethod(String name) {
            return name.startsWith("assertArrayEquals") ||
                    name.startsWith("assertEquals") ||
                    name.startsWith("assertNotSame") ||
                    name.startsWith("assertSame") ||
                    name.startsWith("assertThat") ||
                    name.equals("assertNotNull") ||
                    name.equals("assertNull") ||
                    name.equals("assertTrue") ||
                    name.equals("assertFalse");
        }


        private void countMagicNumbers(Expression expr) {
            if (expr == null) return;
            expr = unwrap(expr);
            if (isNumericLiteral(expr)) {
                magicCount++;
                return;
            }
            if (expr.isUnaryExpr()) {
                countMagicNumbers(expr.asUnaryExpr().getExpression()); // es: -0
            } else if (expr.isBinaryExpr()) {
                countMagicNumbers(expr.asBinaryExpr().getLeft());
                countMagicNumbers(expr.asBinaryExpr().getRight());
            } else if (expr.isMethodCallExpr()) {
                MethodCallExpr m = expr.asMethodCallExpr();
                m.getScope().ifPresent(this::countMagicNumbers);
                for (Expression arg : m.getArguments()) countMagicNumbers(arg);
            } else if (expr.isObjectCreationExpr()) {
                for (Expression arg : expr.asObjectCreationExpr().getArguments()) countMagicNumbers(arg);
            } else if (expr.isConditionalExpr()) { // es: cond ? 0 : foo()
                ConditionalExpr c = expr.asConditionalExpr();
                countMagicNumbers(c.getCondition());
                countMagicNumbers(c.getThenExpr());
                countMagicNumbers(c.getElseExpr());
            } else if (expr.isArrayInitializerExpr()) {
                expr.asArrayInitializerExpr().getValues().forEach(this::countMagicNumbers);
            }
        }

        private Expression unwrap(Expression e) {
            while (true) {
                if (e.isEnclosedExpr()) {
                    e = e.asEnclosedExpr().getInner();
                } else if (e.isCastExpr()) {
                    e = e.asCastExpr().getExpression();
                } else {
                    return e;
                }
            }
        }

        private boolean isNumericLiteral(Expression e) {
            // JavaParser separa: IntegerLiteralExpr, LongLiteralExpr, DoubleLiteralExpr
            // (i float di solito arrivano come DoubleLiteralExpr con suffisso f/F)
            if (e.isIntegerLiteralExpr()) return true;   // es: 0, 0x0, 0b0, 00
            if (e.isLongLiteralExpr())    return true;   // es: 0L
            if (e.isDoubleLiteralExpr())  return true;   // es: 0.0, .0, 0f, 0d
            return false;
        }

    }
}
