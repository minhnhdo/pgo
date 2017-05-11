package pgo.model.golang;

import java.util.Vector;

/**
 * Represents a parenthesized expression.
 * 
 * @author Brandon Zhang
 *
 */
public class Group extends Expression {
	private Expression inside;
	
	public Group(Expression inside) {
		this.inside = inside;
	}
	
	@Override
	public Vector<String> toGo() {
		Vector<String> ret = inside.toGo();
		ret.set(0, "(" + ret.get(0));
		ret.set(ret.size()-1, ret.get(ret.size()-1) + ")");
		return ret;
	}

}