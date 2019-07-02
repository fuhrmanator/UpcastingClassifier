import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DotPrinter;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class ASTVisualizer {

    private static final String FILE_PATH = "C:/path/to/java/file.java";

    // See https://javaparser.org/inspecting-an-ast/
    public static void main(String[] args) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(new File(FILE_PATH));
        DotPrinter printer = new DotPrinter(true);
        try (FileWriter fileWriter = new FileWriter("ast.dot");
             PrintWriter printWriter = new PrintWriter(fileWriter)) {
            printWriter.print(printer.output(cu));
        }
//        YamlPrinter printer = new YamlPrinter(true);
//        System.out.println(printer.output(cu));
    }
}
