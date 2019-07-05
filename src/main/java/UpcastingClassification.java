import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.SymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UpcastingClassification {

    private static File CHOSEN_SOURCE_DIR;
    private static CombinedTypeSolver typeSolver;
    private static SymbolSolver symbolSolver;

    public static void main(String[] args) throws Exception {

        ProjectRoot projectRoot;
        JFileChooser chooser;
        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle("Select project root folder (sources)");
        int returnVal = chooser.showOpenDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            CHOSEN_SOURCE_DIR = chooser.getSelectedFile();
        } else {
            System.exit(0);
        }

        chooser.setDialogTitle("Select project root folder (jars)");
        returnVal = chooser.showOpenDialog(null);
        File jarPath;
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            jarPath = chooser.getSelectedFile();
        } else {
            jarPath = null;
            System.exit(0);
        }

        Collection<Path> jarFilesCollection = Files.find(Paths.get(jarPath.getPath()),
                Integer.MAX_VALUE,
                (filePath, fileAttr) -> filePath.toFile().getName().endsWith("jar")).collect(Collectors.toList());
        jarFilesCollection.forEach(System.out::println);

        // Useful shell command to find JARs for a class:
        // find /path/to/jars/ -name '*.jar' -exec grep -Hls VoidVisitor {} \;
        typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver() /* , new JavaParserTypeSolver(CHOSEN_SOURCE_DIR) */);

        /* Find all the Camel components' root directories, normally 'src' */
        Collection<Path> componentsPathCollection = Files.find(Paths.get(jarPath.getPath()),
                Integer.MAX_VALUE,
                (filePath, fileAttr) -> filePath.toFile().getName().equals("src")).collect(Collectors.toList());
        System.out.println("Components directories:");
        componentsPathCollection.forEach(System.out::println);
        componentsPathCollection.forEach(path -> typeSolver.add(new JavaParserTypeSolver(path.toFile())));


        jarFilesCollection.forEach(path -> {
            try {
                typeSolver.add(new JarTypeSolver(path.toFile()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        symbolSolver = new SymbolSolver(typeSolver);

        SymbolSolverCollectionStrategy projectRootSolverCollectionStrategy = new SymbolSolverCollectionStrategy();
        projectRoot =
                projectRootSolverCollectionStrategy
                        .collect(CHOSEN_SOURCE_DIR.toPath());

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
                System.out.printf("New (assignExpr) '%s'\n", assignExpr);
                final ResolvedType[] targetType = new ResolvedType[1];
                final ResolvedType expressionType;
                ResolvedType expressionType1 = null;
                try {
                    expressionType1 = objectCreationExpr.getType().resolve();
                } catch (UnsolvedSymbolException e) {
                    System.out.printf("!>>>> ERROR: Could not resolve objectCreationExpr type: %s\n", e.getName());
                } catch (UnsupportedOperationException e) {
                    System.out.printf("!>>>> ERROR: %s\n", e.getMessage());
                }
                expressionType = expressionType1;
                try {
                    assignExpr.getTarget().toTypeExpr().ifPresent(typeExpr -> targetType[0] = typeExpr.getType().resolve());
                } catch (UnsolvedSymbolException e) {
                    System.out.printf("!>>>> ERROR: Could not resolve assignExpr.getTarget() type: %s\n", e.getName());
                } catch (UnsupportedOperationException e) {
                    System.out.printf("!>>>> ERROR: %s\n", e.getMessage());
                }

                if (targetType[0] != null && expressionType != null) classifyUpcasting(targetType[0], expressionType);
                printContainingClassName(assignExpr);
            });
        }

        @Override
        public void visit(VariableDeclarator variableDeclarator, Void arg) {
            super.visit(variableDeclarator, arg);
            variableDeclarator.getInitializer().ifPresent(expression -> expression.ifObjectCreationExpr(objectCreationExpr -> {
                System.out.printf("New (variableDeclarator) '%s'\n", variableDeclarator);
                final ResolvedType targetType, expressionType;
                ResolvedType targetType1 = null, expressionType1 = null;
                try {
                    expressionType1 = objectCreationExpr.getType().resolve();
                } catch (UnsolvedSymbolException e) {
                    System.out.printf("!>>>> ERROR: Could not resolve objectCreationExpr type: %s\n", e.getName());
                } catch (UnsupportedOperationException e) {
                    System.out.printf("!>>>> ERROR: %s\n", e.getMessage());
                }
                expressionType = expressionType1;

                try {
                    targetType1 = variableDeclarator.getType().resolve();
                } catch (UnsolvedSymbolException e) {
                    System.out.printf("!>>>> ERROR: Could not resolve variableDeclarator type: %s\n", e.getName());
                } catch (UnsupportedOperationException e) {
                    System.out.printf("!>>>> ERROR: %s\n", e.getMessage());
                }

                targetType = targetType1;
                if (targetType != null && expressionType != null) classifyUpcasting(targetType, expressionType);
                printContainingClassName(variableDeclarator);
            }));
        }

        private void classifyUpcasting(ResolvedType targetType, ResolvedType expressionType) {
            if (isSameType(targetType, expressionType)) {
                System.out.println("NO Upcasting -- same type (" +
                        targetType.asReferenceType().getQualifiedName() +
                        ") on both sides of assignment.");
            } else {
                System.out.println("YES Upcasting -- from (" +
                        expressionType.asReferenceType().getQualifiedName() +
                        ") to (" + targetType.asReferenceType().getQualifiedName() + ")");
            }
        }

        private void printContainingClassName(@NotNull Node node) {
            System.out.print("  in class:");
            Optional<String> name = node.findAncestor(CompilationUnit.class).
                    flatMap(CompilationUnit::getPrimaryTypeName);
            name.ifPresent(System.out::println);
        }

        private boolean isSameType(@NotNull ResolvedType targetType, @NotNull ResolvedType expressionType) {
            return targetType.asReferenceType().getQualifiedName().equals(expressionType.asReferenceType().getQualifiedName());
        }

    }

}