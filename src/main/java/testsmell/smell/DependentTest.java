package testsmell.smell;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import testsmell.AbstractSmell;
import testsmell.Util;
import thresholds.Thresholds;
import java.io.FileNotFoundException;
import java.util.*;

public class DependentTest extends AbstractSmell {

    private final Map<String, TestMethod> testMethods; //mappa nome del test con TestMethod che contiene tutto l'utile


    public DependentTest(Thresholds thresholds) {
        super(thresholds);
        testMethods = new HashMap<>();
    }


    @Override
    public String getSmellName() {
        return "Dependent Test";
    }


    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit, CompilationUnit productionFileCompilationUnit, String testFileName, String productionFileName) throws FileNotFoundException {
        DependentTest.ClassVisitor classVisitor;
        classVisitor = new DependentTest.ClassVisitor();
        classVisitor.visit(testFileCompilationUnit, null);
        for (TestMethod tm : testMethods.values()) {
            for (CalledMethod cm : tm.getCalled()) {
                TestMethod target = testMethods.get(cm.getName());
                if (target != null) {
                    int args = target.getArgs();
                    if(args == cm.getArgs()){
                        smellyElementsSet.add(
                                new testsmell.TestMethod(tm.getName(), true)
                        );
                        System.out.println("Dependent Test detected: " + tm.getName());
                    }
                }
            }
        }

    }

    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private String currentMethod = null;
        private int argsOfTheCurrentMethod = 0;

        // examine all methods in the test class
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            if (Util.isValidTestMethod(md)) {
                currentMethod = md.getNameAsString();
                argsOfTheCurrentMethod = md.getParameters().size();
                testMethods.put(currentMethod, new TestMethod(currentMethod,argsOfTheCurrentMethod));
                super.visit(md, arg);
            }
        }

        @Override
        public void visit(MethodCallExpr mc, Void arg) {
            super.visit(mc, arg);
            if (currentMethod != null) {
                testMethods.get(currentMethod)
                        .addCalled(new CalledMethod(mc.getNameAsString(), mc.getArguments().size()));
            }
        }
    }

    private class TestMethod {
        private String name = null; // nome del metodo in esame ad es. @Test
        private int args = 0;
        private Set<CalledMethod> called = new HashSet<>(); // set di metodi che richiama

        public TestMethod(String currentMethod,int args) {
            this.name = currentMethod;
            this.args = args;
        }

        public int getArgs() {
            return args;
        }

        void addCalled(CalledMethod cm) { called.add(cm); }
        Set<CalledMethod> getCalled() { return called; }

        public String getName() {
            return name;
        }
    }

    private class CalledMethod {
        private String name = null;
        private int args = 0;

        public CalledMethod(String nameAsString, int size) {
            this.name = nameAsString;
            this.args=size;
        }


        public int getArgs() {
            return args;
        }

        public String getName() {
            return name;
        }
    }
}