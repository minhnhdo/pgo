package pgo.trans.passes.type;

import pgo.Unreachable;
import pgo.model.mpcal.ModularPlusCalYield;
import pgo.model.pcal.*;
import pgo.model.type.*;
import pgo.model.type.constraint.MonomorphicConstraint;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlusCalStatementTypeConstraintVisitor extends PlusCalStatementVisitor<Void, RuntimeException> {
	protected DefinitionRegistry registry;
	protected TypeSolver solver;
	private TypeGenerator generator;
	protected Map<UID, TypeVariable> mapping;
	protected TLAExpressionTypeConstraintVisitor exprVisitor;

	public PlusCalStatementTypeConstraintVisitor(DefinitionRegistry registry, TypeSolver solver,
	                                             TypeGenerator generator, Map<UID, TypeVariable> mapping) {
		this(registry, solver, generator, mapping,
				new TLAExpressionTypeConstraintVisitor(registry, solver, generator, mapping));
	}

	protected PlusCalStatementTypeConstraintVisitor(DefinitionRegistry registry, TypeSolver solver,
	                                                TypeGenerator generator, Map<UID, TypeVariable> mapping,
	                                                TLAExpressionTypeConstraintVisitor exprVisitor) {
		this.registry = registry;
		this.solver = solver;
		this.generator = generator;
		this.mapping = mapping;
		this.exprVisitor = exprVisitor;
	}

	@Override
	public Void visit(PlusCalLabeledStatements plusCalLabeledStatements) throws RuntimeException {
		for (PlusCalStatement stmt : plusCalLabeledStatements.getStatements()) {
			stmt.accept(this);
		}
		return null;
	}

	@Override
	public Void visit(PlusCalWhile plusCalWhile) throws RuntimeException {
		solver.addConstraint(new MonomorphicConstraint(plusCalWhile, exprVisitor.wrappedVisit(plusCalWhile.getCondition()), new BoolType(Collections.singletonList(plusCalWhile))));
		for (PlusCalStatement stmt : plusCalWhile.getBody()) {
			stmt.accept(this);
		}
		return null;
	}

	@Override
	public Void visit(PlusCalIf plusCalIf) throws RuntimeException {
		solver.addConstraint(new MonomorphicConstraint(plusCalIf, exprVisitor.wrappedVisit(plusCalIf.getCondition()), new BoolType(Collections.singletonList(plusCalIf))));
		for (PlusCalStatement stmt : plusCalIf.getYes()) {
			stmt.accept(this);
		}
		for (PlusCalStatement stmt : plusCalIf.getNo()) {
			stmt.accept(this);
		}
		return null;
	}

	@Override
	public Void visit(PlusCalEither plusCalEither) throws RuntimeException {
		for (List<PlusCalStatement> eitherCase : plusCalEither.getCases()) {
			for (PlusCalStatement statement : eitherCase) {
				statement.accept(this);
			}
		}
		return null;
	}

	@Override
	public Void visit(PlusCalAssignment plusCalAssignment) throws RuntimeException {
		for(PlusCalAssignmentPair pair : plusCalAssignment.getPairs()) {
			solver.addConstraint(new MonomorphicConstraint(
					pair,
					exprVisitor.wrappedVisit(pair.getLhs()),
					exprVisitor.wrappedVisit(pair.getRhs())));
		}
		return null;
	}

	@Override
	public Void visit(PlusCalReturn plusCalReturn) throws RuntimeException {
		// pass
		return null;
	}

	@Override
	public Void visit(PlusCalSkip plusCalSkip) throws RuntimeException {
		// pass
		return null;
	}

	@Override
	public Void visit(PlusCalCall plusCalCall) throws RuntimeException {
		PlusCalProcedure proc = registry.findProcedure(plusCalCall.getTarget());
		List<Type> callArgs = plusCalCall.getArguments()
				.stream()
				.map(e -> exprVisitor.wrappedVisit(e))
				.collect(Collectors.toList());
		solver.addConstraint(new MonomorphicConstraint(
				plusCalCall,
				mapping.get(proc.getUID()),
				new ProcedureType(callArgs, Collections.singletonList(plusCalCall))));
		return null;
	}

	@Override
	public Void visit(PlusCalMacroCall macroCall) throws RuntimeException {
		throw new Unreachable();
	}

	@Override
	public Void visit(PlusCalWith plusCalWith) throws RuntimeException {
		for(PlusCalVariableDeclaration declaration : plusCalWith.getVariables()) {
			TypeInferencePass.constrainVariableDeclaration(registry, declaration, solver, generator, mapping);
		}
		for (PlusCalStatement stmt : plusCalWith.getBody()) {
			stmt.accept(this);
		}
		return null;
	}

	@Override
	public Void visit(PlusCalPrint plusCalPrint) throws RuntimeException {
		exprVisitor.wrappedVisit(plusCalPrint.getValue());
		return null;
	}

	@Override
	public Void visit(PlusCalAssert plusCalAssert) throws RuntimeException {
		solver.addConstraint(new MonomorphicConstraint(plusCalAssert, exprVisitor.wrappedVisit(plusCalAssert.getCondition()), new BoolType(Collections.singletonList(plusCalAssert))));
		return null;
	}

	@Override
	public Void visit(PlusCalAwait plusCalAwait) throws RuntimeException {
		solver.addConstraint(new MonomorphicConstraint(plusCalAwait, exprVisitor.wrappedVisit(plusCalAwait.getCondition()), new BoolType(Collections.singletonList(plusCalAwait))));
		return null;
	}

	@Override
	public Void visit(PlusCalGoto plusCalGoto) throws RuntimeException {
		// pass
		return null;
	}

	@Override
	public Void visit(ModularPlusCalYield modularPlusCalYield) throws RuntimeException {
		exprVisitor.wrappedVisit(modularPlusCalYield.getExpression());
		return null;
	}
}
