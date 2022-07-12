# Test Project for a Tycho-Maven-Runner-Plugin

This is only a test project to find either an bug or an coding error for our maven plugin.

## How-to

Build the tycho projcet `antPlugin` and the maven plugin `mavenMojo` with the root `pom.xml`.

Then run `mvn clean install -f testMavenMojoAntPlugin/pom.xml` for the Exception:

```Caused by: org.eclipse.tycho.core.osgitools.OsgiManifestParserException: Exception parsing OSGi MANIFEST /home/dhooker/.m2/repository/at/hooky/antPlugin.dependency/1.0.0-SNAPSHOT/antPlugin.dependency-1.0.0-SNAPSHOT.eclipse-plugin/META-INF/MANIFEST.MF: Manifest file not found
```
