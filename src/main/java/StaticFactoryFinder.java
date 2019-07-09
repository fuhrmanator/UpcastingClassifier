import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.utils.SourceRoot;

import java.awt.geom.Area;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class StaticFactoryFinder {

    static final File myProjectSourceDir = new File("../../GitHub/FactoryVariants/src");

    public static void main(String[] args) throws IOException {
        // Parse all source files
        SourceRoot sourceRoot = new SourceRoot(myProjectSourceDir.toPath());
//        sourceRoot.setParserConfiguration(parserConfiguration);
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse("");

// Now get all compilation unitsList
        List<CompilationUnit> allCus = parseResults.stream()
                .filter(ParseResult::isSuccessful)
                .map(r -> r.getResult().get())
                .collect(Collectors.toList());
        List<String> staticFactoryMethods = new ArrayList<>();
        for (CompilationUnit cu:allCus
             ) {
            staticFactoryMethods.addAll(cu.findAll(MethodDeclaration.class).stream()
                    .filter(methodDeclaration -> methodDeclaration.getModifiers().contains(Modifier.staticModifier())
                            && methodDeclaration.getName().getIdentifier().startsWith("create"))
                    .map(methodDeclaration -> methodDeclaration.getName().getIdentifier())
                    .collect(Collectors.toList()));
//                    .forEach(System.out::println);
        }
        staticFactoryMethods.forEach(System.out::println);
        System.out.println("-----------------");
        List<AssignExpr> invocationsOfStaticFactories = new ArrayList<>();
        for (CompilationUnit cu:allCus
             ) {
            invocationsOfStaticFactories.addAll(cu.findAll(AssignExpr.class).stream()
                    .filter(assignExpr -> assignExpr.getValue().isMethodCallExpr()
                            && staticFactoryMethods.contains(
                                    assignExpr.getValue().asMethodCallExpr().getName().getIdentifier()))
                    .collect(Collectors.toList()));
        }
        invocationsOfStaticFactories.forEach(System.out::println);
    }

    public static List<CompilationUnit> getNodes(List<CompilationUnit> cus, Class nodeClass) {
        List res = new LinkedList();
        cus.forEach(cu -> res.addAll(cu.findAll(nodeClass)));
        return res;
    }

}