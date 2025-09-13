package testsmell.smell;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithArguments;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import testsmell.AbstractSmell;
import testsmell.TestMethod;
import testsmell.Util;
import thresholds.Thresholds;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Level;

public class GeneralFixture extends AbstractSmell {

    private List<MethodDeclaration> methodList;
    private MethodDeclaration setupMethod;
    private List<FieldDeclaration> fieldList;
    private Set<String> setupFields;

    private static final Logger log = LoggerFactory.getLogger(GeneralFixture.class);

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
            setupMethod.findAll(AssignExpr.class).forEach(assign -> {
                Expression target = assign.getTarget();
                try {
                    ResolvedValueDeclaration decl = null;
                    if (target.isFieldAccessExpr()) {
                        decl = target.asFieldAccessExpr().resolve();
                    } else if (target.isNameExpr()) {
                        decl = target.asNameExpr().resolve();
                    }
                    if (decl != null && decl.isField()) {
                        setupFields.add(decl.getName());
                    }
                } catch (Exception e) {
                    log.error("Impossibile risolvere il simbolo {}", target, e);
                }
            });
            setupMethod.getBody().ifPresent(body -> {
                List<NodeWithArguments<?>> calls = new ArrayList<>();
                calls.addAll(body.findAll(MethodCallExpr.class));
                calls.addAll(body.findAll(ObjectCreationExpr.class));
                calls.addAll(body.findAll(ExplicitConstructorInvocationStmt.class));
                for (NodeWithArguments<?> call : calls) {
                    for (Expression arg : call.getArguments()) {
                        try {
                            String name = null;
                            if (arg.isFieldAccessExpr()) {
                                FieldAccessExpr fa = arg.asFieldAccessExpr();
                                if (fa.getScope().isThisExpr()) {
                                    name = fa.getNameAsString();
                                }
                            } else if (arg.isNameExpr()) {
                                name = arg.asNameExpr().getNameAsString();
                            }
                            if (name != null) {
                                setupFields.remove(name);
                            }
                        } catch (Exception e) {
                            log.warn("Errore nella risoluzione dell'argomento {}", arg, e);
                        }
                    }
                    if (call instanceof MethodCallExpr) {
                        MethodCallExpr m = (MethodCallExpr) call;
                        m.getScope().ifPresent(scope -> {
                            try {
                                String recv = extractReceiverName(scope);
                                if (recv != null) {
                                    setupFields.remove(recv); // es. "bank" in bank.openConsumerAccount(...)
                                }
                            } catch (Exception e) {
                                log.warn("Errore nella risoluzione del receiver {}", scope, e);
                            }
                        });
                    }
                }
            });


        }
        for (MethodDeclaration method : methodList) {
            classVisitor.visit(method, null);
        }
    }

    // Utility: estrae il nome della variabile/campo usata come receiver di una call.
// Gestisce chain (es. bank.getX().getY()), this.bank, cast, parentesi, ecc.
    private static String extractReceiverName(Expression scope) {
        Expression e = scope;
        while (e != null) {
            if (e.isEnclosedExpr()) {                       // ( ... )
                e = e.asEnclosedExpr().getInner();
                continue;
            }
            if (e.isCastExpr()) {                           // (Type) expr
                e = e.asCastExpr().getExpression();
                continue;
            }
            if (e.isFieldAccessExpr()) {                    // this.bank  oppure  someObj.field
                FieldAccessExpr fa = e.asFieldAccessExpr();
                // Caso classico: this.bank -> nome del campo = "bank"
                if (fa.getScope().isThisExpr()) {
                    return fa.getNameAsString();
                }
                // Altrimenti continua a risalire a sinistra (es. someObj.field -> risali a someObj)
                e = fa.getScope();
                continue;
            }
            if (e.isMethodCallExpr()) {                     // chain: foo().bar() -> risali allo scope di foo()
                e = e.asMethodCallExpr().getScope().orElse(null);
                continue;
            }
            if (e.isNameExpr()) {                           // bank
                return e.asNameExpr().getNameAsString();
            }
            if (e.isThisExpr()) {                           // this.method() senza campo specifico
                return null;
            }
            break;
        }
        return null;
    }


    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private MethodDeclaration currentMethod;
        private Set<String> fixtureCount = new HashSet<>();
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
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
