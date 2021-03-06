package at.hooky.mavenMojo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.sisu.equinox.EquinoxServiceFactory;
import org.eclipse.sisu.equinox.launching.EquinoxInstallationFactory;
import org.eclipse.sisu.equinox.launching.EquinoxLauncher;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.maven.ToolchainProvider;
import org.eclipse.tycho.extras.eclipserun.EclipseRunMojo;
import org.eclipse.tycho.plugins.p2.extras.Repository;

/**
 * It adds - at the beginning of each line
 */
@Mojo(name = "hooky-build", defaultPhase = LifecyclePhase.COMPILE)
public class BuildMojo extends AbstractMojo {

	private boolean addDefaultDependencies = true;
	@Parameter(property = "faktorips.executionEnvironment")
	private String executionEnvironment;
	@Parameter(property = "faktorips.skip", defaultValue = "false")
	private boolean skip;
	private List<Dependency> dependencies = new ArrayList<>();
	@Parameter
	private List<Dependency> additionalPlugins = new ArrayList<>();
	@Parameter
	private List<String> jvmArgs = new ArrayList<>();
	@Parameter(property = "maven.repo.local")
	private String localRepository;
	private List<String> applicationsArgs = new ArrayList<>();
	@Parameter
	private List<Repository> repositories = new ArrayList<>();
	@Parameter
	private List<Repository> additionalRepositories = new ArrayList<>();
	private int forkedProcessTimeoutInSeconds;
	private Map<String, String> environmentVariables = Collections.emptyMap();
	@Parameter
	private File work;
	@Parameter(property = "session", readonly = true, required = true)
	private MavenSession session;
	@Parameter(property = "ant.script")
	private String antScriptPath;
	@Parameter(property = "ant.target", defaultValue = "import")
	private String antTarget;
	@Parameter(defaultValue = "false")
	private boolean exportHtml;
	@Parameter(property = "jdk.dir")
	private String jdkDir;
	@Parameter(property = "jdk.id")
	private String jdkId;
	@Parameter(defaultValue = "true")
	private boolean importAsMavenProject;
	@Parameter(property = "faktorips.repository.version")
	private String fipsRepositoryVersion;
	@Parameter(property = "repository.fips", defaultValue = "https://update.faktorzehn.org/faktorips/${faktorips.repository.version}/")
	private String fipsRepository;
	@Parameter(property = "repository.eclipse", defaultValue = "https://download.eclipse.org/eclipse/updates/4.11/")
	private String eclipseRepository;
	@Parameter(property = "faktorips.debug")
	private boolean debug;
	@Parameter(property = "faktorips.debug.port", defaultValue = "8000")
	private int debugPort;
	@Component
	private MavenProject project;
	@Component
	private EquinoxInstallationFactory installationFactory;
	@Component
	private EquinoxLauncher launcher;
	@Component
	private ToolchainProvider toolchainProvider;
	@Component
	private EquinoxServiceFactory equinox;
	@Component
	private Logger logger;
	@Component
	private ToolchainManager toolchainManager;
	@Component
	private PluginDescriptor pluginDescriptor;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		boolean clearWorkspaceBeforeLaunch = false;

		work = createTempWorkspace();

		addDependencies();

		addRepositories();

		// default values for parameter applicationArgs
		applicationsArgs.add("-consoleLog");
		applicationsArgs.add("-application");
		applicationsArgs.add("org.eclipse.ant.core.antRunner");
		applicationsArgs.add("-buildfile");
		applicationsArgs.add(getPathToAntScript());
		applicationsArgs.add(antTarget);
		// default values for parameter jvmArgs
		jvmArgs.add("-Xmx1024m");
		jvmArgs.add("-XX:+HeapDumpOnOutOfMemoryError");
		jvmArgs.add("-DjavacFailOnError=true");

		jvmArgs.add("-DprojectName=" + getProjectName());
		jvmArgs.add("-Dsourcedir=" + project.getBasedir().getAbsolutePath());

		configureDebug();
		if (StringUtils.isBlank(executionEnvironment)) {
			executionEnvironment = "JavaSE-" + Runtime.version().feature();
		}

		EclipseRunMojo eclipseRunMojo = null;
		eclipseRunMojo = new EclipseRunMojo(work, clearWorkspaceBeforeLaunch, project, dependencies,
				addDefaultDependencies, executionEnvironment, repositories, session, jvmArgs, skip, applicationsArgs,
				forkedProcessTimeoutInSeconds, environmentVariables, installationFactory, launcher, toolchainProvider,
				equinox, logger, toolchainManager);

		removeIncompatibleTychoProjectsFromContextCache();

		eclipseRunMojo.execute();

	}

	private String getProjectName() {
		if (importAsMavenProject) {
			return project.getArtifactId();
		}
		return project.getBasedir().getName();
	}

	private String getPathToAntScript() {
		try {
			Path path = Paths.get(project.getBuild().getDirectory() + "/importProjects.xml").toAbsolutePath();
			Files.deleteIfExists(path);
			FileUtils.forceMkdir(new File(project.getBuild().getDirectory()));
			List<String> lines = new ArrayList<>();
			lines.add("<project name=\"importProjects\" default=\"import\">");

			lines.add("  <taskdef name=\"hooky.build\" classname=\"at.hooky.antPlugin.HelloWorldAntTask\" />");

			lines.add("  <target name=\"import\">");
			lines.add("    <echo message=\"Building ${projectName}\" />");

			lines.add("    <hooky.build />");

			lines.add("  </target>");
			lines.add("</project>");
			Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			antScriptPath = path.toString();
		} catch (IOException e) {
			getLog().error("Can't create ant script in " + antScriptPath, e);
		}
		return antScriptPath;
	}

	private void configureDebug() {
		if (debug) {
			jvmArgs.add("-Xdebug");
			jvmArgs.add("-Xnoagent");
			jvmArgs.add("-Xrunjdwp:transport=dt_socket,address=" + debugPort + ",server=y,suspend=y");
		}
	}

	private void addRepository(String url) {

		URI uri;
		if (!url.startsWith("http")) {
			uri = new File(url).toURI();
		} else {
			uri = URI.create(url);
		}
		Repository repo = new Repository(uri);
		repo.setLayout("p2");
		repositories.add(repo);
	}

	private void addDependency(String artifactId) {
		Dependency dependency = new Dependency();
		dependency.setArtifactId(artifactId);
		dependency.setType("eclipse-plugin");
		dependencies.add(dependency);
	}

	private void addRepositories() {
		// add default repositories if no repositories are specified in the pom.xml
		if (repositories.isEmpty()) {
			addRepository(eclipseRepository);
			addRepository(fipsRepository);
		}
		repositories.addAll(additionalRepositories);
	}

	private void addDependencies() {
		// default values for parameter dependencies
		addDependency("antPlugin.plugin");

//		if (importAsMavenProject) {
//			addDependency("org.eclipse.m2e.core");
//			addDependency("org.eclipse.m2e.maven.runtime");
//		}
		dependencies.addAll(additionalPlugins);
	}

	private File createTempWorkspace() {
		int leftLimit = 97; // letter 'a'
		int rightLimit = 122; // letter 'z'
		int targetStringLength = 10;
		Random random = new Random();

		String generatedString = random.ints(leftLimit, rightLimit + 1).limit(targetStringLength)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();

		return Path.of(System.getProperty("java.io.tmpdir"), generatedString).toFile();
	}

	private void removeIncompatibleTychoProjectsFromContextCache() {
		/**
		 * org.eclipse.tycho.core.osgitools.DefaultReactorProject.CTX_REACTOR_PROJECT
		 */
		String tychoReactorProject = "tycho.reactor-project";
		for (MavenProject mavenProject : session.getProjects()) {
			Object reactorProject = mavenProject.getContextValue(tychoReactorProject);
			if (!(reactorProject instanceof ReactorProject)) {
				mavenProject.setContextValue(tychoReactorProject, null);
			}
		}
	}

}
