import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public class UpcastingClassifierTest {
    private File projectSourceDir;
    private CombinedTypeSolver typeSolver;
    private List<CompilationUnit> compilationUnits;

    @Test
    public void testStaticFactoryFinder() {
        UpcastingClassifier uc = new UpcastingClassifier(projectSourceDir, typeSolver);
        uc.findObjectCreationExpressions(compilationUnits);

        Assert.assertEquals(26, uc.getObjectCreationsInAssignments().size());
        Assert.assertEquals(18, uc.getObjectCreationsInDeclarationInitializers().size());
        Assert.assertEquals(10, uc.getCompilationUnitsWithObjectCreationsUpcast().size());
        Assert.assertEquals(5, uc.getCompilationUnitsWithObjectCreationsNotUpcast().size());

        String[] expectedClassNamesNotUpcasting = {
                "NoInterfaceClient",
                "headfirst.designpatterns.factory.pizzaaf.Pizza",
                "headfirst.designpatterns.factory.pizzas.Pizza",
                "headfirst.designpatterns.factory.pizzafm.Pizza",
                "headfirst.designpatterns.factory.pizzas.PizzaTestDrive"};
        int i = 0;
        for (Iterator<ClassOrInterfaceDeclaration> iter =
             uc.getCompilationUnitsWithObjectCreationsNotUpcast().iterator(); iter.hasNext(); i++){
            Assert.assertEquals(expectedClassNamesNotUpcasting[i],
                    iter.next().getFullyQualifiedName().get());
        }

        String[] expectedClassNamesUpcasting = {
                "headfirst.designpatterns.factory.pizzas.SimplePizzaFactory",
                "static_factory.Product",
                "headfirst.designpatterns.factory.pizzafm.DependentPizzaStore",
                "headfirst.designpatterns.factory.pizzaaf.ChicagoPizzaStore",
                "headfirst.designpatterns.factory.pizzaaf.NYPizzaStore",
                "simple_factory.SimpleFactory",
                "headfirst.designpatterns.factory.pizzaaf.PizzaTestDrive",
                "ClientWithUnprotected",
                "ClientNoFactoryMain",
                "headfirst.designpatterns.factory.pizzafm.PizzaTestDrive",
        };
        //uc.getCompilationUnitsWithObjectCreationsUpcast().stream().forEach(classOrInterfaceDeclaration -> System
        // .out.println(classOrInterfaceDeclaration.getFullyQualifiedName().get()));
        i = 0;
        for (Iterator<ClassOrInterfaceDeclaration> iter =
             uc.getCompilationUnitsWithObjectCreationsUpcast().iterator(); iter.hasNext(); i++){
            Assert.assertEquals(expectedClassNamesUpcasting[i],
                    iter.next().getFullyQualifiedName().get());
        }

        Assert.assertTrue("TODO: Need to test getObjectCreationsInAssignments", false);
        Assert.assertTrue("TODO: Need to test getObjectCreationsInDeclarationInitializers", false);

//        Assert.assertTrue(sff.getStaticFactoryMethodSignatures()
//                .contains("static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE)"));
//        Assert.assertTrue(sff.getStaticFactoryMethodSignatures()
//                .contains("simple_factory.SimpleFactory.createProduct(simple_factory.SimpleFactory.PRODUCT)"));
//
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(0).toString(),
//                "stpB = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_B)");
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(1).toString(),
//                "smpB = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.B)");
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(2).toString(),
//                "stpB = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_B)");
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInAssignments().get(3).toString(),
//                "smpB = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.B)");
//
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(0).toString(),
//                "stpA = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_A)");
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(1).toString(),
//                "smpA = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.A)");
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(2).toString(),
//                "stpA = static_factory.Product.createProduct(static_factory.Product.PRODUCT_TYPE.PRODUCT_A)");
//        Assert.assertEquals(sff.getInvocationsOfStaticFactoriesInDeclarationInitializers().get(3).toString(),
//                "smpA = simple_factory.SimpleFactory.createProduct(SimpleFactory.PRODUCT.A)");

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