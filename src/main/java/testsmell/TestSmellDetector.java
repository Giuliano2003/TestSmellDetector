package testsmell;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.apache.commons.lang3.StringUtils;
import testsmell.smell.*;
import thresholds.Thresholds;
import report.JsonReporter;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestSmellDetector {

    private List<AbstractSmell> testSmells;
    private Thresholds thresholds;

    /**
     * Instantiates the various test smell analyzer classes and loads the objects into an list.
     * Each smell analyzer is initialized with a threshold object to set the most appropriate rule for the detection
     *
     * @param thresholds it could be the default threshold of the ones defined by Spadini
     */
    public TestSmellDetector(Thresholds thresholds) {
        this.thresholds = thresholds;
        initializeSmells();
    }

    private void initializeSmells() {
        testSmells = new ArrayList<>();
        testSmells.add(new AssertionRoulette(thresholds));
        testSmells.add(new ConditionalTestLogic(thresholds));
        testSmells.add(new ConstructorInitialization(thresholds));
        testSmells.add(new DefaultTest(thresholds));
        testSmells.add(new EmptyTest(thresholds));
        testSmells.add(new ExceptionCatchingThrowing(thresholds));
        testSmells.add(new GeneralFixture(thresholds));
        testSmells.add(new MysteryGuest(thresholds));
        testSmells.add(new PrintStatement(thresholds));
        testSmells.add(new RedundantAssertion(thresholds));
        testSmells.add(new SensitiveEquality(thresholds));
        testSmells.add(new VerboseTest(thresholds));
        testSmells.add(new SleepyTest(thresholds));
        testSmells.add(new EagerTest(thresholds));
        testSmells.add(new LazyTest(thresholds));
        testSmells.add(new DuplicateAssert(thresholds));
        testSmells.add(new UnknownTest(thresholds));
        testSmells.add(new IgnoredTest(thresholds));
        testSmells.add(new ResourceOptimism(thresholds));
        testSmells.add(new MagicStringTest(thresholds));
        testSmells.add(new MagicNumberTest(thresholds));
        testSmells.add(new DependentTest(thresholds));
        testSmells.add(new NewEagerTest(thresholds));
    }

    public void setTestSmells(List<AbstractSmell> testSmells) {
        this.testSmells = testSmells;
    }

    /**
     * Provides the names of the smells that are being checked for in the code
     *
     * @return list of smell names
     */
    public List<String> getTestSmellNames() {
        return testSmells.stream().map(AbstractSmell::getSmellName).collect(Collectors.toList());
    }

    /**
     * Loads the java source code file into an AST and then analyzes it for the existence of the different types of
     * test smells
     */
    public TestFile detectSmells(TestFile testFile) throws IOException {
        initializeSmells();

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver(false)); // JRE

        if (!StringUtils.isEmpty(testFile.getTestFilePath())) {
            File testDir = new File(testFile.getTestFilePath()).getParentFile();
            if (testDir != null && testDir.isDirectory()) {
                typeSolver.add(new JavaParserTypeSolver(testDir));
            }
        }

        if (!StringUtils.isEmpty(testFile.getProductionFilePath())) {
            File prodDir = new File(testFile.getProductionFilePath()).getParentFile();
            if (prodDir != null && prodDir.isDirectory()) {
                typeSolver.add(new JavaParserTypeSolver(prodDir));
            }
        }

        typeSolver.add(new ClassLoaderTypeSolver(Thread.currentThread().getContextClassLoader()));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);

        ParserConfiguration parserConfig = new ParserConfiguration().setSymbolResolver(symbolSolver);
        JavaParser parser = new JavaParser(parserConfig);

        CompilationUnit testFileCU = null;
        CompilationUnit prodFileCU = null;

        if (!StringUtils.isEmpty(testFile.getTestFilePath())) {
            try (FileInputStream in = new FileInputStream(testFile.getTestFilePath())) {
                ParseResult<CompilationUnit> result = parser.parse(in);
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    testFileCU = result.getResult().get();
                    TypeDeclaration<?> typeDecl = testFileCU.getTypes().get(0);
                    testFile.setNumberOfTestMethods(typeDecl.getMethods().size());
                } else {
                    throw new IOException("Parsing errors: " + result.getProblems());
                }
            }
        }

        if (!StringUtils.isEmpty(testFile.getProductionFilePath())) {
            try (FileInputStream in = new FileInputStream(testFile.getProductionFilePath())) {
                ParseResult<CompilationUnit> result = parser.parse(in);
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    prodFileCU = result.getResult().get();
                } else {
                    throw new IOException("Parsing errors: " + result.getProblems());
                }
            }
        }

        for (AbstractSmell smell : testSmells) {
            try {
                smell.runAnalysis(testFileCU, prodFileCU,
                        testFile.getTestFileNameWithoutExtension(),
                        testFile.getProductionFileNameWithoutExtension());
            } catch (FileNotFoundException e) {
                testFile.addSmell(null);
                continue;
            }
            testFile.addSmell(smell);
        }
        java.util.Map<String, java.util.Set<String>> jsonMap = new java.util.LinkedHashMap<>();

        for (AbstractSmell smell : testSmells) {
            if (smell == null) continue;
            java.util.Map<String, java.util.Set<String>> partial = smell.getResult();
            if (partial == null) continue;

            for (java.util.Map.Entry<String, java.util.Set<String>> e : partial.entrySet()) {
                String smellName = e.getKey();
                java.util.Set<String> methods = e.getValue();
                if (methods == null) continue;

                jsonMap.computeIfAbsent(smellName, k -> new java.util.LinkedHashSet<>())
                        .addAll(methods);
            }
        }
        System.out.println(report.JsonReporter.toJson(jsonMap));

        return testFile;
    }


}
