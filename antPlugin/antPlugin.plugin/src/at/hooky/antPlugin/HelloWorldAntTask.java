package at.hooky.antPlugin;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import at.hooky.dep.SomeInfo;

public class HelloWorldAntTask extends Task {

	
	@Override
	public void execute() throws BuildException {
		System.out.println("Hello World");
		System.out.println(new SomeInfo().toString());
	}
}
