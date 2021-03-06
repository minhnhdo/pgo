package pgo.trans.passes.atomicity;

import pgo.InternalCompilerError;
import pgo.model.tla.TLAExpression;
import pgo.model.tla.TLAGeneralIdentifier;
import pgo.util.UnionFind;
import pgo.model.mpcal.ModularPlusCalBlock;
import pgo.model.pcal.*;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;

import java.util.*;
import java.util.function.BiConsumer;

public class AtomicityInferencePass {
	private AtomicityInferencePass() {}

	private static void trackGlobalVar(DefinitionRegistry registry, Map<UID, Set<UID>> map, TLAExpression expression, UID labelUID) {
		// we are only interested in writes to variable expressions.
		// Function calls are only dealt with in MPCal
		if (expression instanceof TLAGeneralIdentifier) {
			UID definitionUID = registry.followReference(expression.getUID());
			if (registry.isGlobalVariable(definitionUID)) {
				map.putIfAbsent(definitionUID, new HashSet<>());
				map.get(definitionUID).add(labelUID);
			}
		}
	}

	private static void addToUnionFind(UnionFind<UID> unionFind, Map<UID, Set<UID>> map) {
		for (Map.Entry<UID, Set<UID>> entry : map.entrySet()) {
			UID varUID = entry.getKey();
			for (UID labelUID : entry.getValue()) {
				unionFind.union(labelUID, varUID);
			}
		}
	}

	public static void perform(DefinitionRegistry registry, ModularPlusCalBlock modularPlusCalBlock) {
		if (modularPlusCalBlock.getProcesses() instanceof PlusCalMultiProcess) {
			Map<UID, Set<UID>> globalVarReadsToLabel = new HashMap<>();
			Map<UID, Set<UID>> globalVarWritesToLabel = new HashMap<>();
			BiConsumer<TLAExpression, UID> captureLabelRead = (expression, labelUID) ->
					trackGlobalVar(registry, globalVarReadsToLabel, expression, labelUID);
			BiConsumer<TLAExpression, UID> captureLabelWrite = (expression, labelUID) ->
					trackGlobalVar(registry, globalVarWritesToLabel, expression, labelUID);
			Set<UID> foundLabels = new HashSet<>();
			for (PlusCalProcedure p : modularPlusCalBlock.getProcedures()) {
				for (PlusCalStatement statements : p.getBody()) {
					statements.accept(new PlusCalStatementAtomicityInferenceVisitor(
							new UID(), captureLabelRead, captureLabelWrite, foundLabels));
				}
			}
			for (PlusCalProcess p : ((PlusCalMultiProcess) modularPlusCalBlock.getProcesses()).getProcesses()) {
				for (PlusCalStatement statements : p.getBody()) {
					statements.accept(new PlusCalStatementAtomicityInferenceVisitor(
							new UID(), captureLabelRead, captureLabelWrite, foundLabels));
				}
			}
			UnionFind<UID> unionFind = new UnionFind<>();
			addToUnionFind(unionFind, globalVarReadsToLabel);
			addToUnionFind(unionFind, globalVarWritesToLabel);
			Map<UID, Integer> seenRoots = new HashMap<>();
			for (UID labelUID : foundLabels) {
				if (unionFind.getRank(labelUID) > 0) {
					UID rootUID = unionFind.find(labelUID);
					if (!seenRoots.containsKey(rootUID)) {
						seenRoots.put(rootUID, seenRoots.size());
					}
					registry.addLabelToLockGroup(labelUID, seenRoots.get(rootUID));
				}
			}
			for (UID varUID : registry.globalVariables()) {
				if (unionFind.getRank(varUID) > 0) {
					registry.addProtectedGlobalVariable(varUID);
					int lockGroup = seenRoots.get(unionFind.find(varUID));
					boolean isRead = globalVarReadsToLabel.getOrDefault(varUID, Collections.emptySet())
							.stream()
							.map(registry::getLockGroup)
							.anyMatch(i -> i.equals(lockGroup));
					boolean isWritten = globalVarWritesToLabel.getOrDefault(varUID, Collections.emptySet())
							.stream()
							.map(registry::getLockGroup)
							.anyMatch(i -> i.equals(lockGroup));
					if (!isRead && !isWritten) {
						throw new InternalCompilerError();
					}
					if (isRead) {
						registry.addVariableReadToLockGroup(varUID, lockGroup);
					}
					if (isWritten) {
						registry.addVariableWriteToLockGroup(varUID, lockGroup);
					}
				}
			}
		}
	}
}
