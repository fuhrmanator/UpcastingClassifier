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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class VoidVisitorStarter {

    private static final String FILE_PATH;

    static {
        FILE_PATH = "src/main/java/org/javaparser/samples/ReversePolishNotation.java";
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

        //        @Override
//        public void visit(VariableDeclarationExpr vd, Void arg) {
//            super.visit(vd, arg);
//            System.out.println("Declaration: " + vd.toString());
//            for (VariableDeclarator theVD:vd.getVariables()
//                 ) {
//                System.out.println("  Variable: " + theVD);
//                if (theVD.getInitializer().isPresent()) System.out.println("  isNew: " + theVD.getInitializer().get());
//            }
//        }
//        @Override
//        public void visit(ObjectCreationExpr objectCreationExpr, Void arg) {
//            super.visit(objectCreationExpr, arg);
//            System.out.println("New: " + objectCreationExpr.toString() + " Type: " + objectCreationExpr.getType());
//            if (objectCreationExpr.isAssignExpr()) {
//                System.out.println("   Assignment scope: " + objectCreationExpr.getScope());
//            } else if (objectCreationExpr.isVariableDeclarationExpr()) {
//                System.out.println("   used in declaration: " + objectCreationExpr.asVariableDeclarationExpr().getVariables());
//            }
//        }

        @Override
        public void visit(VariableDeclarator variableDeclarator, Void arg) {
            super.visit(variableDeclarator, arg);
            variableDeclarator.getInitializer().ifPresent(expression -> {
                expression.ifObjectCreationExpr(objectCreationExpr -> {
                    System.out.println("New: " + objectCreationExpr
                            + " ==> Type " + objectCreationExpr.getType());
                    System.out.println("  assigned to type " + variableDeclarator.getType());
                    if (variableDeclarator.getType().equals(objectCreationExpr.getType())) {
                        System.out.println("NO upcasting.");
                    } else {
                        System.out.println("Upcasting.");
                    }
                    System.out.println("  in scope:" + variableDeclarator.findAncestor(CompilationUnit.class).
                            flatMap(CompilationUnit::getPrimaryTypeName));
                });
            });
        }

    }

    private static class AssignmentPrinter extends VoidVisitorAdapter<Void> {

        @Override
        public void visit(AssignExpr ae, Void arg) {
            super.visit(ae, arg);
            System.out.println("Assignment: " + ae.toString() + " has objectCreation: " + ae.getValue().isObjectCreationExpr());
        }
    }
}