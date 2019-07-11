import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class StaticFactoryFinder {

    private final File projectSourceDir;
    private final TypeSolver typeSolver;

    private List<String> staticFactoryMethodSignatures;
    private List<AssignExpr> invocationsOfStaticFactoriesInAssignments;
    private List<VariableDeclarator> invocationsOfStaticFactoriesInDeclarationInitializers;

    public StaticFactoryFinder(File projectSourceDir, TypeSolver typeSolver) {
        this.projectSourceDir = projectSourceDir;
        this.typeSolver = typeSolver;
    }

    public static void main(String[] args) throws IOException {
        File projectSourceDir = new File("../../GitHub/FactoryVariants/src");
        // Configure the typeSolver and JSS
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
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
        List<CompilationUnit> allCus = parseResults.stream()
                .filter(ParseResult::isSuccessful)
                .map(r -> {
                    if (r.getResult().isPresent())
                        return r.getResult().get();
                    else return null;
                })
                .collect(Collectors.toList());

        StaticFactoryFinder sff = new StaticFactoryFinder(projectSourceDir, typeSolver);

        sff.findInvocationsOfStaticFactoriesInAssignments(allCus);

    }

    public void findInvocationsOfStaticFactoriesInAssignments(List<CompilationUnit> compilationUnits) {

        staticFactoryMethodSignatures = this.findStaticFactorySignatures(compilationUnits);

        System.out.println("Found the following static factory methods:");
        staticFactoryMethodSignatures.forEach(System.out::println);

        System.out.println("-----------------");

        // Two ways a factory method can be used:
        // assignments, e.g., l = createList(...), or
        // declarations, e.g. List l = createList(...)
        invocationsOfStaticFactoriesInAssignments = this.getAssignmentsWithStaticFactories(compilationUnits,
                staticFactoryMethodSignatures);
        invocationsOfStaticFactoriesInDeclarationInitializers =
                this.getVariableDeclaratorInitializersWithStaticFactories(compilationUnits,
                        staticFactoryMethodSignatures);

        invocationsOfStaticFactoriesInAssignments.forEach(assignExpr -> {
                    System.out.print("Call to static factory in assignment: " + assignExpr.getValue());
                    printContainingClassName(assignExpr);
                }
        );
        invocationsOfStaticFactoriesInDeclarationInitializers.forEach(variableDeclarator -> {
                    Expression call = variableDeclarator.getInitializer().isPresent() ?
                            variableDeclarator.getInitializer().get() : null;
                    System.out.print("Call to static factory in assignment: " +
                            call);
                    printContainingClassName(variableDeclarator);
                }
        );
    }

    @NotNull
    private List<VariableDeclarator> getVariableDeclaratorInitializersWithStaticFactories(
            List<CompilationUnit> compilationUnits, List<String> staticFactoryMethodSignatures) {
        List<VariableDeclarator> invocationsOfStaticFactoriesInDeclarationInitializers = new ArrayList<>();
        for (CompilationUnit cu : compilationUnits) {
            // declaration cases
            invocationsOfStaticFactoriesInDeclarationInitializers.addAll(cu.findAll(VariableDeclarator.class).stream()
                    .filter(variableDeclarator -> {
                        if (variableDeclarator.getInitializer().isPresent() && variableDeclarator.getInitializer().get().isMethodCallExpr()) {
                            ResolvedMethodDeclaration resolved =
                                    variableDeclarator.getInitializer().get().asMethodCallExpr().resolve();
                            return staticFactoryMethodSignatures.contains(resolved.getQualifiedSignature());
                        } else return false;
                    })
                    .collect(Collectors.toList()));
        }
        return invocationsOfStaticFactoriesInDeclarationInitializers;
    }

    @NotNull
    private List<AssignExpr> getAssignmentsWithStaticFactories(List<CompilationUnit> compilationUnits,
                                                               List<String> staticFactoryMethodSignatures) {
        List<AssignExpr> invocationsOfStaticFactoriesInAssignments = new ArrayList<>();
        for (CompilationUnit cu : compilationUnits) {
            // assignment cases
            invocationsOfStaticFactoriesInAssignments.addAll(cu.findAll(AssignExpr.class).stream()
                    .filter(assignExpr -> {
                        if (assignExpr.getValue().isMethodCallExpr()) {
                            ResolvedMethodDeclaration resolved = assignExpr.getValue().asMethodCallExpr().resolve();
                            return staticFactoryMethodSignatures.contains(resolved.getQualifiedSignature());
                        } else return false;
                    })
                    .collect(Collectors.toList()));
        }
        return invocationsOfStaticFactoriesInAssignments;
    }

    @NotNull
    private List<String> findStaticFactorySignatures(List<CompilationUnit> compilationUnits) {
        List<String> staticFactoryMethodSignatures = new ArrayList<>();
        /*
         * Find the static factory methods
         */
        for (CompilationUnit cu : compilationUnits) {
            staticFactoryMethodSignatures.addAll(cu.findAll(MethodDeclaration.class).stream()
                    .filter(methodDeclaration ->
                            methodDeclaration.getModifiers().contains(Modifier.staticModifier())  // is STATIC
                                    && methodDeclaration.getName().getIdentifier().startsWith("create"))  // is a
                    // supposed Factory
                    .map(methodDeclaration -> methodDeclaration.resolve().getQualifiedSignature())
                    .collect(Collectors.toList()));
        }
        return staticFactoryMethodSignatures;
    }

    private static void printContainingClassName(@NotNull Node node) {
        System.out.print("  in class: ");
        System.out.println(getFullyQualifiedName(Objects.requireNonNull(getClass(node))));
    }

    // See https://stackoverflow.com/a/55722326/1168342
    private static String getFullyQualifiedName(ClassOrInterfaceType e) {
        String name = "";
        if (e.getScope().isPresent())
            name += getFullyQualifiedName(e.getScope().get()) + ".";
        return name + e.getNameAsString();
    }

    private static String getFullyQualifiedName(ClassOrInterfaceDeclaration c2) {
        String name = "";
        ClassOrInterfaceDeclaration parentClass = c2.getParentNode().isPresent() ?
                getClass(c2.getParentNode().get()) : null;
        if (parentClass != null) {
            name += getFullyQualifiedName(parentClass) + ".";
        } else {
            CompilationUnit u = getCompilationUnit(c2);
            if (u != null && u.getPackageDeclaration().isPresent()) {
                name += u.getPackageDeclaration().get().getNameAsString() + ".";
            }
        }
        return name + c2.getNameAsString();
    }

    private static ClassOrInterfaceDeclaration getClass(Node n1) {
        while (!(n1 instanceof ClassOrInterfaceDeclaration)) {
            if (n1.getParentNode().isPresent()) {
                n1 = n1.getParentNode().get();
            } else return null;
        }
        return (ClassOrInterfaceDeclaration) n1;
    }

    private static CompilationUnit getCompilationUnit(Node n1) {
        while (!(n1 instanceof CompilationUnit)) {
            if (n1.getParentNode().isPresent()) {
                n1 = n1.getParentNode().get();
            } else return null;
        }
        return (CompilationUnit) n1;
    }

    public List<String> getStaticFactoryMethodSignatures() {
        return staticFactoryMethodSignatures;
    }

    public List<AssignExpr> getInvocationsOfStaticFactoriesInAssignments() {
        return invocationsOfStaticFactoriesInAssignments;
    }

    public List<VariableDeclarator> getInvocationsOfStaticFactoriesInDeclarationInitializers() {
        return invocationsOfStaticFactoriesInDeclarationInitializers;
    }

}