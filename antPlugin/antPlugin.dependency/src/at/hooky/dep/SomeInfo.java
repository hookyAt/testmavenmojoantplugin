package at.hooky.dep;

public class SomeInfo {
	
	@Override
	public String toString() {
		return new StringBuilder()
				.append(System.getProperties())
				.toString();
	}
}
