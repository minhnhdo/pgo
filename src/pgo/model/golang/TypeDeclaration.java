package pgo.model.golang;

public class TypeDeclaration extends Declaration {
	
	private String name;
	private Type type;
	
	public TypeDeclaration(String name, Type type) {
		super();
		this.name = name;
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public Type getType() {
		return type;
	}

	@Override
	public <T, E extends Throwable> T accept(DeclarationVisitor<T, E> v) throws E {
		return v.visit(this);
	}
	
}
