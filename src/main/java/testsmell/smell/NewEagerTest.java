package testsmell.smell;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import testsmell.AbstractSmell;
import testsmell.TestMethod;
import testsmell.Util;
import thresholds.Thresholds;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Un test è Eager se le sue assert verificano gli outcome di più di UNA invocazione concreta (CallInstance) della CUT.
 */
public class NewEagerTest extends AbstractSmell {

    private static final String TEST_FILE = "Test";
    private static final String PRODUCTION_FILE = "Production";

    private String productionClassName;
    private List<MethodDeclaration> productionMethods = new ArrayList<>();
    private int eagerCount; 

    public NewEagerTest(Thresholds thresholds) { super(thresholds); }

    @Override public String getSmellName() { return "New Eager Test"; }

    @Override
    public void runAnalysis(CompilationUnit testFileCompilationUnit,
                            CompilationUnit productionFileCompilationUnit,
                            String testFileName, String productionFileName) throws FileNotFoundException {

        if (productionFileCompilationUnit == null) throw new FileNotFoundException();

        ClassVisitor v;

        v = new ClassVisitor(PRODUCTION_FILE);
        v.visit(productionFileCompilationUnit, null);

        v = new ClassVisitor(TEST_FILE);
        v.visit(testFileCompilationUnit, null);

        eagerCount = v.overallSmelly;
    }

    public int getEagerCount() { return eagerCount; }


    static final class CallInstance {
        String id;                      
        String receiverVar;              // var della CUT (alias root)
        ResolvedMethodDeclaration decl;  // può essere null se resolve fallisce
        String methodName;
        List<String> argKeys = Collections.emptyList();
        boolean isConstructor;
        int order;                       

    }

    static final class OutcomeIndex {
        Map<String, CallInstance> valueFromCall = new HashMap<>();
        Map<String, CallInstance> lastMutation  = new HashMap<>(); 
        Map<String, String> aliasRoot           = new HashMap<>(); 
        String rootOf(String v) { return aliasRoot.getOrDefault(v, v); }
    }

    static final class AssertInfo {
        int id;
        int order;
        int line;
        String fqn;           
        Expression observed;  // actual/observed
    }

    static final class AssertBinding {
        int assertId;
        String callId;
        String kind;   
        String rule;   
        String detail;
    }


    private class ClassVisitor extends VoidVisitorAdapter<Void> {
        private final String fileType;
        private MethodDeclaration currentMethod;
        private TestMethod testMethod;

        private int overallSmelly = 0;

        // —— Stato PER-TEST-METHOD ——
        private OutcomeIndex O;
        private List<CallInstance> calls;
        private Map<Node, CallInstance> nodeToCall;
        private List<AssertInfo> asserts;
        private List<AssertBinding> bindings;
        private Set<String> productionVariables;
        private int orderCounter;
        private final List<VariableDeclarator> classLevelFields = new ArrayList<>();

        private final List<FieldAssign> ctorFieldAssigns = new ArrayList<>();
        private final List<FieldAssign> setupFieldAssigns = new ArrayList<>();
        private final Set<String> classFieldNames = new HashSet<>();

        private final class FieldAssign {
            final String fieldName;
            final Expression rhs;
            FieldAssign(String f, Expression r){ this.fieldName=f; this.rhs=r; }
        }



        ClassVisitor(String type) { this.fileType = type; }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            if (Objects.equals(fileType, PRODUCTION_FILE)) productionClassName = n.getNameAsString();
        }

        @Override
        public void visit(EnumDeclaration n, Void arg) {
            super.visit(n, arg);
            if (Objects.equals(fileType, PRODUCTION_FILE)) productionClassName = n.getNameAsString();
        }

        @Override
        public void visit(FieldDeclaration n, Void arg) {
            if (Objects.equals(fileType, TEST_FILE)) {
                classLevelFields.addAll(n.getVariables());
            }
            if (Objects.equals(fileType, TEST_FILE)) {
                for (VariableDeclarator v : n.getVariables()) {
                    classFieldNames.add(v.getNameAsString());
                }
            }
            super.visit(n, arg);
        }



        @Override
        public void visit(ConstructorDeclaration n, Void arg) {
            if (Objects.equals(fileType, TEST_FILE)) {
                for (AssignExpr ae : n.findAll(AssignExpr.class)) {
                    Expression target = ae.getTarget();
                    String field = null;
                    if (target.isFieldAccessExpr()) {
                        FieldAccessExpr fa = target.asFieldAccessExpr();
                        if (fa.getScope().isThisExpr() || fa.getScope().isSuperExpr()) {
                            field = fa.getNameAsString();
                        }
                    } else if (target.isNameExpr()) {
                        String name = target.asNameExpr().getNameAsString();
                        if (classFieldNames.contains(name)) field = name;
                    }
                    if (field != null) {
                        ctorFieldAssigns.add(new FieldAssign(field, ae.getValue()));
                    }
                }
            }
            super.visit(n, arg);
        }



        @Override
        public void visit(MethodDeclaration n, Void arg) {

            if (Objects.equals(fileType, PRODUCTION_FILE)) {
                for (Modifier modifier : n.getModifiers()) {
                    if (n.isPublic() || n.isProtected()) {
                        productionMethods.add(n);
                    }
                }
            }

            if (!Objects.equals(fileType, TEST_FILE)) {
                if (n.isPublic() || n.isProtected()) productionMethods.add(n);
                super.visit(n, arg);
                return;
            }

            if(Util.isValidSetupMethod(n)){
                for (AssignExpr ae : n.findAll(AssignExpr.class)) {
                    Expression target = ae.getTarget();
                    String field = null;

                    if (target.isFieldAccessExpr()) {
                        FieldAccessExpr fa = target.asFieldAccessExpr();
                        if (fa.getScope().isThisExpr() || fa.getScope().isSuperExpr()) {
                            field = fa.getNameAsString();
                        }
                    } else if (target.isNameExpr()) {
                        String name = target.asNameExpr().getNameAsString();
                        if (classFieldNames.contains(name)) {
                            field = name;
                        }
                    }

                    if (field != null) {
                        setupFieldAssigns.add(new FieldAssign(field, ae.getValue()));
                    }
                }
            }

            if (Util.isValidTestMethod(n)) {
                currentMethod = n;
                testMethod = new TestMethod(currentMethod.getNameAsString());
                testMethod.setSmell(false);

                O = new OutcomeIndex();
                calls = new ArrayList<>();
                nodeToCall = new IdentityHashMap<>();
                asserts = new ArrayList<>();
                bindings = new ArrayList<>();
                productionVariables = new HashSet<>();
                orderCounter = 0;

                for (VariableDeclarator v : classLevelFields) {
                    if (v.getType().asString().equals(productionClassName)) {
                        recordProductionVariable(v.getNameAsString());
                    }
                    v.getInitializer().ifPresent(init -> handleAssignment(v.getNameAsString(), init));
                }

                for (FieldAssign fa : ctorFieldAssigns) {
                    recordProductionVariable(fa.fieldName);
                    handleAssignment(fa.fieldName, fa.rhs);
                }

                for (FieldAssign fa : setupFieldAssigns) {
                    recordProductionVariable(fa.fieldName);
                    handleAssignment(fa.fieldName, fa.rhs);
                }


                super.visit(n, arg);

    


                int distinct = (int) bindings.stream().map(b -> b.callId).distinct().count();
                // System.out.println(orderCounter + " distinct " + distinct);
                boolean isSmelly = distinct > 1;
                testMethod.setSmell(isSmelly);
                smellyElementsSet.add(testMethod);
                if (isSmelly){
                    overallSmelly++;
                    putSmellyElement(n.getNameAsString());
                    addScore(1);
                }

                currentMethod = null;
            } else {
                super.visit(n, arg);
            }
        }

        @Override
        public void visit(VariableDeclarator n, Void arg) {
            if (currentMethod == null) {  return; }
            if (n.getType().asString().equals(productionClassName)) {
                recordProductionVariable(n.getNameAsString());
            }
            n.getInitializer().ifPresent(init -> handleAssignment(n.getNameAsString(), init));
            super.visit(n, arg);
        }

        @Override
        public void visit(AssignExpr n, Void arg) {
            if (currentMethod == null) { super.visit(n, arg); return; }
            String lhs = n.getTarget().isNameExpr() ? n.getTarget().asNameExpr().getNameAsString() : null;
            if (lhs != null) handleAssignment(lhs, n.getValue());
            super.visit(n, arg);
        }


        private void handleAssignment(String lhs, Expression rhs) {
            rhs = unwrap(rhs);
            assert rhs != null;
            if (rhs.isNameExpr()) {
                alias(lhs, rhs.asNameExpr().getNameAsString());
                return;
            }
            if (rhs.isObjectCreationExpr()) {
                ObjectCreationExpr oce = rhs.asObjectCreationExpr();
                if (oce.getType().getNameAsString().equals(productionClassName)) {
                    recordProductionVariable(lhs);
                    CallInstance C = newCallInstance(lhs, null, "<init>", true, oce, normalizeArgs(oce.getArguments()));
                    O.valueFromCall.put(lhs, C);
                    O.lastMutation.put(lhs, C);
                    return;
                }
            }
            if (rhs.isMethodCallExpr()) {
                MethodCallExpr m = rhs.asMethodCallExpr();
                String recv = receiverIfCUT(m);
                if (recv != null) {
                    CallInstance C = newCallInstance(recv, tryResolve(m), m.getNameAsString(), false, m, normalizeArgs(m.getArguments()));
                    O.valueFromCall.put(lhs, C);
                    if (looksLikeMutation(C)) O.lastMutation.put(recv, C);
                } else {
                    String root = firstRootName(m);
                    if (root != null && O.valueFromCall.containsKey(root)) O.valueFromCall.put(lhs, O.valueFromCall.get(root));
                }
                return;
            }
            String root = firstRootName(rhs);
            if (root != null && O.valueFromCall.containsKey(root)) O.valueFromCall.put(lhs, O.valueFromCall.get(root));
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            if (currentMethod == null) { super.visit(n, arg); return; }

            super.visit(n, arg);

            String recv = receiverIfCUT(n);
            if (recv != null) {
                CallInstance C = newCallInstance(recv, tryResolve(n), n.getNameAsString(), false, n, normalizeArgs(n.getArguments()));
                if (looksLikeMutation(C)){
                    O.lastMutation.put(recv, C);
                }
            }

            parseJUnit4Assert(n).ifPresent(A -> {
                A.id = asserts.size() + 1;
                A.order = ++orderCounter;
                A.line = n.getBegin().map(p -> p.line).orElse(-1);
                A.fqn = "junit4:" + n.getNameAsString();
                asserts.add(A);
                bindAssertMulti(A); // aggiunge uno o più binding
            });



        }

        private void bindAssertMulti(AssertInfo A) {
            if (A.observed.isNameExpr()) {
                String var = A.observed.asNameExpr().getNameAsString();
                CallInstance src = O.valueFromCall.get(var);
                if (src != null) {
                    if (isCallOnCUT(src) && looksLikeGetter(src)) {
                        CallInstance prod = nearestProducerGlobal(src.receiverVar, A.order);
                        if (prod != null) {
                            bindings.add(binding(A, prod, "CUT_STATE", "NEAREST_PRODUCER", "from CUT getter via var=" + var));
                        }else{
                            bindings.add(binding(A, src, "RETURN_VALUE", "GETTER_NO_PRODUCER",
                                    "no producer found; var=" + var));
                        }
                    } else {
                        bindings.add(binding(A, src, "RETURN_VALUE", "RETURN_VAR", "var=" + var));
                    }
                    return;
                }
            }

            List<MethodCallExpr> directCalls = allMethodCallsOnCUT(A.observed);
            List<CallInstance> directCIs = directCalls.stream()
                    .map(nodeToCall::get).filter(Objects::nonNull).collect(Collectors.toList());

            if (!directCIs.isEmpty()) {
                for (CallInstance C : directCIs) {
                    if (!looksLikeGetter(C)) {
                        bindings.add(binding(A, C, "RETURN_VALUE", "DIRECT_CALL", "call=" + C.methodName));
                    } else {
                        CallInstance prod = nearestProducerGlobal(C.receiverVar, A.order);
                        if (prod != null) bindings.add(binding(A, prod, "CUT_STATE", "NEAREST_PRODUCER", "from getter call=" + C.methodName));
                    }
                }
                return;
            }

            String recv = firstReceiverAlias(A.observed);
            if (recv != null && productionVariables.contains(O.rootOf(recv))) {
                CallInstance prod = nearestProducerGlobal(O.rootOf(recv), A.order);
                if (prod != null) {
                    bindings.add(binding(A, prod, "CUT_STATE", "NEAREST_PRODUCER", "recv=" + recv));
                    return;
                }
            }
            String root = firstRootName(A.observed);
            if (root != null && O.valueFromCall.containsKey(root)) {
                CallInstance src = O.valueFromCall.get(root);
                bindings.add(binding(A, src, "RETURN_VALUE", "DERIVED_FROM", "root=" + root));
            }
        }

        private CallInstance nearestProducerGlobal(String recv, int assertOrder) {
            CallInstance best = null;
            String r = O.rootOf(recv);
            for (CallInstance c : calls) {
                if (!Objects.equals(c.receiverVar, r)) continue;
                if (c.order >= assertOrder) continue;
                if (c.isConstructor || looksLikeMutation(c)) {
                    if (best == null || c.order > best.order) best = c;
                }
            }
            return best;
        }

        private AssertBinding binding(AssertInfo A, CallInstance C, String kind, String rule, String detail) {
            AssertBinding b = new AssertBinding();
            b.assertId = A.id;
            b.callId = C.id;
            b.kind = kind;
            b.rule = rule;
            b.detail = detail;
            return b;
        }


        private void recordProductionVariable(String varName) {
            productionVariables.add(varName);
            O.aliasRoot.putIfAbsent(varName, varName);
        }

        private void alias(String dst, String src) {
            String root = O.rootOf(src);
            O.aliasRoot.put(dst, root);
            if (O.valueFromCall.containsKey(src)) O.valueFromCall.put(dst, O.valueFromCall.get(src));
            if (O.lastMutation.containsKey(src))  O.lastMutation.put(dst,  O.lastMutation.get(src));
        }

        private CallInstance newCallInstance(String recv, ResolvedMethodDeclaration decl, String methodName,
                                             boolean isCtor, Node origin, List<String> argKeys) {
            CallInstance C = new CallInstance();
            C.id = "call#" + (++orderCounter);
            C.receiverVar = recv;
            C.decl = decl;
            C.methodName = methodName;
            C.isConstructor = isCtor;
            C.order = orderCounter;
            C.argKeys = argKeys != null ? argKeys : Collections.emptyList();
            calls.add(C);
            nodeToCall.put(origin, C);
            return C;
        }

        private ResolvedMethodDeclaration tryResolve(MethodCallExpr m) {
            try { return m.resolve(); } catch (Throwable t) {return null; }
        }

        private Expression unwrap(Expression e) {
            while (true) {
                if (e == null) return null;
                if (e.isEnclosedExpr())   e = e.asEnclosedExpr().getInner();
                else if (e.isCastExpr())  e = e.asCastExpr().getExpression();
                else if (e.isFieldAccessExpr() && e.asFieldAccessExpr().getScope().isThisExpr()) {
                    FieldAccessExpr fae = e.asFieldAccessExpr();
                    e = new NameExpr(fae.getNameAsString());
                }
                else break;
            }
            return e;
        }

        private boolean scopeStartsFromCUTClass(Expression e) {
            e = unwrap(e);
            while (e != null) {
                if (e.isNameExpr()) {
                    return e.asNameExpr().getNameAsString().equals(productionClassName);
                } else if (e.isFieldAccessExpr()) {
                    e = e.asFieldAccessExpr().getScope();
                } else if (e.isMethodCallExpr()) {
                    Optional<Expression> sc = e.asMethodCallExpr().getScope();
                    e = sc.orElse(null);
                } else {
                    return false;
                }
                e = unwrap(e);
            }
            return false;
        }

        private boolean isProductionMethodByNameArity(MethodCallExpr m) {
            String name = m.getNameAsString();
            int argc = m.getArguments().size();
            return productionMethods.stream().anyMatch(md ->
                            md.getNameAsString().equals(name)
                                    && md.getParameters().size() == argc
            );
        }


        private String receiverIfCUT(MethodCallExpr m) {
            if (!m.getScope().isPresent()) return null;
            Expression sc = unwrap(m.getScope().get());
            assert sc != null;
            if (sc.isNameExpr()) {
                String name = O.rootOf(sc.asNameExpr().getNameAsString());
                if (productionVariables.contains(name)) return name;
                if (sc.asNameExpr().getNameAsString().equals(productionClassName)) {
                    return productionClassName; 
                }
                if (productionMethods.stream().anyMatch(i -> i.getNameAsString().equals(m.getNameAsString()) &&
                        i.getParameters().size() == m.getArguments().size())) {
                    return sc.asNameExpr().getNameAsString();
                }
                return null;
            }
            if (productionMethods.stream().anyMatch(i -> i.getNameAsString().equals(m.getNameAsString()) &&
                    i.getParameters().size() == m.getArguments().size())) {
                return productionClassName;
            }
            if (scopeStartsFromCUTClass(sc)) {
                return productionClassName;
            }
            String recv = firstReceiverAlias(sc);
            if (recv != null && productionVariables.contains(O.rootOf(recv))) {
                return O.rootOf(recv);
            }
            return null;
        }


        private boolean isCallOnCUT(CallInstance c) { return c.receiverVar != null; }

        private boolean looksLikeMutation(CallInstance c) {
            if (c.decl != null) {
                try { if (c.decl.getReturnType().isVoid()) return true; } catch (Throwable ignored) {}
            }
            String n = c.methodName != null ? c.methodName.toLowerCase() : "";
            return n.startsWith("set") || n.startsWith("add") || n.startsWith("remove") ||
                    n.startsWith("clear") || n.startsWith("update") || n.startsWith("put") ||
                    n.startsWith("append") || n.startsWith("push") || n.startsWith("pop") || n.startsWith("do")
                    || n.startsWith("next") || n.startsWith("reset") || n.startsWith("log") || n.startsWith("insert") || n.startsWith("delete");
        }

        private boolean looksLikeGetter(CallInstance c) {
            String n = c.methodName != null ? c.methodName.toLowerCase() : "";
            if (n.startsWith("get") || n.startsWith("is") || n.equals("size")) return true;
            if (n.startsWith("contains") || n.startsWith("exists") || n.startsWith("has") || n.startsWith("to") || n.equals("capacity")) return true;
            if (c.decl != null) {
                try { if (!c.decl.getReturnType().isVoid() && !looksLikeMutation(c)) return true; } catch (Throwable ignored) {}
            }
            return false;
        }

        private List<String> normalizeArgs(NodeList<Expression> args) {
            List<String> keys = new ArrayList<>();
            for (Expression e : args) {
                if (e.isLiteralExpr()) keys.add("L:" + e.toString());
                else if (e.isNameExpr()) keys.add("V:" + e.asNameExpr().getNameAsString());
                else if (e.isMethodCallExpr()) keys.add("M:" + e.asMethodCallExpr().getNameAsString());
                else keys.add("X");
            }
            return keys;
        }

        private String firstRootName(Expression e) {
            if (e == null) return null;
            if (e.isNameExpr()) return O.rootOf(e.asNameExpr().getNameAsString());
            if (e.isUnaryExpr()) return firstRootName(e.asUnaryExpr().getExpression());
            if (e.isBinaryExpr()) {
                String l = firstRootName(e.asBinaryExpr().getLeft());
                return (l != null) ? l : firstRootName(e.asBinaryExpr().getRight());
            }
            if (e.isConditionalExpr()) {
                String c = firstRootName(e.asConditionalExpr().getCondition());
                if (c != null) return c;
                c = firstRootName(e.asConditionalExpr().getThenExpr());
                if (c != null) return c;
                return firstRootName(e.asConditionalExpr().getElseExpr());
            }
            if (e.isMethodCallExpr()) {
                MethodCallExpr m = e.asMethodCallExpr();
                if (m.getScope().isPresent()) return firstRootName(m.getScope().get());
            }
            if (e.isFieldAccessExpr()) return firstRootName(e.asFieldAccessExpr().getScope());
            if (e.isEnclosedExpr())    return firstRootName(e.asEnclosedExpr().getInner());
            if (e.isCastExpr())        return firstRootName(e.asCastExpr().getExpression());
            if (e.isArrayAccessExpr()) return firstRootName(e.asArrayAccessExpr().getName());
            return null;
        }


        private String firstReceiverAlias(Expression e) {
            if (e == null) return null;
            if (e.isMethodCallExpr()) {
                MethodCallExpr m = e.asMethodCallExpr();
                if (m.getScope().isPresent()) {
                    Expression sc = m.getScope().get();
                    if (sc.isNameExpr()) return O.rootOf(sc.asNameExpr().getNameAsString());
                    return firstReceiverAlias(sc);
                }
            }
            if (e.isFieldAccessExpr()) return firstReceiverAlias(e.asFieldAccessExpr().getScope());
            if (e.isEnclosedExpr()) return firstReceiverAlias(e.asEnclosedExpr().getInner());
            if (e.isCastExpr()) return firstReceiverAlias(e.asCastExpr().getExpression());
            return null;
        }

        private List<MethodCallExpr> allMethodCallsOnCUT(Expression e) {
            Set<MethodCallExpr> set = Collections.newSetFromMap(new IdentityHashMap<>());
            collectCUTCalls(e, set);
            return new ArrayList<>(set);
        }

        private void collectCUTCalls(Expression e, Set<MethodCallExpr> out) {
            if (e == null) return;

            if (e.isMethodCallExpr()) {
                MethodCallExpr m = e.asMethodCallExpr();
                String recv = receiverIfCUT(m);
                if (recv != null) out.add(m);

                m.getScope().ifPresent(sc -> collectCUTCalls(sc, out));
                for (Expression a : m.getArguments()) collectCUTCalls(a, out);
                return;
            }
            if (e.isFieldAccessExpr()) { collectCUTCalls(e.asFieldAccessExpr().getScope(), out); return; }
            if (e.isEnclosedExpr())    { collectCUTCalls(e.asEnclosedExpr().getInner(), out);   return; }
            if (e.isCastExpr())        { collectCUTCalls(e.asCastExpr().getExpression(), out);  return; }
            if (e.isBinaryExpr())      { collectCUTCalls(e.asBinaryExpr().getLeft(), out);
                collectCUTCalls(e.asBinaryExpr().getRight(), out);     return; }
            if (e.isConditionalExpr()) { collectCUTCalls(e.asConditionalExpr().getCondition(), out);
                collectCUTCalls(e.asConditionalExpr().getThenExpr(), out);
                collectCUTCalls(e.asConditionalExpr().getElseExpr(), out); return; }
            if (e.isUnaryExpr())       { collectCUTCalls(e.asUnaryExpr().getExpression(), out); }
        }


        private Optional<AssertInfo> parseJUnit4Assert(MethodCallExpr m) {
            String name = m.getNameAsString();
            if (!JUNIT4_NAMES.contains(name)) return Optional.empty();
            List<Expression> args = m.getArguments();
            Expression observed = null;
            switch (name) {
                case "assertEquals":
                case "assertNotEquals":
                case "assertSame":
                case "assertNotSame":
                case "assertArrayEquals":
                    observed = pickActualForEqualsFamily(args); break;
                case "assertNull":
                case "assertNotNull":
                case "assertTrue":
                case "assertFalse":
                    observed = pickObservedUnary(args); break;
                case "assertThat":
                    if (args.size() >= 2) {
                        observed = (args.size() == 2 ? args.get(0) : args.get(1));
                    }
                    else if (args.size() == 1) {
                        observed = args.get(0);
                    }
                    break;
                    default: return Optional.empty();
            }
            if (observed == null) return Optional.empty();
            AssertInfo A = new AssertInfo();
            A.observed = observed;
            return Optional.of(A);
        }

        private Expression pickObservedUnary(List<Expression> args) {
            return args.size() == 1 ? args.get(0) : (args.size() >= 2 ? args.get(1) : null);
        }
        private Expression pickActualForEqualsFamily(List<Expression> args) {
            if (args.size() == 2) return args.get(1); 
            if (args.size() == 3) {
                if (args.get(0).isStringLiteralExpr()) return args.get(2);
                try {
                    if (args.get(0).calculateResolvedType().isReferenceType() &&
                            "java.lang.String".equals(args.get(0).calculateResolvedType().asReferenceType().getQualifiedName()))
                        return args.get(2);
                } catch (Throwable ignored) {}
                return args.get(1);
            }
            if (args.size() >= 4) return args.get(2);
            return null;
        }
    }

    private static final Set<String> JUNIT4_NAMES = new HashSet<>(Arrays.asList(
            "assertEquals", "assertNotEquals", "assertArrayEquals", "assertSame", "assertNotSame",
            "assertNull", "assertNotNull", "assertTrue", "assertFalse", "assertThat", "fail"
    ));
}
