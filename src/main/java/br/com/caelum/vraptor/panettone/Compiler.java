package br.com.caelum.vraptor.panettone;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class Compiler {

	private final File from;
	private final File to;
	private final Watcher watcher;
	private final List<String> imports;
	private final CompilationListener[] listeners;
	private final PrintStream out = System.out;
	private final PrintStream err = System.err;

	public Compiler(File from, File to) {
		this(from, to, new ArrayList<String>());
	}
	
	public Compiler(File from, File to, List<String> imports, CompilationListener... listeners) {
		this.from = from;
		this.to = to;
		this.imports = new ArrayList<>(imports);
		this.listeners = listeners;
		from.mkdirs();
		to.mkdirs();
		this.watcher = new Watcher(from.toPath(), this);
		File defaults = new File(from, "tone.defaults");
		if(defaults.exists()) {
			parse(defaults);
		}
	}

	private void parse(File defaults) {
		try {
			Files.lines(defaults.toPath())
				.filter(l -> l.startsWith("import "))
				.map(l -> l.substring("import ".length()).trim())
				.forEach(l -> this.imports.add(l));
		} catch (IOException e) {
			throw new RuntimeException("Unable to read defaults "+ defaults.getAbsolutePath());
		}
	}

	public List<Exception> compileAll() {
		return precompile();
	}

	private List<Exception> precompile() {
		List<File> files = tonesAt(from);
		long start = System.currentTimeMillis();
		out.println("Compiling " + files.size() + " files...");
		List<Exception> exceptions = files.stream()
			.map(this::compile)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(toList());
		long finish = System.currentTimeMillis();
		double delta = (finish - start) / 1000.0;
		if(!exceptions.isEmpty()) {
			err.println("Precompilation failed");
		}
		out.println(String.format("Finished in %.2f secs", delta));
		return exceptions;
	}

	public Optional<Exception> compile(File f) {
		try (FileReader reader = new FileReader(f)) {
			Template template = new Template(reader);
			String name = noExtension(nameFor(f));
			String content = template.renderType();
			CompiledTemplate compiled = new CompiledTemplate(to, name, imports, content, listeners);
			invokeOn(listeners, l-> l.finished(f, compiled));
			return Optional.empty();
		} catch (Exception e) {
			invokeOn(listeners, l -> l.finished(f, e));
			return Optional.of(e);
		}
	}

	private void invokeOn(CompilationListener[] listeners, Consumer<CompilationListener> listener) {
		stream(listeners).forEach(listener);
	}

	private String nameFor(File f) {
		String replaced = f.getAbsolutePath().replace(from.getAbsolutePath(), "");
		if(replaced.startsWith("/")) return replaced.substring(1);
		return replaced;
	}

	private List<File> tonesAt(File currentDir) {
		try {
			List<File> tones = Files.walk(currentDir.toPath(), 10)
					.map(Path::toFile)
					.filter(this::isTone)
					.collect(toList());
			return tones.stream().filter(File::isFile).collect(toList());
		} catch (IOException e) {
			throw new CompilationIOException(e);
		}
	}
	
	private boolean isTone(File p) {
		return p.isDirectory() ||  p.getName().endsWith(".tone") || p.getName().contains(".tone.");
	}

	private String noExtension(String name) {
		return name.replaceAll("\\.tone.*", "");
	}

	/**
	 * Watches the base directory for any file changes.
	 */
	public void startWatch() {
		Thread t = new Thread(watcher);
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Stops watching the base directory for file changes.
	 */
	public void stopWatch() {
		watcher.stop();
	}

	/**
	 * Compiles everything, throwing an exception if failing.
	 */
	public void compileAllOrError() {
		List<Exception> exceptions = compileAll();
		if(!exceptions.isEmpty()) throw new CompilationIOException(exceptions);
	}

	/**
	 * Removes the output folder and everything within it.
	 */
	public void clear() {
		out.println("Clearing compilation path...");
		invokeOn(listeners, l -> l.clear());
		clearChildren(to);
	}

	private void clearChildren(File current) {
		try {
			Files.walk(current.toPath()).map(Path::toFile).forEach(File::delete);
		} catch (IOException e) {
			err.println("Unable to clear folders: " + current.getAbsolutePath() + " due to " + e.getMessage());
		}
	}

	/**
	 * Removes the java version of a tone file. It will not remove any subclass that
	 * the tone user might have created.
	 */
	public void removeJavaVersionOf(String path) {
		int position = path.indexOf(VRaptorCompiler.VIEW_INPUT);
		path = path.substring(position + VRaptorCompiler.VIEW_INPUT.length() + 1);
		String java = "templates/" + path.replaceAll("\\.tone.*", "\\.java");
		new File(to, java).delete();
	}

}
