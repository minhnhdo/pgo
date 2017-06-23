package pgo.model.parser;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import pcal.PcalBuiltInSymbols;
import pcal.TLAToken;

import java.util.Vector;

import pgo.model.intermediate.PGoType;
import pgo.model.intermediate.PGoVariable;
import pgo.parser.PGoParseException;

public class AnnotatedTLADefinitionTest {
	@Before
	public void setup() {
		PcalBuiltInSymbols.Initialize();
	}
	
	@Test
	public void testSimpleDef() throws PGoParseException {
		String annot = "macro SimpleDef(param int) == param * 2";
		AnnotatedTLADefinition defn = AnnotatedTLADefinition.parse(annot, 0);
		assertEquals("SimpleDef", defn.getName());
		Vector<PGoVariable> params = defn.getParams();
		assertEquals(1, params.size());
		assertEquals("param", params.get(0).getName());
		assertEquals(PGoType.inferFromGoTypeName("int"), params.get(0).getType());
		Vector<TLAToken> expr = (Vector<TLAToken>) defn.getExpr().tokens.get(0);
		assertEquals(3, expr.size());
		assertEquals("param", expr.get(0).string);
		assertEquals("*", expr.get(1).string);
		assertEquals("2", expr.get(2).string);
		
		annot = "macro SimpleDefTwo(param1 int, param2 int) == param1 > param2";
		defn = AnnotatedTLADefinition.parse(annot, 0);
		assertEquals("SimpleDefTwo", defn.getName());
		params = defn.getParams();
		assertEquals(2, params.size());
		assertEquals("param1", params.get(0).getName());
		assertEquals("param2", params.get(1).getName());
		assertEquals(PGoType.inferFromGoTypeName("int"), params.get(0).getType());
		assertEquals(PGoType.inferFromGoTypeName("int"), params.get(1).getType());
		expr = (Vector<TLAToken>) defn.getExpr().tokens.get(0);
		assertEquals(3, expr.size());
		assertEquals("param1", expr.get(0).string);
		assertEquals(">", expr.get(1).string);
		assertEquals("param2", expr.get(2).string);
	}
	
	@Test
	public void testMultiLine() throws PGoParseException {
		String annot = "macro MultiLine(param uint64) == \\A i \\in 1 .. param :\n"
				+ "i > 1";
		AnnotatedTLADefinition defn = AnnotatedTLADefinition.parse(annot, 0);
		assertEquals("MultiLine", defn.getName());
		assertEquals(1, defn.getParams().size());
		assertEquals("param", defn.getParams().get(0).getName());
		assertEquals(PGoType.inferFromGoTypeName("uint64"), defn.getParams().get(0).getType());
		Vector<Vector<TLAToken>> expr = (Vector<Vector<TLAToken>>) defn.getExpr().tokens;
		assertEquals(2, expr.size());
		assertEquals(7, expr.get(0).size());
		assertEquals(3, expr.get(1).size());
		
		annot += "\n";
		annot += "/\\ i < 4";
		defn = AnnotatedTLADefinition.parse(annot, 0);
		expr = (Vector<Vector<TLAToken>>) defn.getExpr().tokens;
		assertEquals(3, expr.size());
		assertEquals(7, expr.get(0).size());
		assertEquals(3, expr.get(1).size());
		assertEquals(4, expr.get(2).size());
	}
}