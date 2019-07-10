import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class StaticFactoryFinder {

    private static final File myProjectSourceDir = new File("../../GitHub/FactoryVariants/src");
    private static TypeSolver typeSolver;

    public static void main(String[] args) throws IOException {
        // Configure the typeSolver and JSS
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(myProjectSourceDir));
        ParserConfiguration parserConfiguration =
                new ParserConfiguration()
                        .setSymbolResolver(new JavaSymbolSolver(typeSolver));

        // Parse all source files with the typeSolver and JSS configures
        SourceRoot sourceRoot = new SourceRoot(myProjectSourceDir.toPath());
        sourceRoot.setParserConfiguration(parserConfiguration);

        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse("");


        // Now get all compilation unitsList
        List<CompilationUnit> allCus = parseResults.stream()
                .filter(ParseResult::isSuccessful)
                .map(r -> r.getResult().get())
                .collect(Collectors.toList());
        List<String> staticFactoryMethods = new ArrayList<>();
        for (CompilationUnit cu : allCus
        ) {
            staticFactoryMethods.addAll(cu.findAll(MethodDeclaration.class).stream()
                    .filter(methodDeclaration -> methodDeclaration.getModifiers().contains(Modifier.staticModifier())
                            && methodDeclaration.getName().getIdentifier().startsWith("create"))
                    .map(methodDeclaration -> {
                        ResolvedMethodDeclaration resolved = methodDeclaration.resolve();
                        //System.out.println("Found " + resolved.getClassName() + "." + resolved.getName() + " in " + cu.getPrimaryType().get().resolve().getQualifiedName());
                        return resolved.getClassName() + "." + resolved.getName();
                    })
                    .collect(Collectors.toList()));
        }
        staticFactoryMethods.forEach(System.out::println);
        System.out.println("-----------------");
        List<AssignExpr> invocationsOfStaticFactoriesInAssignments = new ArrayList<>();
        List<VariableDeclarator> invocationsOfStaticFactoriesInDeclarations = new ArrayList<>();
        for (CompilationUnit cu : allCus
        ) {
            invocationsOfStaticFactoriesInAssignments.addAll(cu.findAll(AssignExpr.class).stream()
                    .filter(assignExpr -> {
                        if (assignExpr.getValue().isMethodCallExpr()) {
                            ResolvedMethodDeclaration resolved = assignExpr.getValue().asMethodCallExpr().resolve();
                            System.out.println("  Found assignment with method invocation: " + resolved.getClassName() + "." + resolved.getName());
                            //System.out.println("    in Class: " + cu.getPrimaryType().map(typeDeclaration -> typeDeclaration.resolve().getQualifiedName()));
                            System.out.println("  2 in Class: " + /* cu.getType(0).resolve().getPackageName() + "|" + */ cu.getType(0).resolve().getQualifiedName());
                            return staticFactoryMethods.contains(
                                    resolved.getClassName()
                                            + "." + resolved.getName()
                            );
                        }
                        return false;
                    })
                    .collect(Collectors.toList()));
            invocationsOfStaticFactoriesInDeclarations.addAll(cu.findAll(VariableDeclarator.class).stream()
                    .filter(variableDeclarator -> {
                        if (variableDeclarator.getInitializer().isPresent() && variableDeclarator.getInitializer().get().isMethodCallExpr()) {
                            ResolvedMethodDeclaration resolved = variableDeclarator.getInitializer().get().asMethodCallExpr().resolve();
                            System.out.println("  Found declaration with initialization via method invocation: " + resolved.getClassName() + "." + resolved.getName());
                            System.out.println("  2 in Class: " + /* cu.getType(0).resolve().getPackageName() + "|" + */ cu.getType(0).resolve().getQualifiedName());
                            return staticFactoryMethods.contains(
                                    resolved.getClassName()
                                            + "." + resolved.getName());
                        } else return false;
                    })
                    .collect(Collectors.toList()));
        }

        invocationsOfStaticFactoriesInAssignments.forEach(assignExpr -> {
                    System.out.print("Call to static factory in assignment: " + assignExpr.getValue());
                    printContainingClassName(assignExpr);
                }
        );
        invocationsOfStaticFactoriesInDeclarations.forEach(variableDeclarator -> {
                    System.out.print("Call to static factory in assignment: " +
                            variableDeclarator.getInitializer().get());
                    printContainingClassName(variableDeclarator);
                }
        );
    }

    public static List<CompilationUnit> getNodes(List<CompilationUnit> cus, Class nodeClass) {
        List res = new LinkedList();
        cus.forEach(cu -> res.addAll(cu.findAll(nodeClass)));
        return res;
    }

    private static void printContainingClassName(@NotNull Node node) {
        System.out.println("  in class: ");
        Optional<CompilationUnit> ancestor = node.findAncestor(CompilationUnit.class);
        //Optional<ClassOrInterfaceDeclaration> declaredType =
        if (ancestor.isPresent()) {
            CompilationUnit compilationUnit = ancestor.get();
            if (compilationUnit.getPrimaryType().isPresent()) {
                TypeDeclaration<?> typeDeclaration = compilationUnit.getPrimaryType().get();
                    System.out.println("Package: " + typeDeclaration.resolve().getPackageName());
                    System.out.println("Classname: " + typeDeclaration.resolve().getQualifiedName());
            } else System.out.println("No primary type for ancestor for node " + node);
        } else System.out.println("Could not find ancestor for node " + node);
        
//        System.out.println("  fully qualified name: " + getFullyQualifiedName(declaredType.get()));
//        ancestor.ifPresent(compilationUnit -> compilationUnit.getPrimaryType().ifPresent(typeDeclaration -> {
//            System.out.println(typeDeclaration.resolve().asReferenceType().getPackageName());
//            System.out.println(typeDeclaration.getFullyQualifiedName());
//        }));
//        Optional<String> name = ancestor.flatMap(compilationUnit -> compilationUnit.getPrimaryTypeName());
//        name.ifPresent(System.out::println);
    }
    // See https://stackoverflow.com/a/55722326/1168342
    private static String getFullyQualifiedName(ClassOrInterfaceType e) {
        String name = "";
        if(e.getScope().isPresent())
            name+=getFullyQualifiedName(e.getScope().get())+".";
        return name+e.getNameAsString();
    }

    private static String getFullyQualifiedName(ClassOrInterfaceDeclaration c2) {
        String name = "";
        ClassOrInterfaceDeclaration parentClass = c2.getParentNode().isPresent() ? getClass(c2.getParentNode().get()): null;
        if(parentClass!=null) {
            name+=getFullyQualifiedName(parentClass)+".";
        } else {
            CompilationUnit u = getCompilationUnit(c2);
            if(u!=null && u.getPackageDeclaration().isPresent()) {
                name+=u.getPackageDeclaration().get().getNameAsString()+".";
            }
        }
        return name+c2.getNameAsString();
    }

    private static ClassOrInterfaceDeclaration getClass(Node n1) {
        while (!(n1 instanceof ClassOrInterfaceDeclaration)) {
            if(n1.getParentNode().isPresent()) {
                n1 = n1.getParentNode().get();
            } else return null;
        }
        return (ClassOrInterfaceDeclaration)n1;
    }

    private static CompilationUnit getCompilationUnit(Node n1) {
        while (!(n1 instanceof CompilationUnit)) {
            if(n1.getParentNode().isPresent()) {
                n1 = n1.getParentNode().get();
            } else return null;
        }
        return (CompilationUnit)n1;
    }
}