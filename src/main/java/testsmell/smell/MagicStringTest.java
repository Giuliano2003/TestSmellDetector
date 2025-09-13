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
import java.util.List;

/**
 * Detects the "Magic String Test" smell:
 * stringhe letterali hard-coded usate nella logica di verifica (assert),
 */
public class MagicStringTest extends AbstractSmell {

    public MagicStringTest(Thresholds thresholds) {
        super(thresholds);
    }

    @Override
    public String getSmellName() {
        return "Magic String Test";
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
        private TestMethod testMethod;
        private int magicStringCount = 0;

        @Override
        public void visit(MethodDeclaration n, Void arg) {
            if (Util.isValidTestMethod(n)) {
                currentMethod = n;
                testMethod = new TestMethod(n.getNameAsString());
                testMethod.setSmell(false); // default

                super.visit(n, arg);

                // decide smell by threshold
                testMethod.setSmell(magicStringCount >= thresholds.getMagicStringTest());
                testMethod.addDataItem("MagicStringCount", String.valueOf(magicStringCount));
                smellyElementsSet.add(testMethod);

                // reset for next method
                currentMethod = null;
                magicStringCount = 0;
            }
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);

            if (currentMethod == null) return;

            String name = n.getNameAsString();
            boolean isJUnitAssert = isAssertOfInterest(name);
            boolean isAssertJ = isInAssertJChain(n) && !isAssertJDescriptionMethod(name);

            if (!(isJUnitAssert || isAssertJ)) return;


            List<Expression> args = n.getArguments();

            int startIndex = 0;
            if(isJUnitAssert) {
                if ((args.size() >= 3 && supportsMessageOverloadHead_3plus(name) && isStringLike(args.get(0))) || (
                        (args.size() >= 2 && supportsMessageOverloadHead_2args(name) && isStringLike(args.get(0)))
                )) {
                    // questo lo faccio perchè ci sono test come G04/03 che ci sono test con messaggi con due argomenti e se non
                    //mettessi questo l'algoritmo partirebbe da 0 contandomi come smell i messaggi.
                    startIndex = 1;
                }
            }

            for (int i = startIndex; i < args.size(); i++) {
                countMagicStrings(args.get(i));
            }
        }

        private boolean isInAssertJChain(MethodCallExpr n) {
            // true se nello scope della call c'è, risalendo la catena, un "assertThat(...)"
            Expression scope = n.getScope().orElse(null);
            while (scope instanceof MethodCallExpr) {
                MethodCallExpr m = (MethodCallExpr) scope;
                if (m.getNameAsString().equals("assertThat")) return true;
                scope = m.getScope().orElse(null);
            }
            return false;
        }

        private boolean isAssertJDescriptionMethod(String name) {
            return name.equals("as")
                    || name.equals("describedAs")
                    || name.equals("withFailMessage")
                    || name.equals("overridingErrorMessage");
        }


        private boolean supportsMessageOverloadHead_2args(String name) {
            return name.startsWith("assertTrue")
                    || name.startsWith("assertFalse")
                    || name.startsWith("assertNull")
                    || name.startsWith("assertNotNull");
        }

        private boolean isAssertOfInterest(String name) {
            return name.startsWith("assertEquals")
                    || name.startsWith("assertNotEquals")
                    || name.startsWith("assertSame")
                    || name.startsWith("assertNotSame")
                    || name.startsWith("assertArrayEquals")
                    || name.equals("assertThat")
                    || name.equals("assertTrue")
                    || name.equals("assertFalse")
                    || name.equals("assertNull")
                    || name.equals("assertNotNull");
        }

        /** Queste assert hanno overload con messaggio/raison come primo argomento. */
        private boolean supportsMessageOverloadHead_3plus(String name) {
            return name.startsWith("assertEquals")
                    || name.startsWith("assertNotEquals")
                    || name.startsWith("assertSame")
                    || name.startsWith("assertNotSame")
                    || name.startsWith("assertArrayEquals");
        }

        /** Heuristica: l'argomento è "messaggio" se contiene literal string (anche concatenazioni). */
        private boolean isStringLike(Expression expr) {
            if (expr.isStringLiteralExpr()) return true;
            if (expr.isBinaryExpr()) return containsStringLiteral(expr);
            return false;
        }

        /** Conta i literal string che partecipano alla verifica (ricorsivo). */
        private void countMagicStrings(Expression expr) {
            if (expr == null) return;

            if (expr.isStringLiteralExpr()) {
                magicStringCount++;
                return;
            }

            if (expr instanceof BinaryExpr) {
                BinaryExpr bin = (BinaryExpr) expr;
                countMagicStrings(bin.getLeft());
                countMagicStrings(bin.getRight());
                return;
            }

            if (expr instanceof MethodCallExpr) {
                // es. assertThat(actual, is("OK")) → visita "is(...)" e i suoi argomenti
                for (Expression a : ((MethodCallExpr) expr).getArguments()) {
                    countMagicStrings(a);
                }
                return;
            }

            if (expr instanceof ObjectCreationExpr) {
                for (Expression a : ((ObjectCreationExpr) expr).getArguments()) {
                    countMagicStrings(a);
                }
                return;
            }

            if (expr instanceof ArrayInitializerExpr) {
                for (Expression a : ((ArrayInitializerExpr) expr).getValues()) {
                    countMagicStrings(a);
                }
                return;
            }

            if (expr instanceof ArrayCreationExpr) {
                ((ArrayCreationExpr) expr).getInitializer().ifPresent(this::countMagicStrings);
                return;
            }

            if (expr instanceof EnclosedExpr) {
                countMagicStrings(((EnclosedExpr) expr).getInner());
                return;
            }

            if (expr instanceof ConditionalExpr) {
                ConditionalExpr c = (ConditionalExpr) expr;
                countMagicStrings(c.getThenExpr());
                countMagicStrings(c.getElseExpr());
                return;
            }

            if (expr instanceof CastExpr) {
                countMagicStrings(((CastExpr) expr).getExpression());
                return;
            }
        }

        /** Rileva se l'espressione (ricorsivamente) contiene almeno un StringLiteralExpr. */
        private boolean containsStringLiteral(Expression expr) {
            if (expr == null) return false;
            if (expr.isStringLiteralExpr()) return true;

            if (expr instanceof BinaryExpr) {
                BinaryExpr bin = (BinaryExpr) expr;
                return containsStringLiteral(bin.getLeft()) || containsStringLiteral(bin.getRight());
            }
            if (expr instanceof MethodCallExpr) {
                for (Expression a : ((MethodCallExpr) expr).getArguments()) {
                    if (containsStringLiteral(a)) return true;
                }
            }
            if (expr instanceof ObjectCreationExpr) {
                for (Expression a : ((ObjectCreationExpr) expr).getArguments()) {
                    if (containsStringLiteral(a)) return true;
                }
            }
            if (expr instanceof ArrayInitializerExpr) {
                for (Expression a : ((ArrayInitializerExpr) expr).getValues()) {
                    if (containsStringLiteral(a)) return true;
                }
            }
            if (expr instanceof ArrayCreationExpr) {
                return ((ArrayCreationExpr) expr).getInitializer()
                        .map(this::containsStringLiteral).orElse(false);
            }
            if (expr instanceof EnclosedExpr) {
                return containsStringLiteral(((EnclosedExpr) expr).getInner());
            }
            if (expr instanceof ConditionalExpr) {
                ConditionalExpr c = (ConditionalExpr) expr;
                return containsStringLiteral(c.getThenExpr()) || containsStringLiteral(c.getElseExpr());
            }
            if (expr instanceof CastExpr) {
                return containsStringLiteral(((CastExpr) expr).getExpression());
            }
            return false;
        }
    }
}
