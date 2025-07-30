package testsmell.smell;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import testsmell.AbstractSmell;
import testsmell.TestMethod;
import testsmell.Util;
import thresholds.Thresholds;

import java.io.FileNotFoundException;
import java.util.*;

public class GeneralFixture extends AbstractSmell {

    private List<MethodDeclaration> methodList;
    private MethodDeclaration setupMethod;
    private List<FieldDeclaration> fieldList;
    private Set<String> setupFields;

    public GeneralFixture(Thresholds thresholds) {
        super(thresholds);
        methodList = new ArrayList<>();
        fieldList = new ArrayList<>();
        setupFields = new HashSet<>();

    }

    @Override
    public String getSmellName() {
        return "General Fixture";
    }

    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit,
                            CompilationUnit productionFileCompilationUnit,
                            String testFileName,
                            String productionFileName) throws FileNotFoundException {
        ClassVisitor classVisitor = new ClassVisitor();
        classVisitor.visit(testFileCompilationUnit, null);

        if (setupMethod != null) {
            setupFields.clear();

            // Find all assignments in setupMethod at any nesting
            setupMethod.findAll(AssignExpr.class).forEach(assign -> {
                Expression target = assign.getTarget();
                try {
                    ResolvedValueDeclaration decl = null;
                    if (target.isFieldAccessExpr()) {
                        decl = target.asFieldAccessExpr().resolve();
                    } else if (target.isNameExpr()) {
                        decl = target.asNameExpr().resolve();
                    }
                    // If it's a field of this class, record its name
                    if (decl != null && decl.isField()) {
                        System.out.println("Nome campo:");
                        System.out.println(decl.getName());
                        setupFields.add(decl.getName());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        for (MethodDeclaration method : methodList) {
            classVisitor.visit(method, null);
        }
    }

    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private MethodDeclaration currentMethod;
        private Set<String> fixtureCount = new HashSet<>();

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Collect methods and fields
            for (BodyDeclaration<?> member : n.getMembers()) {
                if (member instanceof MethodDeclaration) {
                    MethodDeclaration m = (MethodDeclaration) member;
                    if (Util.isValidTestMethod(m)) {
                        methodList.add(m);
                    }
                    if (Util.isValidSetupMethod(m) && m.getBody().isPresent()) {
                        setupMethod = m;
                    }
                }
                if (member instanceof FieldDeclaration) {
                    fieldList.add((FieldDeclaration) member);
                }
            }
        }

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            if (Util.isValidTestMethod(n)) {
                currentMethod = n;
                super.visit(n, arg);
                TestMethod testMethod = new TestMethod(n.getNameAsString());
                boolean isSmelly = fixtureCount.size() != setupFields.size();
                testMethod.setSmell(isSmelly);
                smellyElementsSet.add(testMethod);
                fixtureCount.clear();
                currentMethod = null;
            }
        }

        @Override
        public void visit(NameExpr n, Void arg) {
            if (currentMethod != null) {
                String name = n.getNameAsString();
                if (setupFields.contains(name)) {
                    fixtureCount.add(name);
                }
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(FieldAccessExpr n, Void arg) {
            if (currentMethod != null && n.getScope().isThisExpr()) {
                String name = n.getNameAsString();
                if (setupFields.contains(name)) {
                    fixtureCount.add(name);
                }
            }
            super.visit(n, arg);
        }
    }
}
