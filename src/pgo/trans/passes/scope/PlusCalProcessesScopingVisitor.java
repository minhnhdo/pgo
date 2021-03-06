package pgo.trans.passes.scope;

import pgo.errors.IssueContext;
import pgo.model.pcal.*;
import pgo.modules.TLAModuleLoader;
import pgo.scope.ChainMap;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlusCalProcessesScopingVisitor extends PlusCalProcessesVisitor<Void, RuntimeException> {

	private IssueContext ctx;
	private TLAScopeBuilder builder;
	private TLAScopeBuilder tlaBuilder;
	private DefinitionRegistry registry;
	private TLAModuleLoader loader;
	private Set<String> moduleRecursionSet;
	private boolean resolveConstants;

	public PlusCalProcessesScopingVisitor(IssueContext ctx, TLAScopeBuilder builder, TLAScopeBuilder tlaBuilder,
	                                      DefinitionRegistry registry, TLAModuleLoader loader, Set<String> moduleRecursionSet, boolean resolveConstants) {
		this.ctx = ctx;
		this.builder = builder;
		this.tlaBuilder = tlaBuilder;
		this.registry = registry;
		this.loader = loader;
		this.moduleRecursionSet = moduleRecursionSet;
		this.resolveConstants = resolveConstants;
	}

	@Override
	public Void visit(PlusCalSingleProcess singleProcess) throws RuntimeException {
		TLAScopeBuilder labelScope = new TLAScopeBuilder(ctx, new HashMap<>(), new ChainMap<>(builder.getDefinitions()), builder.getReferences());

		for (PlusCalStatement stmts : singleProcess.getBody()) {
			stmts.accept(new PlusCalStatementLabelCaptureVisitor(labelScope));
		}

		TLAScopeBuilder procScope = new TLAScopeBuilder(ctx, builder.getDeclarations(), labelScope.getDefinitions(), builder.getReferences());
		for (PlusCalStatement stmts : singleProcess.getBody()) {
			stmts.accept(new PlusCalStatementScopingVisitor(ctx, procScope, registry, loader, moduleRecursionSet, resolveConstants));
		}
		return null;
	}

	@Override
	public Void visit(PlusCalMultiProcess multiProcess) throws RuntimeException {
		for (PlusCalProcess proc : multiProcess.getProcesses()) {
			builder.defineGlobal(proc.getName().getName().getValue(), proc.getName().getUID());
			TLAScopeBuilder procTLAScope = new TLAScopeBuilder(ctx, new ChainMap<>(tlaBuilder.getDeclarations()), builder.getDefinitions(), builder.getReferences());
			proc.getName().getValue().accept(new TLAExpressionScopingVisitor(ctx, tlaBuilder, registry, loader, new HashSet<>(), resolveConstants));
			Map<String, UID> procVars = new ChainMap<>(builder.getDeclarations());

			for (PlusCalVariableDeclaration var : proc.getVariables()) {
				if (procTLAScope.declare(var.getName().getValue(), var.getUID())) {
					procVars.put(var.getName().getValue(), var.getUID());
					registry.addLocalVariable(var.getUID());
					var.getValue().accept(new TLAExpressionScopingVisitor(ctx, procTLAScope, registry, loader, new HashSet<>(), resolveConstants));
				}
			}

			TLAScopeBuilder procScope = new TLAScopeBuilder(ctx, procVars, new ChainMap<>(builder.getDefinitions()), builder.getReferences());
			procScope.defineLocal("self", proc.getName().getUID());
			registry.addLocalVariable(proc.getName().getUID());

			for (PlusCalStatement stmts : proc.getBody()) {
				stmts.accept(new PlusCalStatementLabelCaptureVisitor(procScope));
			}

			for (PlusCalStatement stmts : proc.getBody()) {
				stmts.accept(new PlusCalStatementScopingVisitor(ctx, procScope, registry, loader, moduleRecursionSet, resolveConstants));
			}
		}
		return null;
	}

}
