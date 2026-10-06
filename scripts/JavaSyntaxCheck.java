import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import javax.tools.*;
import com.sun.source.util.JavacTask;

/** Parse-only check, NOT type checking, NOT a Forge compile. No API stubs. */
public final class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        List<File> files=new ArrayList<File>();
        for(String root:args)try(Stream<Path> paths=Files.walk(Paths.get(root))) {
            paths.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->files.add(p.toFile()));
        }
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        if(compiler==null)throw new IllegalStateException("JDK compiler required");
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,Locale.ROOT,java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,Arrays.asList("-proc:none","-source","8"),null,manager.getJavaFileObjectsFromFiles(files));
            task.parse();
            for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException(d.toString());
        }
        System.out.println("PARSE PASS: "+files.size()+" Java files; Java 8 syntax. No external API type/link verification performed.");
    }
}
