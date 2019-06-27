import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DotPrinter;
import com.github.javaparser.printer.YamlPrinter;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.SymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class VoidVisitorStarter {

    private static final String FILE_PATH, SOURCE_PATH;
    private static TypeSolver typeSolver;
    private static SymbolSolver symbolSolver;

    static {
        FILE_PATH = "src/main/java/org/javaparser/samples/ReversePolishNotation.java";
        SOURCE_PATH = "src/main/java/org/javaparser/samples/";
        typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(), new JavaParserTypeSolver(SOURCE_PATH));
        symbolSolver = new SymbolSolver(typeSolver);
    }

    public static void main(String[] args) throws Exception {

        CompilationUnit cu = StaticJavaParser.parse(new File(FILE_PATH));
//        DotPrinter printer = new DotPrinter(true);
//        try (FileWriter fileWriter = new FileWriter("ast.dot");
//             PrintWriter printWriter = new PrintWriter(fileWriter)) {
//            printWriter.print(printer.output(cu));
//        }
//        YamlPrinter printer = new YamlPrinter(true);
//        System.out.println(printer.output(cu));
        VoidVisitor<?> theVisitor = new MyPrinter();
        theVisitor.visit(cu, null);
    }

    private static class MyPrinter extends VoidVisitorAdapter<Void> {

        @Override
        public void visit(AssignExpr assignExpr, Void arg) {
            super.visit(assignExpr, arg);
            assignExpr.getValue().ifObjectCreationExpr(objectCreationExpr -> {
                ResolvedType targetType, expressionType;
                expressionType = JavaParserFacade.get(typeSolver).getType(objectCreationExpr);
                targetType = JavaParserFacade.get(typeSolver).getType(assignExpr.getTarget());

                System.out.println("New (assignExpr): " + assignExpr);
//                System.out.println("    Type from resolver: " + JavaParserFacade.get(typeSolver).getType(objectCreationExpr));
//                System.out.print("  assigned to ");
//                assignExpr.getTarget().ifFieldAccessExpr(fieldAccessExpr -> {
//                    System.out.println("fieldAccess: " + fieldAccessExpr.getName());
//                });
//                assignExpr.getTarget().ifNameExpr(nameExpr -> {
//                    System.out.println("nameExpression: " + nameExpr.getName());
//                });
//                System.out.println("    Type from resolver: " + JavaParserFacade.get(typeSolver).getType(assignExpr.getTarget()));
                if (targetType.equals(expressionType)) {
                    System.out.println("NO upcasting -- same type (" + targetType + ") on both sides of assignment.");
                } else {
                    System.out.println("Upcasting -- from (" + expressionType + ") to (" + targetType + ")");
                }
                System.out.println("  in class:" + assignExpr.findAncestor(CompilationUnit.class).
                        flatMap(CompilationUnit::getPrimaryTypeName));
            });
        }


        @Override
        public void visit(VariableDeclarator variableDeclarator, Void arg) {
            super.visit(variableDeclarator, arg);
            variableDeclarator.getInitializer().ifPresent(expression -> {
                expression.ifObjectCreationExpr(objectCreationExpr -> {
                    ResolvedType targetType, expressionType;
                    expressionType = JavaParserFacade.get(typeSolver).getType(objectCreationExpr);
                    targetType = JavaParserFacade.get(typeSolver).getType(variableDeclarator);

                    System.out.println("New (variableDeclarator): " + variableDeclarator);
//                    System.out.println("    Type from resolver: " + expressionType);
//                    System.out.println("  assigned to type " + variableDeclarator.getType());
//                    System.out.println("    Type from resolver: " + targetType);
                    if (targetType.asReferenceType().getQualifiedName().equals(expressionType.asReferenceType().getQualifiedName())) {
                        System.out.println("NO upcasting -- same type (" + targetType + ") on both sides of assignment.");
                    } else {
                        System.out.println("Upcasting -- from (" + expressionType + ") to (" + targetType + ")");
                    }
                    System.out.println("  in class:" + variableDeclarator.findAncestor(CompilationUnit.class).
                            flatMap(CompilationUnit::getPrimaryTypeName));
                });
            });
        }

    }

}