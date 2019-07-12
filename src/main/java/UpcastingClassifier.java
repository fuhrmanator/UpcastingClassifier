import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class UpcastingClassifier {

    private final File projectSourceDir;
    private final TypeSolver typeSolver;

    private List<AssignExpr> objectCreationsInAssignments;
    private List<VariableDeclarator> objectCreationsInDeclarationInitializers;
    private LinkedHashSet<ClassOrInterfaceDeclaration> compilationUnitsWithObjectCreationsUpcast = new LinkedHashSet<>();
    private LinkedHashSet<ClassOrInterfaceDeclaration> compilationUnitsWithObjectCreationsNotUpcast =
            new LinkedHashSet<>();

    public UpcastingClassifier(File projectSourceDir, TypeSolver typeSolver) {
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

        UpcastingClassifier upcastingClassifier = new UpcastingClassifier(projectSourceDir, typeSolver);
        upcastingClassifier.findObjectCreationExpressions(allCus);

    }

    public void findObjectCreationExpressions(List<CompilationUnit> compilationUnits) {

        // Two ways we are interested in 'new Object()':
        // assignments, e.g., l = new ArrayList(), or
        // declarations, e.g. List l = new ArrayList(...)
        objectCreationsInAssignments = getAssignmentsWithObjectCreations(compilationUnits);
        objectCreationsInDeclarationInitializers =
                getVariableDeclaratorInitializersWithObjectCreations(compilationUnits);

        objectCreationsInAssignments.forEach(assignExpr -> {
                    //System.out.println("Object creation in assignment: " + assignExpr.getValue());
                    if (isUpcastingAssignmentExpr(assignExpr))
                        compilationUnitsWithObjectCreationsUpcast.add(getClass(assignExpr));
                    else
                        compilationUnitsWithObjectCreationsNotUpcast.add(getClass(assignExpr));
                    //printContainingClassName(assignExpr);
                }
        );
        objectCreationsInDeclarationInitializers.forEach(variableDeclarator -> {
//                    Expression call = variableDeclarator.getInitializer().isPresent() ?
//                            variableDeclarator.getInitializer().get() : null;
//                    System.out.println("Object creation in initializer: " + call);
                    if (isUpcastingVariableDeclarator(variableDeclarator))
                        compilationUnitsWithObjectCreationsUpcast.add(getClass(variableDeclarator));
                    else
                        compilationUnitsWithObjectCreationsNotUpcast.add(getClass(variableDeclarator));
                    //printContainingClassName(variableDeclarator);
                }
        );
    }

    @NotNull
    private List<VariableDeclarator> getVariableDeclaratorInitializersWithObjectCreations(
            List<CompilationUnit> compilationUnits) {
        List<VariableDeclarator> objectCreationsInDeclarationInitializers = new ArrayList<>();
        for (CompilationUnit cu : compilationUnits) {
            // declaration cases
            objectCreationsInDeclarationInitializers.addAll(cu.findAll(VariableDeclarator.class).stream()
                    .filter(variableDeclarator -> variableDeclarator.getInitializer().isPresent() &&
                            variableDeclarator.getInitializer().get().isObjectCreationExpr())
                    .collect(Collectors.toList()));
        }
        return objectCreationsInDeclarationInitializers;
    }

    @NotNull
    private List<AssignExpr> getAssignmentsWithObjectCreations(List<CompilationUnit> compilationUnits) {
        List<AssignExpr> objectCreationsInAssignments = new ArrayList<>();
        for (CompilationUnit cu : compilationUnits) {
            // assignment cases
            objectCreationsInAssignments.addAll(cu.findAll(AssignExpr.class).stream()
                    .filter(assignExpr -> assignExpr.getValue().isObjectCreationExpr())
                    .collect(Collectors.toList()));
        }
        return objectCreationsInAssignments;
    }

    private static void printContainingClassName(@NotNull Node node) {
        System.out.print("  in class: ");
        getClass(node).getFullyQualifiedName().ifPresent(System.out::println);
        //System.out.println(getFullyQualifiedName(Objects.requireNonNull(getClass(node))));
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

    public List<AssignExpr> getObjectCreationsInAssignments() {
        return objectCreationsInAssignments;
    }

    public List<VariableDeclarator> getObjectCreationsInDeclarationInitializers() {
        return objectCreationsInDeclarationInitializers;
    }

    private boolean isUpcasting(ResolvedType targetType, ResolvedType expressionType) {
        if (isSameType(targetType, expressionType)) {
            System.out.println("NO Upcasting -- same type (" +
                    targetType.asReferenceType().getQualifiedName() +
                    ") on both sides of assignment.");
            return false;
        } else {
            System.out.println("YES Upcasting -- from (" +
                    expressionType.asReferenceType().getQualifiedName() +
                    ") to (" + targetType.asReferenceType().getQualifiedName() + ")");
            return true;
        }
    }

    private boolean isSameType(@NotNull ResolvedType targetType, @NotNull ResolvedType expressionType) {
        return targetType.asReferenceType().getQualifiedName().equals(expressionType.asReferenceType().getQualifiedName());
    }

    private boolean isUpcastingAssignmentExpr(AssignExpr assignExpr) {
        System.out.printf("New (assignExpr) '%s'\n", assignExpr);
        final ObjectCreationExpr objectCreationExpr = assignExpr.getValue().asObjectCreationExpr();
        final ResolvedType[] targetType = new ResolvedType[1];
        final ResolvedType expressionType;
        ResolvedType expressionType1 = null;
        try {
            expressionType1 = objectCreationExpr.getType().resolve();
            //System.out.println(" objectCreationExpr.type() is " + expressionType1);
        } catch (UnsolvedSymbolException e) {
            System.err.printf("!>>>> ERROR: Could not resolve objectCreationExpr type: %s\n", e.getName());
        } catch (UnsupportedOperationException e) {
            System.err.printf("!>>>> ERROR: %s\n", e.getMessage());
        }
        expressionType = expressionType1;
        try {
            Expression target = assignExpr.getTarget();
            targetType[0] = target.calculateResolvedType(); // uses JSS
//                    assignExpr.getTarget().toTypeExpr().ifPresent(typeExpr -> targetType[0] = typeExpr.getType()
//                    .resolve());
            //System.out.println(" targetType is " + targetType[0]);
        } catch (UnsolvedSymbolException e) {
            System.err.printf("!>>>> ERROR: Could not resolve assignExpr.getTarget() type: %s\n", e.getName());
        } catch (UnsupportedOperationException e) {
            System.err.printf("!>>>> ERROR: %s\n", e.getMessage());
        }

        if (targetType[0] != null && expressionType != null) return isUpcasting(targetType[0], expressionType);
        else return false;
    }

    private boolean isUpcastingVariableDeclarator(VariableDeclarator variableDeclarator) {
        final ObjectCreationExpr objectCreationExpr = variableDeclarator.getInitializer().get().asObjectCreationExpr();
        System.out.printf("New (variableDeclarator) '%s'\n", variableDeclarator);
        final ResolvedType targetType, expressionType;
        ResolvedType targetType1 = null, expressionType1 = null;
        try {
            expressionType1 = objectCreationExpr.getType().resolve();
        } catch (UnsolvedSymbolException e) {
            System.err.printf("!>>>> ERROR: Could not resolve objectCreationExpr type: %s\n", e.getName());
        } catch (UnsupportedOperationException e) {
            System.err.printf("!>>>> ERROR: %s\n", e.getMessage());
        }
        expressionType = expressionType1;

        try {
            targetType1 = variableDeclarator.getType().resolve();
        } catch (UnsolvedSymbolException e) {
            System.err.printf("!>>>> ERROR: Could not resolve variableDeclarator type: %s\n", e.getName());
        } catch (UnsupportedOperationException e) {
            System.err.printf("!>>>> ERROR: %s\n", e.getMessage());
        }

        targetType = targetType1;
        if (targetType != null && expressionType != null) return isUpcasting(targetType, expressionType);
        else return false;

    }

    public LinkedHashSet<ClassOrInterfaceDeclaration> getCompilationUnitsWithObjectCreationsUpcast() {
        return compilationUnitsWithObjectCreationsUpcast;
    }

    public LinkedHashSet<ClassOrInterfaceDeclaration> getCompilationUnitsWithObjectCreationsNotUpcast() {
        return compilationUnitsWithObjectCreationsNotUpcast;
    }

}