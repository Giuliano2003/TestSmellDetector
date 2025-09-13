package testsmell

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import testsmell.smell.EagerTest
import thresholds.DefaultThresholds
import com.github.javaparser.StaticJavaParser


class TestAssertionsDetection {

    private lateinit var testCompilationUnit: CompilationUnit
    private lateinit var productionCompilationUnit: CompilationUnit
    private lateinit var testFile: TestFile
    private val booleanGranularity: ((AbstractSmell) -> Any) = { it.hasSmell() }



    /**
     * Check whether production calls made in the assertions count toward detection of
     * eagerness. In theory, they should. This might not apply (or carefully considered) in the
     * context of generated tests, where the assertions are placed at the end of the search process.
     */
}