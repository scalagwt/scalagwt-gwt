package com.google.gwt.dev.jjs.impl.jribble;

import static com.google.gwt.dev.jjs.impl.jribble.AstUtils.*;
import static java.util.Arrays.asList;

import com.google.gwt.dev.javac.MethodArgNamesLookup;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.ast.JDeclarationStatement;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.jjs.ast.JExpressionStatement;
import com.google.gwt.dev.jjs.ast.JMethod;
import com.google.gwt.dev.jjs.ast.JMethodBody;
import com.google.gwt.dev.jjs.ast.JMethodCall;
import com.google.gwt.dev.jjs.ast.JNewArray;
import com.google.gwt.dev.jjs.ast.JNode;
import com.google.gwt.dev.jjs.ast.JPrimitiveType;
import com.google.gwt.dev.jjs.impl.SourceGenerationVisitor;
import com.google.gwt.dev.jjs.impl.jribble.JribbleAstBuilder;
import com.google.gwt.dev.util.AbstractTextOutput;
import com.google.gwt.dev.util.TextOutput;

import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.*;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Expr.ExprType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Literal.LiteralType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Statement.StatementType;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

public class JribbleAstBuilderTest extends TestCase {

  private static final List<Type> noTypes = Collections.emptyList();
  private static final Expr superRef =
    Expr.newBuilder().setType(ExprType.SuperRef).build();
  private static final Expr thisRef =
    Expr.newBuilder().setType(ExprType.ThisRef).build();

  public void testEmptyClass() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    foo.ext = null;
    foo.classBody = asList(foo.defaultCstr);
    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testEmptyClass");
  }

  // test package object ClassDef with a None ext

  public void testOneVoidMethod() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.stmts = asList(returnn(null));
    foo.classBody = asList(declaration(zaz.build()),foo.defaultCstr);

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testOneVoidMethod");
    Assert.assertEquals("zaz()V", fooType.getMethods().get(3).getSignature());
    Assert.assertFalse(fooType.isExternal());
    Assert.assertEquals(JPrimitiveType.VOID, fooType.getMethods().get(3).getOriginalReturnType());
  }

  public void testMethodCall() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.stmts = asList(newWindowAlert("hello"));
    foo.classBody = asList(foo.defaultCstr, declaration(zaz.build()));

    JDeclaredType fooType = process(foo);
    JMethodCall call =
        (JMethodCall) ((JExpressionStatement) ((JMethodBody) fooType.getMethods().get(4)
            .getBody()).getStatements().get(0)).getExpr();
    Assert.assertEquals("alert(Ljava/lang/String;)V", call.getTarget().getSignature());
  }

  public void testOneStringMethod() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.returnType = toGlobalNameType("java.lang.String");
    zaz.stmts = asList(returnn(literal("hello")));
    foo.classBody = asList(declaration(zaz.build()), foo.defaultCstr);

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testOneStringMethod");
    Assert.assertEquals("zaz()Ljava/lang/String;", fooType.getMethods().get(3).getSignature());
    Assert.assertFalse(fooType.isExternal());
    Assert.assertTrue(fooType.getMethods().get(3).getOriginalReturnType().isExternal());
  }

  public void testLocalVariables() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");

    Statement intDef = varDef(primitive(PrimitiveType.Int), "i", null);
    Statement intAssignment = statement(assignment(varRef("i"), literal(10)));
    Statement stringDef = varDef(toGlobalNameType("java.lang.String"), "s", null);
    Statement stringAssignment = statement(assignment(varRef("s"), literal("string")));

    zaz.stmts = asList(intDef, intAssignment, stringDef, stringAssignment);
    foo.classBody = asList(declaration(zaz.build()), foo.defaultCstr);

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testLocalVariables");
  }

  public void testTryCatchFinally() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");

    Statement intDef = varDef(primitive(PrimitiveType.Int), "i", null);
    Statement tryBlock = block(intDef);
    
    Catch catchh = catchh("java.lang.Exception", "e", block(newWindowAlert("caught")));

    Statement finalBlock = block(newWindowAlert("finally"));

    Statement tryStmt = tryy(tryBlock, finalBlock, catchh);

    zaz.stmts = asList(tryStmt);
    foo.classBody = asList(declaration(zaz.build()), foo.defaultCstr);

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testTryCatchFinally");
    JMethodBody body = (JMethodBody) fooType.getMethods().get(3).getBody();
    Assert.assertEquals("zaz", body.getMethod().getName());
    // ensure both "int i" and "Exception e" are locals
    Assert.assertEquals(2, body.getLocals().size());
  }

  public void testNewCall() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    
    MethodSignature cstrSig = signature("java.util.ArrayList", "new",
        asList(primitive(PrimitiveType.Int)), toGlobalNameType("java.util.ArrayList"));

    Expr cstrArg = literal(1);
    Statement varDef = varDef(toGlobalNameType("java.util.List"), "l", 
        newObject("java.util.ArrayList", cstrSig, cstrArg));
    
    MethodSignature addSig = signature("java.util.List", "add",
        asList(toGlobalNameType("java.lang.Object")), voidType());

    Expr addParam = literal(1); // should be boxed
    Statement addCall = statement(methodCall(varRef("l"), addSig, addParam));

    zaz.stmts = asList(varDef, addCall);
    foo.classBody = asList(declaration(zaz.build()), foo.defaultCstr);

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testNewCall");
    // ensure the external JMethod was frozen so getSignature doesn't NPE
    JExpressionStatement addStmt =
        (JExpressionStatement) ((JMethodBody) fooType.getMethods().get(3).getBody()).getBlock()
            .getStatements().get(1);
    Assert.assertEquals("add(Ljava/lang/Object;)V", ((JMethodCall) addStmt.getExpr()).getTarget().getSignature());
  }

  public void testConstructors() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    
    MethodDefBuilder b1 = new MethodDefBuilder("new");
    b1.params = asList(paramDef("s", stringType()));
    b1.stmts = asList(statement(newSuperCstrCall("java.lang.Object")), 
        newWindowAlertVar("s"));
    Declaration cstr1 = declaration(b1.buildCstr());
    
    // cstr2 calls cstr1, which means it shouldn't have an $init call
    Expr cstr1Call =  methodCall(thisRef, signature("foo.Bar", "new", 
        asList(stringType()), voidType()), literal("a"));
    
    MethodDefBuilder b2 = new MethodDefBuilder("new");
    b2.params = asList(paramDef("i", toGlobalNameType("java.lang.Integer")));
    b2.stmts = asList(statement(cstr1Call), newWindowAlertVar("i"));
    Declaration cstr2 = declaration(b2.buildCstr());

    foo.classBody = asList(cstr1, cstr2);

    JClassType fooType = (JClassType) process(foo);
    assertEquals(fooType, "testConstructors");

    // ensure cstr2 calls cstr1
    JMethod cstr1m = fooType.getMethods().get(3);
    JMethod cstr2m = fooType.getMethods().get(4);
    JMethodCall c =
        (JMethodCall) ((JExpressionStatement) ((JMethodBody) cstr2m.getBody()).getStatements().get(
            0)).getExpr();
    Assert.assertEquals(cstr1m, c.getTarget());
    Assert.assertEquals(true, c.isStaticDispatchOnly());
  }

  public void testFields() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    
    // initialized
    Declaration f1 = declaration(modifiers("private"), 
        fieldDef(stringType(), "f1", literal("f1")));

    // un-initialized
    Declaration f2 = declaration(modifiers("private"),
        fieldDef(stringType(), "f2", null));
    
    // static initialized
    Declaration f3 = declaration(modifiers("private", "static"),
        fieldDef(stringType(), "f3", literal("f3")));

    // static un-initialized
    Declaration f4 = declaration(modifiers("private", "static"),
        fieldDef(stringType(), "f4", null));

    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.params = asList(paramDef("other", toGlobalNameType("foo.Bar")));
    
    Statement assignf1 = statement(
        assignment(fieldRef(thisRef, "foo.Bar", "f1"), literal("f11")));
    
    Statement assignf2 = statement(
        assignment(fieldRef(varRef("other"), "foo.Bar", "f2"), literal("f22")));
    
    Statement assignf3 = statement(
        assignment(fieldRef(null, "foo.Bar", "f3"), literal("f33")));
    
    Statement assignfOther = statement(
        assignment(fieldRef(null, "foo.Other", "i"), literal(1)));

    zaz.stmts = asList(assignf1, assignf2, assignf3, assignfOther);
    foo.classBody = asList(f1, f2, f3, f4, 
        declaration(zaz.build()), foo.defaultCstr);

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testFields");
    assertEquals(4, fooType.getFields().size());
  }

  public void testInterface() throws Exception {
    // are interface methods abstract? otherwise SourceGenerationVisitor fails
    
    MethodDefBuilder b = new MethodDefBuilder("zaz");
    b.returnType = stringType();
    b.params = asList(paramDef("x", toGlobalNameType("java.lang.Integer")));
    Declaration zaz = declaration(
        Modifiers.newBuilder().setIsAbstract(true).build(), 
        b.build());
    
    DeclaredTypeBuilder b1 = new DeclaredTypeBuilder("foo.SpecialList", true);
    b1.modifs = Modifiers.newBuilder().setIsPrivate(true).build();
    b1.impls = asList(toGlobalName("java.util.List"));
    b1.classBody = asList(zaz);
    DeclaredType specialList = b1.build();

    JDeclaredType fooType = new JribbleAstBuilder().process(specialList).types.get(0);
    assertEquals(fooType, "testInterface");
    Assert.assertTrue(fooType.getMethods().get(1).isAbstract());
  }

  public void testArrays() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");

    Type stringA = arrayType(stringType());
    Type stringAA = arrayType(stringA);
    
    Statement s1 = varDef(stringAA, "aa", newArray(stringType(), literal(1), literal(1)));
    
    Statement s2 = statement(assignment(arrayRef(arrayRef(varRef("aa"), literal(0)), 
        literal(0)), literal("s")));
    
    Statement s3 = varDef(stringA, "a", arrayInitializer(stringType(), 
        literal("1"), literal("2")));
    
    Statement s4 = statement(assignment(arrayRef(varRef("a"), literal(0)), literal("0")));

    zaz.stmts = asList(s1, s2, s3, s4);
    foo.classBody = asList(declaration(zaz.build()), foo.defaultCstr);

    JClassType fooType = (JClassType) process(foo);
    assertEquals(fooType, "testArrays");
    JMethod zazMethod = fooType.getMethods().get(3);
    // String[][]
    JDeclarationStatement decl1 = (JDeclarationStatement) ((JMethodBody) zazMethod.getBody()).getStatements().get(0);
    Assert.assertEquals(2, ((JNewArray) decl1.getInitializer()).dims.size());
    Assert.assertEquals(2, ((JNewArray) decl1.getInitializer()).getArrayType().getDims());
    // String[]
    JDeclarationStatement decl2 = (JDeclarationStatement) ((JMethodBody) zazMethod.getBody()).getStatements().get(2);
    Assert.assertEquals(null, ((JNewArray) decl2.getInitializer()).dims);
    Assert.assertEquals(1, ((JNewArray) decl2.getInitializer()).getArrayType().getDims());
  }

  public void testInterfacesTreatedAsClasses() throws Exception {
    JribbleAstBuilder jab = new JribbleAstBuilder();
    // do one unit that refers to another via a method call, assumed to be a class
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.params = asList(paramDef("l", toGlobalNameType("java.util.List")));
    Statement s1 = statement(methodCall(varRef("l"), signature("java.util.List", "add", 
        asList(toGlobalNameType("java.lang.Object")), voidType()), 
        varRef("l"))); 
    zaz.stmts = asList(s1);
    foo.classBody = asList(foo.defaultCstr, declaration(zaz.build()));
    jab.process(foo.build());
    // now do another unit that implements java.util.List, that requires it to be an interface
    DeclaredTypeBuilder foo2 = new DeclaredTypeBuilder("foo.Bar2", false);
    foo2.impls = asList(toGlobalName("java.util.List"));
    JDeclaredType foo2Type = process(foo2);
    assertEquals(foo2Type.getImplements().get(0).getName(), "java.util.List");
  }

  public void testMethodArgNames() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.params = asList(paramDef("i", primitive(PrimitiveType.Int)));
    foo.classBody = asList(foo.defaultCstr,  declaration(zaz.build()));

    MethodArgNamesLookup methodArgNames =
        new JribbleAstBuilder().process(foo.build()).methodArgNames;
    Assert.assertEquals("foo.Bar.Bar()V", methodArgNames.getMethods()[0]);
    Assert.assertEquals("foo.Bar.zaz(I)V", methodArgNames.getMethods()[1]);
    Assert.assertEquals("i", methodArgNames.lookup("foo.Bar.zaz(I)V")[0]);
  }

  public void testSuperCall() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    MethodDefBuilder zaz = new MethodDefBuilder("toString");
    MethodCall.Builder b = MethodCall.newBuilder();
    b.setSignature(signature("java.lang.Object", "toString", noTypes, stringType()));
    b.setReceiver(superRef);
    Statement superCall = statement(expr(b.build()));
    zaz.stmts = asList(superCall);
    zaz.returnType = toGlobalNameType("java.lang.String");
    foo.classBody = asList(declaration(zaz.build()), foo.defaultCstr);

    assertEquals(process(foo), "testSuperCall");
  }

  private static Statement newWindowAlert(String message) {
    Literal lit = Literal.newBuilder().setType(LiteralType.String).
      setStringValue(message).build();
    Expr alertParam = Expr.newBuilder().setType(ExprType.Literal).
      setLiteral(lit).build();
    return newWindowAlert(alertParam);
  }
  
  private static Statement newWindowAlert(Expr message) {
    MethodCall.Builder b = MethodCall.newBuilder();
    MethodSignature s = signature("gwt.Window", "alert", 
        asList(toGlobalNameType("java.lang.String")), voidType()); 
    b.setSignature(s);
    b.addArgument(message);
    return statement(expr(b.build()));
  }

  private static Statement newWindowAlertVar(String varName) {
    VarRef varRef = VarRef.newBuilder().setName(varName).build();
    return newWindowAlert(expr(varRef));
  }

  private static Expr newSuperCstrCall(String superClassName) {
    // jribble uses "super" when calling super constructors, e.g.:
    // (Ljava/lang/Object;::super()V;)();
    MethodSignature.Builder msb = MethodSignature.newBuilder();
    msb.setName("new");
    msb.setOwner(toGlobalName(superClassName));
    msb.setReturnType(voidType());
    MethodCall.Builder mb = MethodCall.newBuilder();
    mb.setSignature(msb.build());
    //return mb.build();
    return Expr.newBuilder().setType(ExprType.MethodCall).
      setMethodCall(mb.build()).build();
  }

  private static class MethodDefBuilder {
    private final String name;
    List<ParamDef> params = java.util.Collections.emptyList();
    List<Statement> stmts = java.util.Collections.emptyList();
    Type returnType = voidType();
    
    private Method.Builder b = Method.newBuilder();

    private MethodDefBuilder(String name) {
      this.name = name;
    }

    private Method build() {
      b.setReturnType(returnType);
      b.setName(name);
      b.addAllParamDef(params);
      Block block = Block.newBuilder().addAllStatement(stmts).build();
      Statement stmt = Statement.newBuilder().setType(StatementType.Block).
        setBlock(block).build(); 
      b.setBody(stmt);
      return b.build();
    }

    private Method buildCstr() {
      b.setIsConstructor(true);
      b.setReturnType(returnType);
      b.setName(name);
      b.addAllParamDef(params);
      Block block = Block.newBuilder().addAllStatement(stmts).build();
      Statement stmt = Statement.newBuilder().setType(StatementType.Block).
        setBlock(block).build(); 
      b.setBody(stmt);
      return b.build();
    }
  }

  private void assertEquals(JNode node, String name) throws IOException {
    dump(node, name);

    String actualFile =
        "../../dev/core/test/" + getClass().getName().replace('.', '/') + "." + name
            + ".ast.actual";
    String actual = FileUtils.readFileToString(new File(actualFile));

    String expectedFile =
        "../../dev/core/test/" + getClass().getName().replace('.', '/') + "." + name + ".ast";
    String expected;
    if (new File(expectedFile).exists()) {
      expected = FileUtils.readFileToString(new File(expectedFile));
    } else {
      expected = "";
    }

    Assert.assertEquals(expected, actual);
  }

  private void dump(JNode node, String name) throws IOException {
    String dumpFile =
        "../../dev/core/test/" + getClass().getName().replace('.', '/') + "." + name
            + ".ast.actual";
    FileOutputStream os = new FileOutputStream(dumpFile, false);
    final PrintWriter pw = new PrintWriter(os);
    TextOutput out = new AbstractTextOutput(false) {
      {
        setPrintWriter(pw);
      }
    };
    SourceGenerationVisitor v = new SourceGenerationVisitor(out);
    v.accept(node);
    pw.close();
  }

  private static class DeclaredTypeBuilder {
    private Modifiers modifs = Modifiers.getDefaultInstance();
    private GlobalName ext = toGlobalName("java.lang.Object");
    private List<GlobalName> impls = Collections.emptyList();
    private List<Declaration> classBody = Collections.emptyList();
    private Declaration defaultCstr;
    
    private DeclaredType.Builder b = DeclaredType.newBuilder();

    private DeclaredTypeBuilder(String name, boolean isInterface) {
      b.setName(toGlobalName(name));
      b.setIsInterface(isInterface);
      MethodDefBuilder cstr = new MethodDefBuilder("new");
      cstr.stmts = asList(statement(newSuperCstrCall("java.lang.Object")));
      defaultCstr = declaration(cstr.buildCstr());
    }

    private DeclaredType build() {
      if (ext != null) {
        b.setExt(ext);
      }
      b.addAllImplements(impls);
      b.addAllMember(classBody);
      b.setModifiers(modifs);
      return b.build();
    }
  }

  private static JDeclaredType process(DeclaredTypeBuilder foo) {
    return new JribbleAstBuilder().process(foo.build()).types.get(0);
  }

}
