import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class StaticFactoryFinderTest {
    private File projectSourceDir;
    private CombinedTypeSolver typeSolver;
    private List<CompilationUnit> compilationUnits;

    @Test
    public void testStaticFactoryFinder() {
        StaticFactoryFinder sff = new StaticFactoryFinder(projectSourceDir, typeSolver);
        sff.findInvocationsOfStaticFactoriesInAssignments(compilationUnits);
        Assert.assertEquals(2, sff.getStaticFactoryMethodSignatures().size());
        Assert.assertEquals(4, sff.getInvocationsOfStaticFactoriesInAssignments().size());
        Assert.assertEquals(4, sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().size());

        Assert.assertTrue(sff.getStaticFactoryMethodSignatures()
                .contains("static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE)"));
        Assert.assertTrue(sff.getStaticFactoryMethodSignatures()
                .contains("simple_factory.SimpleFactory.createProduct(simple_factory.SimpleFactory.PRODUCT)"));

        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(0).toString(),
                "stpB = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_B)");
        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(1).toString(),
                "smpB = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.B)");
        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(2).toString(),
                "stpB = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_B)");
        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(3).toString(),
                "smpB = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.B)");

        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(0).toString(),
                "stpA = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_A)");
        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(1).toString(),
                "smpA = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.A)");
        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(2).toString(),
                "stpA = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_A)");
        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(3).toString(),
                "smpA = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.A)");

    }

    @Before
    public void setUp() throws Exception {
//        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        projectSourceDir = new File("src/test/resources/FactoryVariants/src");
        // Configure the typeSolver and JSS
        typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(projectSourceDir));
        ParserConfiguration parserConfiguration =
                new ParserConfiguration()
                        .setSymbolResolver(new JavaSymbolSolver(typeSolver));

        // Parse all source files with the typeSolver and JSS configures
        SourceRoot sourceRoot = new SourceRoot(projectSourceDir.toPath());
        sourceRoot.setParserConfiguration(parserConfiguration);

        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse("");

        // Now get all compilation unitsList
        compilationUnits = parseResults.stream()
                .filter(ParseResult::isSuccessful)
                .map(r -> {
                    if (r.getResult().isPresent())
                        return r.getResult().get();
                    else return null;
                })
                .collect(Collectors.toList());

    }
}