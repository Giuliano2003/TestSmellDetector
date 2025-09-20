package testsmell.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import testsmell.AbstractSmell;
import testsmell.TestClass;
import testsmell.TestMethod;
import thresholds.Thresholds;

import java.io.FileNotFoundException;

public class IgnoredTest extends AbstractSmell {

    public IgnoredTest(Thresholds thresholds) {
        super(thresholds);
    }

    @Override
    public String getSmellName() {
        return "Ignored Test";
    }

    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit,
                            CompilationUnit productionFileCompilationUnit,
                            String testFileName,
                            String productionFileName) throws FileNotFoundException {
        new ClassVisitor().visit(testFileCompilationUnit, null);
    }

    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Se la classe è annotata @Ignore
            if (n.getAnnotationByName("Ignore").isPresent()) {
                TestClass testClass = new TestClass(n.getNameAsString());
                testClass.setHasSmell(true);
                smellyElementsSet.add(testClass);
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            // JUnit 4: @Test @Ignore
            if (n.getAnnotationByName("Test").isPresent()
                    && n.getAnnotationByName("Ignore").isPresent()) {
                TestMethod tm = new TestMethod(n.getNameAsString());
                tm.setSmell(true);
                putSmellyElement(n.getName().toString());
                addScore(1);
                smellyElementsSet.add(tm);
                return;
            }

            // JUnit 3: metodi che iniziano per 'test' non-public
            if (n.getNameAsString().toLowerCase().startsWith("test")
                    && !n.hasModifier(Modifier.Keyword.PUBLIC)) {
                TestMethod tm = new TestMethod(n.getNameAsString());
                tm.setSmell(true);
                smellyElementsSet.add(tm);
                return;
            }

            super.visit(n, arg);
        }
    }
}
