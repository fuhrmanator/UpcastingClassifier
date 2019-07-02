import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class UpcastingClassification {

    private static File CHOSEN_SOURCE;
    private static CombinedTypeSolver typeSolver;
//    private static SymbolSolver symbolSolver;

    private static ProjectRoot projectRoot;

    public static void main(String[] args) throws Exception {

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setDialogTitle("Select project root folder (sources)");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            CHOSEN_SOURCE = chooser.getSelectedFile();
        } else {
            projectRoot = null;
            System.exit(0);
        }

        String [] jarPaths = {
                "C:/Users/fuhrm/.gradle/caches/modules-2/files-2.1/com.github.javaparser/javaparser-symbol-solver-core/3.14.5/9f941bdbb377ccc7a428f5f55779a33f1dff4145/javaparser-symbol-solver-core-3.14.5.jar",
                "C:/Users/fuhrm/.gradle/caches/modules-2/files-2.1/com.github.javaparser/javaparser-symbol-solver-model/3.14.5/615636566c2a5a68a3ecf79407596c62bdcd441a/javaparser-symbol-solver-model-3.14.5-sources.jar",
                "C:/Users/fuhrm/.gradle/caches/modules-2/files-2.1/com.github.javaparser/javaparser-core/3.14.5/9ecd8f4b92f3ab51d09464cf4c28847a81b96196/javaparser-core-3.14.5.jar",
        };
        typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(), new JavaParserTypeSolver(CHOSEN_SOURCE));
        for (String jarPath: jarPaths)
            typeSolver.add(new JarTypeSolver(jarPath));

        SymbolSolverCollectionStrategy projectRootSolverCollectionStrategy = new SymbolSolverCollectionStrategy();
        projectRoot =
                projectRootSolverCollectionStrategy
                        .collect(CHOSEN_SOURCE.toPath());
//
//        symbolSolver = new SymbolSolver(typeSolver);

        VoidVisitor<?> theVisitor = new MyPrinter();
        System.out.println("projectRoot.getSourceRoots(): " + projectRoot.getSourceRoots());

        for (SourceRoot sourceRoot : projectRoot.getSourceRoots()) {
            System.out.println("Trying to parse files in " + sourceRoot);
            try {
                sourceRoot.tryToParse();
                List<CompilationUnit> compilationUnits = sourceRoot.getCompilationUnits();
                for (CompilationUnit cu : compilationUnits) {
                    System.out.print("-------- Analyzing ");
                    cu.getPrimaryTypeName().ifPresent(System.out::println);
                    theVisitor.visit(cu, null);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
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
                classifyUpcasting(targetType, expressionType);
                printContainingClassName(assignExpr);
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
                    classifyUpcasting(targetType, expressionType);
                    printContainingClassName(variableDeclarator);
                });
            });
        }

        private void classifyUpcasting(ResolvedType targetType, ResolvedType expressionType) {
            if (isSameType(targetType, expressionType)) {
                System.out.println("NO upcasting -- same type (" +
                        targetType.asReferenceType().getQualifiedName() +
                        ") on both sides of assignment.");
            } else {
                System.out.println("Upcasting -- from (" +
                        expressionType.asReferenceType().getQualifiedName() +
                        ") to (" + targetType.asReferenceType().getQualifiedName() + ")");
            }
        }

        private void printContainingClassName(Node node) {
            System.out.print("  in class:");
            Optional<String> name = node.findAncestor(CompilationUnit.class).
                    flatMap(CompilationUnit::getPrimaryTypeName);
            name.ifPresent(System.out::println);
        }

        private boolean isSameType(ResolvedType targetType, ResolvedType expressionType) {
            return targetType.asReferenceType().getQualifiedName().equals(expressionType.asReferenceType().getQualifiedName());
        }

    }

}