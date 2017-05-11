package pgo.trans.intermediate;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Vector;
import java.util.logging.Logger;

import pcal.AST;
import pcal.AST.Assert;
import pcal.AST.Assign;
import pcal.AST.Call;
import pcal.AST.CallReturn;
import pcal.AST.Clause;
import pcal.AST.Either;
import pcal.AST.Goto;
import pcal.AST.If;
import pcal.AST.LabelEither;
import pcal.AST.LabelIf;
import pcal.AST.LabeledStmt;
import pcal.AST.MacroCall;
import pcal.AST.PrintS;
import pcal.AST.Return;
import pcal.AST.SingleAssign;
import pcal.AST.Skip;
import pcal.AST.When;
import pcal.AST.While;
import pcal.AST.With;
import pgo.model.golang.Comment;
import pgo.model.golang.Expression;
import pgo.model.golang.For;
import pgo.model.golang.Function;
import pgo.model.golang.FunctionCall;
import pgo.model.golang.GoProgram;
import pgo.model.golang.Group;
import pgo.model.golang.ParameterDeclaration;
import pgo.model.golang.SimpleExpression;
import pgo.model.golang.Statement;
import pgo.model.golang.Token;
import pgo.model.golang.VariableDeclaration;
import pgo.model.intermediate.PGoFunction;
import pgo.model.intermediate.PGoPrimitiveType;
import pgo.model.intermediate.PGoRoutineInit;
import pgo.model.intermediate.PGoVariable;
import pgo.model.tla.*;
import pgo.parser.PGoParseException;
import pgo.parser.TLAExprParser;
import pgo.trans.PGoTransException;
import pgo.util.PcalASTUtil;

/**
 * The last stage of the translation. Takes given intermediate data and converts it to a Go AST, properly
 *
 */
public class PGoTransStageGoGen extends PGoTransStageBase {

	// the ast
	private GoProgram go;

	// the main block pointer
	private Vector<Statement> main;

	public PGoTransStageGoGen(PGoTransStageAtomicity s1) throws PGoParseException, PGoTransException {
		super(s1);

		go = new GoProgram("main");

		main = go.getMain().getBody();

		generateArgParsing();
		generateGlobalVariables();
		generateFunctions();
		generateMain();
	}

	public GoProgram getGo() {
		return go;
	}

	private void generateMain() throws PGoTransException {
		Logger.getGlobal().info("Generating Main Function");

		if (this.intermediateData.isMultiProcess) {
			main.addAll(convertGoRoutinesToGo(this.intermediateData.goroutines.values()));
		} else {
			Vector block = this.intermediateData.mainBlock;
			Vector<Statement> stmts = convertStatementToGo(block);
			main.addAll(stmts);
		}
	}

	private void generateArgParsing() throws PGoTransException {
		int argN = 0;
		boolean hasArg = false;

		Vector<Statement> positionalArgs = new Vector<Statement>();
		for (PGoVariable pv : this.intermediateData.globals.values()) {
			// Add flags as necessary
			if (pv.getArgInfo() != null) {
				Logger.getGlobal().info("Generating command line argument code for variable \"" + pv.getName() + "\"");
				go.getImports().addImport("flag");
				hasArg = true;
				if (pv.getArgInfo().isPositionalArg()) {
					addPositionalArgToMain(argN, positionalArgs, pv);

					argN++;
				} else {
					addFlagArgToMain(pv);
				}

			}
		}

		if (hasArg) {
			main.add(new FunctionCall("flag.Parse", new Vector<Expression>()));
			main.addAll(positionalArgs);
			main.add(new Token(""));
		}
	}

	/**
	 * Converts a given code black into Go code. Given code block should not have function definitions and such in pluscal
	 * 
	 * @param stmts
	 * @return
	 * @throws PGoTransException
	 */
	private Vector<Statement> convertStatementToGo(Vector<AST> stmts) throws PGoTransException {
		return new PcalASTUtil.Walker<Vector<Statement>>() {

			@Override
			protected void init() {
				result = new Vector<Statement>();
			}

			protected void visit(LabeledStmt ls) throws PGoTransException {
				// result.add(new Label(ls.label)); TODO add cleanup for
				// non-used labels. Go compile fails on non-used labels. So scan
				// through for gotos and remove all labels that isnt used. Or
				// somehow keep track of which labels are used in preprocessing
				super.visit(ls);
			}

			protected void visit(While w) throws PGoTransException {
				Vector<Statement> cond = tlaTokenToStatement(new TLAExprParser(w.test, w.line).getResult());
				// TODO handle complicated conditions
				assert (cond.size() > 0);
				Vector<Expression> exps = new Vector<Expression>();
				for (Statement s : cond) {
					exps.add((Expression) s);
				}
				Expression se = new SimpleExpression(exps);

				Vector<Statement> loopBody = new Vector<Statement>();

				// Store the result so far temporarily
				Vector<Statement> tempRes = result;
				result = loopBody; // we send the loop body to be filled
				super.visit(w); // visit the loop body
				For loopAst = new For(se, result);
				result = tempRes;
				result.add(loopAst);
			}

			protected void visit(Assign as) throws PGoTransException {
				if (as.ass.size() > 1) {
					// pluscal semantics:
					// u = v || v = u is equivalent to setting new_u and new_v
					Vector<Expression> exps = new Vector<Expression>();
					boolean firstAssign = true; // whether we need to prepend var w/ comma
					for (SingleAssign sa : (Vector<SingleAssign>) as.ass) {
						exps.add(new Token((firstAssign ? "" : ", ") + sa.lhs.var));
						firstAssign = false;
					}
					exps.add(new Token(" = "));
					firstAssign = true;
					for (SingleAssign sa : (Vector<SingleAssign>) as.ass) {
						Vector<Statement> rhs = tlaTokenToStatement(new TLAExprParser(sa.rhs, sa.line).getResult());
						assert (rhs.size() == 1);
						if (!firstAssign) {
							exps.add(new Token(", "));
						}
						exps.add((Expression) rhs.remove(0)); // TODO check if cast is
						firstAssign = false;
					}
					result.add(new SimpleExpression(exps));
				} else {
					// only one assign, just treat it like a single assignment
					// without temp variables
					walk(as.ass);
				}
			}

			protected void visit(SingleAssign sa) throws PGoTransException {
				Vector<Expression> exps = new Vector<Expression>();
				exps.add(new Token(sa.lhs.var));
				// TODO parse sub for [2] etc
				exps.add(new Token(" = "));
				// TODO this is tlaexpr exps.add(sa.rhs);
				Vector<Statement> rhs = tlaTokenToStatement(new TLAExprParser(sa.rhs, sa.line).getResult());
				assert (rhs.size() > 0);
				exps.add((Expression) rhs.remove(0)); // TODO check if cast is

				result.add(new SimpleExpression(exps));
				result.addAll(rhs);

			}

			protected void visit(If ifast) throws PGoTransException {
				Vector<Statement> cond = tlaTokenToStatement(new TLAExprParser(ifast.test, ifast.line).getResult());
				// TODO handle complicated conditions
				assert (cond.size() > 0);
				Vector<Expression> exps = new Vector<Expression>();
				for (Statement s : cond) {
					exps.add((Expression) s);
				}
				Expression se = new SimpleExpression(exps);

				Vector<Statement> thenS = new Vector<Statement>();
				Vector<Statement> elseS = new Vector<Statement>();

				// Store the result so far temporarily
				Vector<Statement> tempRes = result;
				result = thenS; // we send the loop body to be filled
				walk(ifast.Then);
				result = elseS;
				walk(ifast.Else);

				pgo.model.golang.If ifAst = new pgo.model.golang.If(se, thenS, elseS);

				result = tempRes;
				result.add(ifAst);
			}

			protected void visit(Either ei) {
				for (Vector v : (Vector<Vector>) ei.ors) {
					// either has vector of vectors
					// walk(v);
					// TODO handle either
				}
			}

			protected void visit(With with) {
				// TODO handle
				// Select a random element of with.exp and perform with.Do on it
				go.getImports().addImport("math/rand");
				Vector<Statement> pre; // handle random selection and declaration of var
				// walk(with.Do);
			}

			protected void visit(When when) {
				// TODO handle await
			}

			protected void visit(PrintS ps) throws PGoTransException {
				// TODO parse ps.exp
				Vector<PGoTLA> argExp = new TLAExprParser(ps.exp, ps.line).getResult();
				assert (argExp.size() == 1); // print should only have 1
												// argument as a tupple
				assert (argExp.get(0) instanceof PGoTLAArray); // convert tupple
																// to array

				PGoTLAArray tup = (PGoTLAArray) argExp.get(0);
				Vector<String> strfmt = new Vector<String>();
				Vector<Expression> args = new Vector<Expression>();
				for (PGoTLA arg : tup.getContents()) {
					if (arg instanceof PGoTLAString) {
						strfmt.add(((PGoTLAString) arg).getString());
					} else if (arg instanceof PGoTLANumber) {
						strfmt.add(((PGoTLANumber) arg).getVal());
					} else if (arg instanceof PGoTLABool) {
						strfmt.add(String.valueOf(((PGoTLABool) arg).getVal()));
					} else if (arg instanceof PGoTLAVariable) {
						strfmt.add("%v"); // use go default printing
						args.add(new Token(((PGoTLAVariable) arg).getName()));
					} else {
						// TODO handle printing sets/arrays/maps if its legal in
						// plscal
					}
				}

				args.add(0, new Token("\"" + String.join(" ", strfmt) + "\\n\""));

				go.getImports().addImport("fmt");
				FunctionCall fc = new FunctionCall("fmt.Printf", args);
				result.add(fc);
			}

			protected void visit(Assert as) {
				// TODO actually do we even want this?
			}

			protected void visit(Skip s) {
				Vector<String> cmt = new Vector<String>();
				cmt.add("TODO skipped from pluscal");
				result.add(new Comment(cmt, false));
			}

			protected void visit(LabelIf lif) throws PGoTransException {
				// TODO w.test is the condition
				Vector<Statement> cond = tlaTokenToStatement(new TLAExprParser(lif.test, lif.line).getResult());
				// TODO handle complicated conditions
				assert (cond.size() > 0);
				Vector<Expression> exps = new Vector<Expression>();
				exps.add((Expression) cond.get(0));
				Expression se = new SimpleExpression(exps);

				Vector<Statement> thenS = new Vector<Statement>();
				Vector<Statement> elseS = new Vector<Statement>();

				// Store the result so far temporarily
				Vector<Statement> tempRes = result;
				result = thenS; // we send the loop body to be filled
				walk(lif.unlabThen);
				walk(lif.labThen);
				result = elseS;
				walk(lif.unlabElse);
				walk(lif.labElse);

				pgo.model.golang.If ifAst = new pgo.model.golang.If(se, thenS, elseS);

				result = tempRes;
				result.add(ifAst);
			}

			protected void visit(LabelEither le) {
				// TODO
				// walk(le.clauses);
			}

			protected void visit(Clause c) {
				// TODO handle with either
				// walk(c.unlabOr);
				// walk(c.labOr);
			}

			protected void visit(Call c) {
				// TODO c.args is vector of tlaExpr
				Vector<Expression> args = new Vector<Expression>();
				FunctionCall fc = new FunctionCall(c.to, args);
				result.add(fc);
			}

			protected void visit(Return r) {
				// TODO learn to get the return variable
				result.add(new pgo.model.golang.Return(null));
			}

			protected void visit(CallReturn cr) {
				// TODO c.args is vector of tlaExpr
				Vector<Expression> args = new Vector<Expression>();
				FunctionCall fc = new FunctionCall(cr.to, args);
				result.add(fc);
			}

			protected void visit(Goto g) {
				result.add(new pgo.model.golang.GoTo(g.to));
			}

			protected void visit(MacroCall m) {
				// TODO
			}
		}.getResult(stmts);
	}

	/**
	 * Generate the go routines init blocks as statements
	 * 
	 * @param goroutines
	 */
	private Vector<Statement> convertGoRoutinesToGo(Collection<PGoRoutineInit> goroutines) {
		return null;
		// TODO Auto-generated method stub

	}

	/**
	 * Create declarations for all functions
	 * 
	 * @throws PGoTransException
	 */
	private void generateFunctions() throws PGoTransException {
		for (PGoFunction pf : this.intermediateData.funcs.values()) {

			Vector<ParameterDeclaration> params = new Vector<ParameterDeclaration>();
			for (PGoVariable param : pf.getParams()) {
				params.add(new ParameterDeclaration(param.getName(), param.getType()));
			}
			Vector<VariableDeclaration> locals = new Vector<VariableDeclaration>();
			for (PGoVariable local : pf.getVariables()) {
				SimpleExpression e = null; // TODO from tla
				Vector<Statement> init = new Vector<Statement>(); // TODO from
																	// tla
				locals.add(new VariableDeclaration(local.getName(), local.getType(), e, init, local.getIsConstant()));
			}

			Vector<Statement> body = new Vector<Statement>();
			body.addAll(convertStatementToGo(pf.getBody()));

			Function f = new Function(pf.getName(), pf.getReturnType(), params, locals, body);

			go.addFunction(f);
		}
	}

	/**
	 * Generate the code for global variables
	 * 
	 * @throws PGoTransException
	 */
	private void generateGlobalVariables() throws PGoTransException {
		// we delay initialization once we hit a variable with \in, in case
		// other variable refer to it. We also want to reset the other values to
		// the initial value. Constants will still be generated at the time
		boolean delay = false;
		for (PGoVariable pv : this.intermediateData.globals.values()) {
			if (pv.getIsSimpleAssignInit()) {
				continue;
			}

			delay = true;

			// this is var \in collection

			Logger.getGlobal().info("Generating go variable \"" + pv.getName() + "\" with loop");

			// being part of the rhs, the parsed result should just be
			// one coherent expression
			TLAExprParser parser = new TLAExprParser(pv.getPcalInitBlock(), pv.getLine());
			Vector<PGoTLA> ptla = parser.getResult();
			assert (ptla.size() == 1);
			Vector<Statement> stmt = tlaTokenToStatement(ptla);
			SimpleExpression se = null;
			if (stmt.size() == 1) {
				if (stmt.get(0) instanceof SimpleExpression) {
					se = (SimpleExpression) stmt.remove(0);
				} else {
					se = new SimpleExpression(new Vector<Expression>() {
						{
							add((Expression) stmt.remove(0));
						}
					});
				}

			} // TODO handle other cases where need more than 1 statement
			go.addGlobal(
					new VariableDeclaration(pv.getName(), pv.getType(), null, new Vector<Statement>(), pv.getIsConstant()));
			Vector<Expression> toks = new Vector<Expression>();
			toks.add(new Token("_, " + pv.getName()));
			toks.add(new Token(" = "));
			toks.add(new Token("range "));
			toks.add(se); // TODO what if not a single expression
			SimpleExpression exp = new SimpleExpression(toks);

			For loop = new For(exp, new Vector<Statement>());
			main.add(loop);
			main = loop.getThen();
			// we set the rest of the main to go in here
		}

		for (PGoVariable pv : this.intermediateData.globals.values()) {
			if (!pv.getIsSimpleAssignInit()) {
				// already did var \in set
				continue;
			}
			Logger.getGlobal().info("Generating go variable \"" + pv.getName() + "\"");

			if (!pv.getGoVal().isEmpty()) {
				// If we have a specifieVector<E>ang value for the variable,
				// use it
				Vector<Expression> exp = new Vector<Expression>();
				exp.add(new Token(pv.getGoVal()));
				SimpleExpression s = new SimpleExpression(exp);

				go.addGlobal(new VariableDeclaration(pv.getName(), pv.getType(), s, pv.getIsConstant()));
				continue;
			} else if (pv.getArgInfo() != null) {
				go.addGlobal(new VariableDeclaration(pv.getName(), pv.getType(), null, pv.getIsConstant()));
				// generateMain will fill the main function
			} else {
				// being part of the rhs, the parsed result should just be
				// one coherent expression
				TLAExprParser parser = new TLAExprParser(pv.getPcalInitBlock(), pv.getLine());
				Vector<PGoTLA> ptla = parser.getResult();
				assert (ptla.size() == 1);
				Vector<Statement> stmt = tlaTokenToStatement(ptla);
				SimpleExpression se = null;
				if (stmt.size() == 1) {
					if (stmt.get(0) instanceof SimpleExpression) {
						se = (SimpleExpression) stmt.remove(0);
					} else {
						se = new SimpleExpression(new Vector<Expression>() {
							{
								add((Expression) stmt.remove(0));
							}
						});
					}

				}
				if (pv.getIsConstant() || !delay) {
					go.addGlobal(new VariableDeclaration(pv.getName(), pv.getType(), se, new Vector<Statement>(),
							pv.getIsConstant()));

					if (stmt.size() > 0) {
						// complex initializations go into main
						main.addAll(stmt);
					}
				} else {
					go.addGlobal(new VariableDeclaration(pv.getName(), pv.getType(), null, new Vector<Statement>(),
							pv.getIsConstant()));
					Vector<Expression> toks = new Vector<Expression>();
					toks.add(new Token(pv.getName()));
					toks.add(new Token(" = "));
					toks.add(se);
					main.add(new SimpleExpression(toks));
				}
			}

		}
	}

	/**
	 * Takes PGoTLA ast tree and converts it to Go statement
	 * 
	 * TODO probably want to take the tokens into a class TLAExprToGo. Then support things like getEquivStatement() to get the
	 * equivalent go expr to refer to the equivalent data in the pluscal, and getInit() to get any initialization code to
	 * generate that data. Constructor of this class may need to know what local variable names are available
	 * 
	 * @param ptla
	 * @return
	 */
	private Vector<Statement> tlaTokenToStatement(Vector<PGoTLA> ptla) {
		Vector<Statement> stmts = new Vector<Statement>();
		for (PGoTLA tla : ptla) {
			stmts.addAll(tlaTokenToStatement(tla));
		}

		return stmts;
	}

	private Vector<Statement> tlaTokenToStatement(PGoTLA tla) {
		Vector<Statement> stmts = new Vector<Statement>();
		if (tla instanceof PGoTLAString) {
			stmts.add(new Token((((PGoTLAString) tla).getString())));
		} else if (tla instanceof PGoTLANumber) {
			stmts.add(new Token((((PGoTLANumber) tla).getVal())));
		} else if (tla instanceof PGoTLABool) {
			stmts.add(new Token(String.valueOf((((PGoTLABool) tla).getVal()))));
		} else if (tla instanceof PGoTLAVariable) {
			stmts.add(new Token(String.valueOf((((PGoTLAVariable) tla).getName()))));
		} else if (tla instanceof PGoTLASimpleArithmetic) {
			Vector<Statement> leftRes = tlaTokenToStatement(((PGoTLASimpleArithmetic) tla).getLeft());
			Vector<Statement> rightRes = tlaTokenToStatement(((PGoTLASimpleArithmetic) tla).getRight());

			// arithmetic operations should just be a single SimpleExpression
			assert (leftRes.size() == 1);
			assert (rightRes.size() == 1);
			assert (leftRes.get(0) instanceof Expression);
			assert (rightRes.get(0) instanceof Expression);

			if (((PGoTLASimpleArithmetic) tla).getToken().equals("^")) {
				// TODO we need to check which number type we are using and cast to float64 if needed
				go.getImports().addImport("math");
				Vector<Expression> params = new Vector<>();
				params.add((Expression) leftRes.get(0));
				params.add((Expression) rightRes.get(0));
				FunctionCall fc = new FunctionCall("math.Pow", params);
				stmts.add(fc);
			} else {
				Vector<Expression> toks = new Vector<Expression>();
				toks.add((Expression) leftRes.get(0));

				toks.add(new Token(" " + ((PGoTLASimpleArithmetic) tla).getToken() + " "));
				toks.add((Expression) rightRes.get(0));

				SimpleExpression arith = new SimpleExpression(toks);

				stmts.add(arith);
			}
		} else if (tla instanceof PGoTLABoolOp) {
			// TODO we need to see whether we are operating on sets
			Vector<Statement> leftRes = tlaTokenToStatement(((PGoTLABoolOp) tla).getLeft());
			Vector<Statement> rightRes = tlaTokenToStatement(((PGoTLABoolOp) tla).getRight());

			// comparators operations should just be a single SimpleExpression
			assert (leftRes.size() == 1);
			assert (rightRes.size() == 1);
			assert (leftRes.get(0) instanceof Expression);
			assert (rightRes.get(0) instanceof Expression);

			Vector<Expression> toks = new Vector<Expression>();
			toks.add((Expression) leftRes.get(0));
			toks.add(new Token(" " + ((PGoTLABoolOp) tla).getToken() + " "));
			toks.add((Expression) rightRes.get(0));

			SimpleExpression comp = new SimpleExpression(toks);

			stmts.add(comp);
		} else if (tla instanceof PGoTLASequence) {
			Vector<Statement> startRes = tlaTokenToStatement(((PGoTLASequence) tla).getStart());
			Vector<Statement> endRes = tlaTokenToStatement(((PGoTLASequence) tla).getEnd());

			// comparators operations should just be a single Expression
			assert (startRes.size() == 1);
			assert (endRes.size() == 1);
			assert (startRes.get(0) instanceof Expression);
			assert (endRes.get(0) instanceof Expression);

			Vector<Expression> args = new Vector<Expression>();
			args.add((Expression) startRes.get(0));
			args.add((Expression) endRes.get(0));

			go.getImports().addImport("pgoutil");
			FunctionCall fc = new FunctionCall("pgoutil.Sequence", args);
			stmts.add(fc);
		} else if (tla instanceof PGoTLASet) {
			Vector<Statement> contents = tlaTokenToStatement(((PGoTLASet) tla).getContents());

			Vector<Expression> args = new Vector<>();
			for (Statement s : contents) {
				assert (s instanceof Expression);
				args.add((Expression) s);
			}

			go.getImports().addImport("mapset");
			FunctionCall fc = new FunctionCall("mapset.NewSet", args);
			stmts.addElement(fc);
		} else if (tla instanceof PGoTLASetOp) {
			Vector<Statement> leftRes = tlaTokenToStatement(((PGoTLASetOp) tla).getLeft());
			Vector<Statement> rightRes = tlaTokenToStatement(((PGoTLASetOp) tla).getRight());

			// lhs and rhs should each be a single Expression
			assert (leftRes.size() == 1);
			assert (rightRes.size() == 1);
			assert (leftRes.get(0) instanceof Expression);
			assert (rightRes.get(0) instanceof Expression);

			Vector<Expression> lhs = new Vector<>();
			lhs.add((Expression) leftRes.get(0));
			Expression rightSet = (Expression) rightRes.get(0);

			Vector<Expression> exp = new Vector<>();
			go.getImports().addImport("mapset");
			String funcName = ((PGoTLASetOp) tla).getGoFunc();
			if (funcName.equals("NotIn")) {
				exp.add(new Token("!"));
				funcName = "Contains";
			}
			// rightSet is the object because lhs can be an element (e.g. in Contains)
			FunctionCall fc = new FunctionCall(funcName, lhs, rightSet);
			exp.add(fc);
			stmts.add(new SimpleExpression(exp));
		} else if (tla instanceof PGoTLAUnary) {
			Vector<Statement> rightRes = tlaTokenToStatement(((PGoTLAUnary) tla).getArg());

			// the argument should be a single Expression
			assert (rightRes.size() == 1);
			assert (rightRes.get(0) instanceof Expression);

			switch (((PGoTLAUnary) tla).getToken()) {
			case "!":
				Vector<Expression> exp = new Vector<>();
				exp.add(new Token("!"));
				exp.add((Expression) rightRes.get(0));
				stmts.add(new SimpleExpression(exp));
				break;
			case "UNION":
				// TODO implement
				break;
			case "SUBSET":
				FunctionCall fc = new FunctionCall("PowerSet", new Vector<>(), (Expression) rightRes.get(0));
				stmts.add(fc);
				break;
			}
		} else if (tla instanceof PGoTLAGroup) {
			Vector<Statement> inside = tlaTokenToStatement(((PGoTLAGroup) tla).getInner());

			assert (inside.size() == 1);
			assert (inside.get(0) instanceof Expression);

			stmts.add(new Group((Expression) inside.get(0)));
		}
		return stmts;
	}

	/**
	 * Given a tla ast, converts it to a Go value equivalent to assign to v
	 * 
	 * @param pGoTLA
	 * @return
	 */
	private Entry<SimpleExpression, Vector<Statement>> getGoValue(PGoVariable v, PGoTLA pGoTLA) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Add the position based argument command line parsing code to main
	 * 
	 * @param argN
	 * @param positionalArgs
	 * @param pv
	 */
	private void addPositionalArgToMain(int argN, Vector<Statement> positionalArgs, PGoVariable pv) {
		if (pv.getType().equals(new PGoPrimitiveType.PGoString())) {
			// var = flag.Args()[..]
			Vector<Expression> args = new Vector<Expression>();
			Vector<Expression> exp = new Vector<Expression>();
			exp.add(new Token(pv.getName()));
			exp.add(new Token(" = "));
			exp.add(new FunctionCall("flag.Args", args));
			exp.add(new Token("[" + argN + "]"));
			positionalArgs.add(new SimpleExpression(exp));
		} else if (pv.getType().equals(new PGoPrimitiveType.PGoInt())) {
			// var,_ = strconv.Atoi(flag.Args()[..])
			Vector<Expression> args = new Vector<Expression>();
			Vector<Expression> argExp = new Vector<Expression>();
			FunctionCall fc = new FunctionCall("flag.Args", args);

			args = new Vector<Expression>();
			argExp.add(fc);
			argExp.add(new Token("[" + argN + "]"));
			args.add(new SimpleExpression(argExp));
			FunctionCall convert = new FunctionCall("strconv.Atoi", args);
			go.getImports().addImport("strconv");

			Vector<Expression> exp = new Vector<Expression>();
			exp.add(new Token(pv.getName()));
			exp.add(new Token(",_"));
			exp.add(new Token(" = "));
			exp.add(convert);

			positionalArgs.add(new SimpleExpression(exp));
		} else if (pv.getType().equals(new PGoPrimitiveType.PGoNatural())) {
			// var,_ = strconv.ParseUint(flag.Args()[..], 10, 64)
			Vector<Expression> args = new Vector<Expression>();
			Vector<Expression> argExp = new Vector<Expression>();
			FunctionCall fc = new FunctionCall("flag.Args", args);

			args = new Vector<Expression>();
			argExp.add(fc);
			argExp.add(new Token("[" + argN + "]"));
			args.add(new SimpleExpression(argExp));
			args.add(new Token("10"));
			args.add(new Token("64"));
			FunctionCall convert = new FunctionCall("strconv.ParseUint", args);
			go.getImports().addImport("strconv");

			Vector<Expression> exp = new Vector<Expression>();
			exp.add(new Token(pv.getName()));
			exp.add(new Token(",_"));
			exp.add(new Token(" = "));
			exp.add(convert);

			positionalArgs.add(new SimpleExpression(exp));
		} else if (pv.getType().equals(new PGoPrimitiveType.PGoDecimal())) {
			// var = strconv.ParseFloat64(flag.Args()[..], 10, 64)
			Vector<Expression> args = new Vector<Expression>();
			Vector<Expression> argExp = new Vector<Expression>();
			FunctionCall fc = new FunctionCall("flag.Args", args);

			args = new Vector<Expression>();
			argExp.add(fc);
			argExp.add(new Token("[" + argN + "]"));
			args.add(new SimpleExpression(argExp));
			args.add(new Token("10"));
			args.add(new Token("64"));
			FunctionCall convert = new FunctionCall("strconv.ParseFloat64", args);
			go.getImports().addImport("strconv");

			Vector<Expression> exp = new Vector<Expression>();
			exp.add(new Token(pv.getName()));
			exp.add(new Token(",_"));
			exp.add(new Token(" = "));
			exp.add(convert);

			positionalArgs.add(new SimpleExpression(exp));
		} else if (pv.getType().equals(new PGoPrimitiveType.PGoBool())) {
			// var = strconv.ParseBool(flag.Args()[..])
			Vector<Expression> args = new Vector<Expression>();
			Vector<Expression> argExp = new Vector<Expression>();
			FunctionCall fc = new FunctionCall("flag.Args", args);

			args = new Vector<Expression>();
			argExp.add(fc);
			argExp.add(new Token("[" + argN + "]"));
			args.add(new SimpleExpression(argExp));
			FunctionCall convert = new FunctionCall("strconv.ParseBool", args);
			go.getImports().addImport("strconv");

			Vector<Expression> exp = new Vector<Expression>();
			exp.add(new Token(pv.getName()));
			exp.add(new Token(",_"));
			exp.add(new Token(" = "));
			exp.add(convert);

			positionalArgs.add(new SimpleExpression(exp));
		}
	}

	private void addFlagArgToMain(PGoVariable pv) throws PGoTransException {
		// flag arguments

		Vector<Expression> args = new Vector<Expression>();
		args.add(new Token("&" + pv.getName()));
		args.add(new Token(pv.getArgInfo().getName()));

		if (pv.getType().toGo().equals(new PGoPrimitiveType.PGoInt())) {
			// TODO we support default value and help message. Add this
			// to annotations
			args.add(new Token("0"));
			args.add(new Token("\"\""));
			main.add(new FunctionCall("flagIntVar", args));
		} else if (pv.getType().toGo().equals(new PGoPrimitiveType.PGoBool())) {
			args.add(new Token("false"));
			args.add(new Token("\"\""));
			main.add(new FunctionCall("flagBoolVar", args));
		} else if (pv.getType().toGo().equals(new PGoPrimitiveType.PGoString())) {
			args.add(new Token(""));
			args.add(new Token("\"\""));
			main.add(new FunctionCall("flagStringVar", args));
		} else if (pv.getType().toGo().equals(new PGoPrimitiveType.PGoNatural())) {
			args.add(new Token("0"));
			args.add(new Token("\"\""));
			main.add(new FunctionCall("flagUint64Var", args));
		} else if (pv.getType().toGo().equals(new PGoPrimitiveType.PGoDecimal())) {
			args.add(new Token("0.0"));
			args.add(new Token("\"\""));
			main.add(new FunctionCall("flagFloat64Var", args));
		} else {
			throw new PGoTransException(
					"Unsupported go argument type \"" + pv.getType().toGo() + "\" for variable \"" + pv.getName() + "\"",
					pv.getLine());
		}
	}

}