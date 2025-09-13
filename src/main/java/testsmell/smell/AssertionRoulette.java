package testsmell.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import testsmell.AbstractSmell;
import testsmell.SmellyElement;
import testsmell.TestMethod;
import testsmell.Util;
import thresholds.Thresholds;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * "Guess what's wrong?" This smell comes from having a number of assertions in a test method that have no explanation.
 * If one of the assertions fails, you do not know which one it is.
 * A. van Deursen, L. Moonen, A. Bergh, G. Kok, “Refactoring Test Code”, Technical Report, CWI, 2001.
 */
public class AssertionRoulette extends AbstractSmell {

    private static final Logger log = LoggerFactory.getLogger(AssertionRoulette.class);
    private int assertionsCount = 0;

    public AssertionRoulette(Thresholds thresholds) {
        super(thresholds);
    }

    /**
     * Checks of 'Assertion Roulette' smell
     */
    @Override
    public String getSmellName() {
        return "Assertion Roulette";
    }

    /**
     * Analyze the test file for test methods for multiple assert statements without an explanation/message
     */
    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit, CompilationUnit productionFileCompilationUnit, String testFileName, String productionFileName) throws FileNotFoundException {
        AssertionRoulette.ClassVisitor classVisitor;
        classVisitor = new AssertionRoulette.ClassVisitor();
        classVisitor.visit(testFileCompilationUnit, null);
        assertionsCount = classVisitor.overallAssertions;
    }

    public int getAssertionsCount() {
        return assertionsCount;
    }

    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private MethodDeclaration currentMethod = null;
        private int assertNoMessageCount = 0;
        private int assertCount = 0;
        private int overallAssertions = 0;
        TestMethod testMethod;

        // examine all methods in the test class
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            if (Util.isValidTestMethod(n)) {
                currentMethod = n;
                testMethod = new TestMethod(n.getNameAsString());
                testMethod.setSmell(false); //default value is false (i.e. no smell)
                super.visit(n, arg);

                boolean isSmelly = assertNoMessageCount >= thresholds.getAssertionRoulette();

                //the method has a smell if there is more than 1 call to production methods
                testMethod.setSmell(isSmelly);
                // if there is only 1 assert statement in the method, then a explanation message is not needed
                if (assertCount == 1)
                    testMethod.setSmell(false);
                    //if there is more than one assert statement, then all the asserts need to have an explanation message
                else if (isSmelly) {
                    testMethod.setSmell(true);
                }

                testMethod.addDataItem("AssertCount", String.valueOf(assertNoMessageCount));
                smellyElementsSet.add(testMethod);

                //reset values for next method
                currentMethod = null;
                overallAssertions += assertCount;
                assertCount = 0;
                assertNoMessageCount = 0;
            }
        }

        // examine the methods being called within the test method
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (currentMethod != null) {
                if (n.getNameAsString().startsWith("assertEquals")
                        || n.getNameAsString().startsWith("assertArrayEquals")
                        || n.getNameAsString().startsWith("assertNotEquals")) {
                    assertCount++;
                    int argCount = n.getArguments().size();
                    boolean hasMessage = false;
                    if (argCount > 2) {
                        Expression firstArg = n.getArgument(0);
                        try {
                            ResolvedType rt = firstArg.calculateResolvedType();
                            if (rt.isReferenceType()
                                    && rt.asReferenceType().getQualifiedName().equals("java.lang.String")) {
                                hasMessage = true;
                            }
                        } catch (UnsolvedSymbolException | UnsupportedOperationException e) {
                            log.error("Non riesco a leggere il simbolo");
                        }
                    }
                    if (!(argCount == 4 || (argCount == 3 && hasMessage))) {
                        assertNoMessageCount++;
                    }
                }
                else if (n.getNameAsString().startsWith(("assertNotSame")) ||
                        n.getNameAsString().startsWith(("assertSame")) ||
                        n.getNameAsString().startsWith("assertThrows") ||
                        n.getNameAsString().startsWith(("assertThat"))) {
                    assertCount++;
                    if (n.getArguments().size() < 3) {
                        assertNoMessageCount++;
                    }
                }
                else if (n.getNameAsString().equals("assertFalse") ||
                        n.getNameAsString().equals("assertNotNull") ||
                        n.getNameAsString().equals("assertNull") ||
                        n.getNameAsString().equals("assertTrue")) {
                    assertCount++;
                    if (n.getArguments().size() < 2) {
                        assertNoMessageCount++;
                    }
                }
                else if (n.getNameAsString().equals("fail")) {
                    assertCount++;
                    if (n.getArguments().size() < 1) {
                        assertNoMessageCount++;
                    }
                }

            }
        }

    }
}

