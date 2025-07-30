package testsmell.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import testsmell.AbstractSmell;
import testsmell.TestMethod;
import testsmell.Util;
import thresholds.Thresholds;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.LiteralExpr;

import java.io.FileNotFoundException;
import java.util.Arrays;

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
                testMethod.addDataItem("MagicNumberCount", String.valueOf(magicCount));
                smellyElementsSet.add(testMethod);
                System.out.println("Test analizzato:");
                System.out.println(n.getNameAsString());
                System.out.println(testMethod.isSmelly());
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
            if (expr instanceof LiteralExpr) {
                if (Util.isNumber(expr.toString())) {
                    magicCount++;
                }
            }
            else if (expr instanceof BinaryExpr) {
                BinaryExpr bin = (BinaryExpr) expr;
                countMagicNumbers(bin.getLeft());
                countMagicNumbers(bin.getRight());
            }
            else if (expr instanceof MethodCallExpr) { // del tipo assertEquals(someMethod(5))
                for (Expression arg : ((MethodCallExpr) expr).getArguments()) {
                    countMagicNumbers(arg);
                }
            }
            else if (expr instanceof ObjectCreationExpr) { // del tipo assertEquals(new Integer(5))
                for (Expression arg : ((ObjectCreationExpr) expr).getArguments()) {
                    countMagicNumbers(arg);
                }
            }
        }
    }
}
