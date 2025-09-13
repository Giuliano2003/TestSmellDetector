package testsmell;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.MethodDeclaration;

public class Util {

    public static boolean isValidTestMethod(MethodDeclaration n) {
        if (n.getAnnotationByName("Ignore").isPresent()) {
            return false;
        }
        // Deve essere un test JUnit 4 (@Test) o JUnit 3 (nome inizia con "test")
        boolean isTest = n.getAnnotationByName("Test").isPresent()
                || n.getNameAsString().toLowerCase().startsWith("test");
        if (!isTest) {
            return false;
        }
        return n.hasModifier(Modifier.Keyword.PUBLIC);
    }

    public static boolean isValidSetupMethod(MethodDeclaration n) {
        // Non consideriamo metodi ignorati
        if (n.getAnnotationByName("Ignore").isPresent()) {
            return false;
        }
        // Deve essere setup JUnit 4 (@Before) o JUnit 3 (nome esatto "setUp")
        boolean isSetup = n.getAnnotationByName("Before").isPresent()
                || n.getNameAsString().equals("setUp");
        if (!isSetup) {
            return false;
        }
        // Deve essere pubblico
        return n.hasModifier(Modifier.Keyword.PUBLIC);
    }

    public static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException er) {
            return false;
        }
    }

    public static boolean isNumber(String str) {

        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }
}
