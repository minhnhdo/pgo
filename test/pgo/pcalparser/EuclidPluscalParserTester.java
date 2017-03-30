package pgo.pcalparser;

import java.util.Vector;

/**
 * Tester class for the Euclid pluscal algorithm
 * 
 * This class stores the annotations, exceptions if any, and ast that is
 * expected.
 *
 */
public class EuclidPluscalParserTester extends PGoPluscalParserTesterBase {

	@Override
	public Vector<String> getAnnotations() {
		Vector<String> v = new Vector<String>();
		v.add("arg int N");
		v.add("var int u");
		return v;
	}

	@Override
	public boolean expectException() {
		return false;
	}

	@Override
	protected String getAlg() {
		return "Euclid";
	}

	@Override
	public int exceptionLine() {
		return 0;
	}

}
