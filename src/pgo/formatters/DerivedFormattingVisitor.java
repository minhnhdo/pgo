package pgo.formatters;

import pgo.model.type.constraint.MonomorphicConstraint;
import pgo.model.type.constraint.PolymorphicConstraint;
import pgo.model.type.Type;
import pgo.scope.UID;
import pgo.trans.intermediate.OperatorAccessor;
import pgo.util.Derived;
import pgo.util.DerivedVisitor;
import pgo.util.Origin;

import java.io.IOException;
import java.util.List;

public class DerivedFormattingVisitor extends DerivedVisitor<Void, IOException> {
	private IndentingWriter out;

	public DerivedFormattingVisitor(IndentingWriter out) {
		this.out = out;
	}

	public void writeOrigins(List<Origin> origins) throws IOException {
		if (origins.isEmpty()) {
			out.write(" derived from ???");
		} else {
			out.write(" derived from ");
			boolean first = true;
			try (IndentingWriter.Indent ignored = out.indent()){
				for (Origin o : origins) {
					if (first) {
						first = false;
					} else {
						out.write(", ");
					}
					o.accept(new OriginFormattingVisitor(out));
				}
			}
		}
	}

	private void writeOrigins(Derived d) throws IOException {
		writeOrigins(d.getOrigins());
	}

	@Override
	public Void visit(UID uid) throws IOException {
		out.write("symbol");
		writeOrigins(uid);
		return null;
	}

	@Override
	public Void visit(Type type) throws IOException {
		out.write("type [");
		type.accept(new TypeFormattingVisitor(out));
		out.write("]");
		writeOrigins(type);
		return null;
	}

	@Override
	public Void visit(OperatorAccessor operatorAccessor) throws IOException {
		out.write("TLA operator");
		writeOrigins(operatorAccessor);
		return null;
	}

	@Override
	public Void visit(MonomorphicConstraint pGoTypeMonomorphicConstraint) throws IOException {
		out.write("type constraint");
		writeOrigins(pGoTypeMonomorphicConstraint);
		return null;
	}

	@Override
	public Void visit(PolymorphicConstraint pGoTypePolymorphicConstraint) throws IOException {
		out.write("polymorphic type constraint");
		writeOrigins(pGoTypePolymorphicConstraint);
		return null;
	}
}
