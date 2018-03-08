package pgo.model.tla;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class PGoTLABackwardsCompatibilityASTConverter extends PGoTLAExpressionVisitor<PGoTLAExpression> {
	public PGoTLABackwardsCompatibilityASTConverter() {}
	
	@Override
	public PGoTLAExpression visit(PGoTLABinOp expr) {
		String op = expr.getOperation();
		
		switch(op) {
			case "+":
			case "-":
			case "*":
			case "/":
			case "\\div":
			case "%":
			case "^":
				return new PGoTLASimpleArithmetic(
						op,
						expr.getLHS().walk(this),
						expr.getRHS().walk(this),
						expr.getLine());
			case "/\\":
			case "\\land":
			case "\\lor":
			case "\\/":
			case "~":
			case "\\lnot":
			case "\\neg":
			case "#":
			case "/=":
			case "<":
			case ">":
			case "<=":
			case "=<":
			case "\\leq":
			case ">=":
			case "\\geq":
			case "==":
			case "=":
				return new PGoTLABoolOp(
						op,
						expr.getLHS().walk(this),
						expr.getLHS().walk(this),
						expr.getLine());
			case "..":
				return new PGoTLASequence(
						expr.getLHS().walk(this),
						expr.getRHS().walk(this),
						expr.getLine());
			case "\\cup":
			case "\\union":
			case "\\cap":
			case "\\intersect":
			case "\\in":
			case "\\notin":
			case "\\subseteq":
			case "\\":
				return new PGoTLASetOp(op,
						expr.getLHS().walk(this),
						expr.getRHS().walk(this),
						expr.getLine());
			default:
				throw new RuntimeException("unimplemented binop conversion for operator "+op);
		}
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLAVariable expr) {
		return expr;
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLATuple expr) {
		List<PGoTLAExpression> items = new ArrayList<>();
		for(PGoTLAExpression e: expr.getItems()) {
			items.add(e.walk(this));
		}
		return new PGoTLAArray(items, expr.getLine());
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLANumber expr) {
		return expr;
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLAExpression.PGoTLADefault expr) {
		return expr;
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLAOperatorCall expr) {
		// this is semantically wrong, but is understood as such
		// but the rest of the pipeline
		// TODO: fix this
		List<PGoTLAExpression> args = new ArrayList<>();
		for(PGoTLAExpression a : expr.getArgs()) {
			args.add(a.walk(this));
		}
		return new PGoTLAFunctionCall(
				new PGoTLAVariable(expr.getName(), expr.getLine()),
				args,
				expr.getLine());
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLASetRefinement expr) {
		Vector<PGoTLAExpression> left = new Vector<>();
		left.add(new PGoTLASetOp("\\in",
				expr.getIdent().toExpression().walk(this),
				expr.getFrom(),
				expr.getLine()));
		
		return new PGoTLAVariadic(":", left, expr.getWhen().walk(this), false, expr.getLine());
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLASetComprehension expr) {
		Vector<PGoTLAExpression> right = new Vector<>();
		for(PGoTLAQuantifierBound b : expr.getBounds()) {
			right.add(new PGoTLASetOp("\\in",
					// the .get(0) here corresponds to the downstream
					// code's inability to handle cases with multiple
					// variables or tuples
					b.getIds().get(0).toExpression().walk(this),
					b.getSet().walk(this),
					expr.getLine()));
		}
		
		return new PGoTLAVariadic(":", right, expr.getBody().walk(this), true, expr.getLine());
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLAString expr) {
		return expr;
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLAUnary expr) {
		return new PGoTLAUnary(expr.getToken(), expr.getArg().walk(this), expr.getLine());
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLAExistential expr) {
		Vector<PGoTLAExpression> vars = new Vector<>();
		for(PGoTLAQuantifierBound b: expr.getIds()) {
			vars.add(new PGoTLASetOp("\\in",
					b.getIds().get(0).toExpression().walk(this),
					b.getSet().walk(this),
					expr.getLine()));
		}
		
		return new PGoTLAUnary("\\E",
				new PGoTLAVariadic(":",
						vars,
						expr.getBody().walk(this),
						false,
						expr.getLine()),
				expr.getLine());
	}
	
	@Override
	public PGoTLAExpression visit(PGoTLASet expr) {
		List<PGoTLAExpression> contents = new ArrayList<>();
		for(PGoTLAExpression e : expr.getContents()) {
			contents.add(e.walk(this));
		}
		return new PGoTLASet(contents, expr.getLine());
	}
}
