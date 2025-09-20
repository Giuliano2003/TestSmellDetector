package testsmell.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import testsmell.AbstractSmell;
import testsmell.TestMethod;
import testsmell.Util;
import thresholds.Thresholds;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

public class SensitiveEquality extends AbstractSmell {

    public SensitiveEquality(Thresholds thresholds) {
        super(thresholds);
    }

    @Override
    public String getSmellName() {
        return "Sensitive Equality";
    }

    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit,
                            CompilationUnit productionFileCompilationUnit,
                            String testFileName,
                            String productionFileName) throws FileNotFoundException {
        ClassVisitor classVisitor = new ClassVisitor();
        classVisitor.visit(testFileCompilationUnit, null);
    }

    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private MethodDeclaration currentMethod = null;
        private int sensitiveCount = 0;
        private TestMethod testMethod;
        private Set<String> sensitiveVars = new HashSet<>();
        private final Set<String> methodTargets = Set.of("toString");

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            if (Util.isValidTestMethod(n)) {
                currentMethod = n;
                sensitiveVars.clear();
                sensitiveCount = 0;
                testMethod = new TestMethod(n.getNameAsString());
                testMethod.setSmell(false);

                super.visit(n, arg);

                boolean isSmelly = sensitiveCount > thresholds.getSensitiveEquality();

                testMethod.setSmell(isSmelly);
                if(isSmelly){
                    putSmellyElement(n.getName().toString());
                    addScore(sensitiveCount);
                }
                testMethod.addDataItem("SensitiveCount", String.valueOf(sensitiveCount));
                smellyElementsSet.add(testMethod);

                currentMethod = null;
            }
        }

        @Override
        public void visit(VariableDeclarator n, Void arg) {
            super.visit(n, arg);
            n.getInitializer().ifPresent(value -> {
                boolean isSensitive = value.findAll(MethodCallExpr.class).stream()
                        .map(MethodCallExpr::getNameAsString)
                        .anyMatch(methodTargets::contains);
                if (isSensitive) {
                    sensitiveVars.add(n.getNameAsString());
                }
            });
        }


        @Override
        public void visit(AssignExpr n, Void arg) {
            super.visit(n, arg);
            if (currentMethod == null) return;
            Expression target = n.getTarget();
            Expression value = n.getValue();
            String varName = null;
            if (target.isNameExpr()) {
                varName = target.asNameExpr().getNameAsString();
            } else if (target.isFieldAccessExpr()) {
                varName = target.asFieldAccessExpr().getNameAsString();
            }
            if (varName != null) {
                boolean isSensitive = value.findAll(MethodCallExpr.class).stream()
                        .map(MethodCallExpr::getNameAsString)
                        .anyMatch(methodTargets::contains);
                if (isSensitive) {
                    sensitiveVars.add(varName);
                }
            }
        }

        private boolean isEqualityAssert(String name) {
            return name.equals("assertEquals")
                    || name.equals("assertNotEquals")
                    || name.equals("assertArrayEquals")
                    || name.equals("assertSame")
                    || name.equals("assertNotSame")
                    || name.equals("assertTrue")
                    || name.equals("assertFalse")
                    || name.equals("assertThat")
                    || name.equals("fail");
        }


        private boolean isSensitiveExpr(Expression expr) {
            if (expr == null) return false;

            boolean direct = expr.findAll(MethodCallExpr.class).stream()
                    .anyMatch(mc -> methodTargets.contains(mc.getNameAsString()));

            boolean viaVar = expr.findAll(NameExpr.class).stream()
                    .map(NameExpr::getNameAsString)
                    .anyMatch(sensitiveVars::contains);

            boolean viaFieldName = expr.findAll(FieldAccessExpr.class).stream()
                    .map(fa -> fa.getNameAsString())
                    .anyMatch(sensitiveVars::contains);

            return direct || viaVar || viaFieldName;
        }



        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (currentMethod != null) {
                String name = n.getNameAsString();
                if (isEqualityAssert(name)) {
                    for (Expression argument : n.getArguments()) {
                        if (isSensitiveExpr(argument)) {
                            sensitiveCount++;
                        }
                    }
                }
            }
        }


    }
}
