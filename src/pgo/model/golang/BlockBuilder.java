package pgo.model.golang;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pgo.scope.UID;

public class BlockBuilder extends ASTBuilder implements Closeable {
	
	private ASTBuilder builder;
	private NameCleaner nameCleaner;
	private Map<UID, VariableName> nameMap;
	private List<Statement> statements;
	
	public interface OnSuccess {
		void action(Block block);
	}
	
	private OnSuccess onSuccess;
	private Set<String> labelScope;

	public BlockBuilder(ASTBuilder builder, NameCleaner nameCleaner, Map<UID, VariableName> nameMap, Set<String> labelScope) {
		this.builder = builder;
		this.nameCleaner = nameCleaner;
		this.nameMap = nameMap;
		this.labelScope = labelScope;
		this.statements = new ArrayList<>();
		this.onSuccess = (block) -> {
			builder.addBlock(block);
		};
	}
	
	public BlockBuilder(ASTBuilder builder, NameCleaner nameCleaner, Map<UID, VariableName> nameMap, Set<String> labelScope, OnSuccess onSuccess) {
		this.builder = builder;
		this.nameCleaner = nameCleaner;
		this.nameMap = nameMap;
		this.labelScope = labelScope;
		this.statements = new ArrayList<>();
		this.onSuccess = onSuccess;
	}
	
	public Block getBlock() {
		return new Block(statements);
	}
	
	public void labelIsUnique(String name) {
		if(labelScope.contains(name)) {
			throw new RuntimeException("internal compiler error");
		}
		statements.add(new Label(name));
	}
	
	public void assign(Expression name, Expression value) {
		addStatement(new Assignment(Collections.singletonList(name), false, Collections.singletonList(value)));
	}
	
	public void assign(List<Expression> names, List<Expression> values) {
		addStatement(new Assignment(names, false, values));
	}
	
	public ASTBuilder getParent() {
		return builder;
	}
	
	public IfBuilder ifStmt(Expression condition) {
		return new IfBuilder(this, nameCleaner.child(), nameMap, labelScope, condition);
	}
	
	public void print(Expression value) {
		addImport("fmt");
		addStatement(
				new ExpressionStatement(
						new Call(
								new Selector(
										new VariableName("fmt"), "Printf"),
								Arrays.asList(new StringLiteral("%v\\n"), value))));
	}
	
	public BlockBuilder forLoop(Expression condition) {
		return new BlockBuilder(
				this, nameCleaner.child(), nameMap, labelScope,
				block -> addStatement(new For(null, condition, null, block)));
	}
	
	public BlockBuilder forLoop(Statement init, Expression condition, Statement inc) {
		return new BlockBuilder(
				this, nameCleaner.child(), nameMap, labelScope,
				block -> addStatement(new For(init, condition, inc, block)));
	}
	
	public VariableName varDecl(String nameHint, Expression value) {
		VariableName name = getFreshName(nameHint);
		addStatement(new Assignment(Collections.singletonList(name), true, Collections.singletonList(value)));
		return name;
	}
	
	public VariableName getFreshName(String nameHint) {
		String actualName = nameCleaner.cleanName(nameHint);
		return new VariableName(actualName);
	}
	
	@Override
	protected void addStatement(Statement s) {
		statements.add(s);
	}
	
	@Override
	protected void addBlock(Block block) {
		statements.add(block);
	}
	
	@Override
	protected void addFunction(FunctionDeclaration fn) {
		builder.addFunction(fn);
	}

	@Override
	public void close() {
		onSuccess.action(getBlock());
	}

	@Override
	public TypeName defineType(String nameHint, Type type) {
		return builder.defineType(nameHint, type);
	}

	@Override
	public FunctionDeclarationBuilder defineFunction(UID uid, String nameHint) {
		return builder.defineFunction(uid, nameHint);
	}

	public void returnStmt(Expression... values) {
		addStatement(new Return(Arrays.asList(values)));
	}

	public void linkUID(UID uid, VariableName name) {
		nameMap.put(uid, name);
	}

	public VariableName findUID(UID uid) {
		if(!nameMap.containsKey(uid)) {
			throw new RuntimeException("internal compiler error");
		}
		return nameMap.get(uid);
	}

	@Override
	public void addImport(String name) {
		builder.addImport(name);
	}
}
