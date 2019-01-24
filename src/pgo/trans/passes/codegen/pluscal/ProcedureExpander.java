package pgo.trans.passes.codegen.pluscal;

import pgo.model.mpcal.ModularPlusCalMappingMacro;
import pgo.model.pcal.PlusCalCall;
import pgo.model.pcal.PlusCalProcedure;
import pgo.model.pcal.PlusCalStatement;
import pgo.model.pcal.PlusCalVariableDeclaration;
import pgo.model.tla.TLAExpression;
import pgo.model.tla.TLAGeneralIdentifier;
import pgo.model.tla.TLARef;
import pgo.scope.UID;
import pgo.trans.intermediate.DefinitionRegistry;
import pgo.trans.passes.codegen.NameCleaner;

import java.util.*;

class ProcedureExpander {
	private final DefinitionRegistry registry;
	private final NameCleaner nameCleaner;
	private final Map<UID, TLAGeneralIdentifier> parentArguments;
	private final Map<UID, ModularPlusCalMappingMacro> parentMappings;
	private final Set<UID> parentRefs;
	private final Set<UID> parentFunctionMappedVars;
	private final List<PlusCalProcedure> procedures;

	ProcedureExpander(DefinitionRegistry registry, NameCleaner nameCleaner,
	                  Map<UID, TLAGeneralIdentifier> parentArguments,
	                  Map<UID, ModularPlusCalMappingMacro> parentMappings, Set<UID> parentRefs,
	                  Set<UID> parentFunctionMappedVars, List<PlusCalProcedure> procedures) {
		this.registry = registry;
		this.nameCleaner = nameCleaner;
		this.parentArguments = parentArguments;
		this.parentMappings = parentMappings;
		this.parentRefs = parentRefs;
		this.parentFunctionMappedVars = parentFunctionMappedVars;
		this.procedures = procedures;
	}

	private void update(UID paramUID, UID valueUID, Map<UID, TLAGeneralIdentifier> arguments,
	                    Map<UID, ModularPlusCalMappingMacro> mappings, Set<UID> functionMappedVars) {
		ModularPlusCalMappingMacro mappingMacro = parentMappings.get(valueUID);
		if (mappingMacro != null) {
			mappings.put(paramUID, mappingMacro);
		}
		if (parentFunctionMappedVars.contains(valueUID)) {
			functionMappedVars.add(paramUID);
		}
		arguments.put(paramUID, parentArguments.get(valueUID));
	}

	PlusCalCall expand(PlusCalCall plusCalCall, TLAExpressionPlusCalCodeGenVisitor visitor) {
		PlusCalProcedure procedure = registry.findProcedure(plusCalCall.getTarget());
		Map<UID, PlusCalVariableDeclaration> params = new HashMap<>();
		Map<UID, TLAGeneralIdentifier> arguments = new LinkedHashMap<>();
		Set<UID> functionMappedVars = new HashSet<>();
		Map<UID, ModularPlusCalMappingMacro> mappings = new HashMap<>();
		Set<UID> refs = new HashSet<>();
		List<PlusCalVariableDeclaration> localVariables = new ArrayList<>(procedure.getVariables());
		List<PlusCalVariableDeclaration> actualParams = new ArrayList<>();
		List<TLAExpression> actualArguments = new ArrayList<>();
		List<PlusCalVariableDeclaration> procedureParams = procedure.getParams();
		List<TLAExpression> callArguments = plusCalCall.getArguments();
		for (int i = 0; i < procedureParams.size(); i++) {
			PlusCalVariableDeclaration param = procedureParams.get(i);
			TLAExpression value = callArguments.get(i);
			UID paramUID = param.getUID();
			params.put(paramUID, param);
			if (value instanceof TLARef) {
				UID valueUID = registry.followReference(value.getUID());
				if (parentRefs.contains(valueUID)) {
					refs.add(paramUID);
				}
				update(paramUID, valueUID, arguments, mappings, functionMappedVars);
			} else if (value instanceof TLAGeneralIdentifier) {
				UID valueUID = registry.followReference(value.getUID());
				if (!parentArguments.containsKey(valueUID)) {
					actualParams.add(param);
					actualArguments.add(value.accept(visitor));
					continue;
				}
				update(paramUID, valueUID, arguments, mappings, functionMappedVars);
			} else {
				actualParams.add(param);
				actualArguments.add(value.accept(visitor));
			}
		}
		ModularPlusCalCodeGenVisitor v = new ModularPlusCalCodeGenVisitor(
				registry, params, arguments, mappings, refs, functionMappedVars,
				new TemporaryBinding(nameCleaner, localVariables), new TemporaryBinding(nameCleaner, localVariables),
				new ProcedureExpander(
						registry, nameCleaner, arguments, mappings, refs, functionMappedVars, procedures));
		List<PlusCalStatement> body = new ArrayList<>();
		for (PlusCalStatement statement : procedure.getBody()) {
			body.addAll(statement.accept(v));
		}
		String procedureName = nameCleaner.cleanName(procedure.getName());
		procedures.add(new PlusCalProcedure(
				procedure.getLocation(), procedureName, actualParams, localVariables, body));
		return new PlusCalCall(plusCalCall.getLocation(), procedureName, actualArguments);
	}
}