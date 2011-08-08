package com.google.gwt.dev.jjs.impl;

import static com.google.gwt.dev.jjs.impl.AstUtils.toRef;

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
import com.google.gwt.dev.util.AbstractTextOutput;
import com.google.gwt.dev.util.TextOutput;
import com.google.jribble.ast.Array;
import com.google.jribble.ast.ArrayInitializer;
import com.google.jribble.ast.ArrayRef;
import com.google.jribble.ast.Assignment;
import com.google.jribble.ast.Block;
import com.google.jribble.ast.ClassBodyElement;
import com.google.jribble.ast.ClassDef;
import com.google.jribble.ast.Constructor;
import com.google.jribble.ast.ConstructorCall;
import com.google.jribble.ast.Expression;
import com.google.jribble.ast.FieldDef;
import com.google.jribble.ast.FieldRef;
import com.google.jribble.ast.IntLiteral;
import com.google.jribble.ast.InterfaceDef;
import com.google.jribble.ast.MethodCall;
import com.google.jribble.ast.MethodDef;
import com.google.jribble.ast.NewArray;
import com.google.jribble.ast.NewCall;
import com.google.jribble.ast.ParamDef;
import com.google.jribble.ast.Primitive;
import com.google.jribble.ast.Ref;
import com.google.jribble.ast.Return;
import com.google.jribble.ast.Signature;
import com.google.jribble.ast.Statement;
import com.google.jribble.ast.StaticFieldRef;
import com.google.jribble.ast.StaticMethodCall;
import com.google.jribble.ast.StringLiteral;
import com.google.jribble.ast.SuperRef$;
import com.google.jribble.ast.ThisRef$;
import com.google.jribble.ast.Try;
import com.google.jribble.ast.Type;
import com.google.jribble.ast.VarDef;
import com.google.jribble.ast.VarRef;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import scala.Option;
import scala.Some;
import scala.Tuple3;
import scala.collection.immutable.$colon$colon;
import scala.collection.immutable.HashSet;
import scala.collection.immutable.List;
import scala.collection.immutable.List$;
import scala.collection.immutable.Set;

public class JribbleAstBuilderTest extends TestCase {

  private static final List<Type> noTypes = list();
  private static final List<Expression> noExpressions = list();
  private static final Expression superRef = SuperRef$.MODULE$;

  public void testEmptyClass() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    foo.ext = Option.apply(null);
    foo.classBody = list(foo.defaultCstr);
    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testEmptyClass");
  }

  // test package object ClassDef with a None ext

  public void testOneVoidMethod() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    Option<Expression> none = Option.apply(null);
    zaz.stmts = list((Statement) new Return(none));
    foo.classBody = list(foo.defaultCstr, zaz.build());

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testOneVoidMethod");
    Assert.assertEquals("zaz()V", fooType.getMethods().get(3).getSignature());
    Assert.assertFalse(fooType.isExternal());
    Assert.assertEquals(JPrimitiveType.VOID, fooType.getMethods().get(3).getOriginalReturnType());
  }

  public void testMethodCall() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.stmts = list(newWindowAlert("hello"));
    foo.classBody = list(foo.defaultCstr, zaz.build());

    JDeclaredType fooType = process(foo);
    JMethodCall call =
        (JMethodCall) ((JExpressionStatement) ((JMethodBody) fooType.getMethods().get(3)
            .getBody()).getStatements().get(0)).getExpr();
    Assert.assertEquals("alert(Ljava/lang/String;)V", call.getTarget().getSignature());
  }

  public void testOneStringMethod() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.returnType = toRef("java.lang.String");
    zaz.stmts = list((Statement) new Return(new Some<Expression>(new StringLiteral("hello"))));
    foo.classBody = list(foo.defaultCstr, zaz.build());

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testOneStringMethod");
    Assert.assertEquals("zaz()Ljava/lang/String;", fooType.getMethods().get(3).getSignature());
    Assert.assertFalse(fooType.isExternal());
    Assert.assertTrue(fooType.getMethods().get(3).getOriginalReturnType().isExternal());
  }

  public void testLocalVariables() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    Option<Expression> none = Option.apply(null);

    Statement intDef = new VarDef(new Primitive("int"), "i", none);
    Statement intAssignment = new Assignment(new VarRef("i"), new IntLiteral(10));
    Statement stringDef = new VarDef(toRef("java.lang.String"), "s", none);
    Statement stringAssignment = new Assignment(new VarRef("s"), new StringLiteral("string"));

    zaz.stmts = list(intDef, intAssignment, stringDef, stringAssignment);
    foo.classBody = list(foo.defaultCstr, zaz.build());

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testLocalVariables");
  }

  public void testTryCatchFinally() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    Option<Expression> none = Option.apply(null);

    Statement intDef = new VarDef(new Primitive("int"), "i", none);
    Block tryBlock = new Block(list(intDef));

    Tuple3<Ref, String, Block> catchTuple =
        new Tuple3<Ref, String, Block>(toRef("java.lang.Exception"), "e", new Block(
            list(newWindowAlert("caught"))));
    List<Tuple3<Ref, String, Block>> catches = list(catchTuple);

    Block finalBlock = new Block(list(newWindowAlert("finally")));

    Statement tryStmt = new Try(tryBlock, catches, Option.apply(finalBlock));

    zaz.stmts = list(tryStmt);
    foo.classBody = list(foo.defaultCstr, zaz.build());

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testTryCatchFinally");
    JMethodBody body = (JMethodBody) fooType.getMethods().get(3).getBody();
    Assert.assertEquals("zaz", body.getMethod().getName());
    // ensure both "int i" and "Exception e" are locals
    Assert.assertEquals(2, body.getLocals().size());
  }

  public void testNewCall() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");

    Signature cstrSig =
        new Signature(toRef("java.util.ArrayList"), "this", list((Type) new Primitive("int")),
            toRef("java.util.ArrayList"));
    Expression cstrArg = new IntLiteral(1);
    Statement varDef =
        new VarDef(toRef("java.util.List"), "l", new Some<Expression>(new NewCall(
            new ConstructorCall(cstrSig, list(cstrArg)))));

    Signature addSig =
        new Signature(toRef("java.util.List"), "add", list((Type) toRef("java.lang.Object")),
            new Primitive("void"));
    Expression addParam = new IntLiteral(1); // should be boxed
    Statement addCall = new MethodCall(new VarRef("l"), addSig, list(addParam));

    zaz.stmts = list(varDef, addCall);
    foo.classBody = list(foo.defaultCstr, zaz.build());

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testNewCall");
    // ensure the external JMethod was frozen so getSignature doesn't NPE
    JExpressionStatement addStmt =
        (JExpressionStatement) ((JMethodBody) fooType.getMethods().get(3).getBody()).getBlock()
            .getStatements().get(1);
    Assert.assertEquals("add(Ljava/lang/Object;)V", ((JMethodCall) addStmt.getExpr()).getTarget().getSignature());
  }

  public void testConstructors() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");

    Set<String> modifs = new HashSet<String>();
    Constructor cstr1 =
        new Constructor(modifs, "this", list(new ParamDef("s", toRef("java.lang.String"))),
            new Block(list(newSuperCstrCall("java.lang.Object"), newWindowAlertVar("s"))));
    // cstr2 calls cstr1, which means it shouldn't have an $init call
    ConstructorCall cstr1Call =
        new ConstructorCall(new Signature(toRef("foo.Bar"), "this",
            list((Type) toRef("java.lang.String")), new Primitive("void")),
            list((Expression) new StringLiteral("a")));
    Constructor cstr2 =
        new Constructor(modifs, "this", list(new ParamDef("i", toRef("java.lang.Integer"))),
            new Block(list(cstr1Call, newWindowAlertVar("i"))));

    foo.classBody = list((ClassBodyElement) cstr1, cstr2);

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
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    Option<Expression> none = Option.apply(null);

    // initialized
    FieldDef f1 =
        new FieldDef(set("private"), toRef("java.lang.String"), "f1", new Some<Expression>(
            new StringLiteral("f1")));
    // un-initialized
    FieldDef f2 = new FieldDef(set("private"), toRef("java.lang.String"), "f2", none);
    // static initialized
    FieldDef f3 =
        new FieldDef(set("private", "static"), toRef("java.lang.String"), "f3",
            new Some<Expression>(new StringLiteral("f3")));
    // static un-initialized
    FieldDef f4 = new FieldDef(set("private", "static"), toRef("java.lang.String"), "f4", none);

    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.params = list(new ParamDef("other", toRef("foo.Bar")));
    Statement assignf1 =
        new Assignment(new FieldRef(ThisRef$.MODULE$, toRef("foo.Bar"), "f1"), new StringLiteral(
            "f11"));
    Statement assignf2 =
        new Assignment(new FieldRef(new VarRef("other"), toRef("foo.Bar"), "f2"),
            new StringLiteral("f22"));
    Statement assignf3 =
        new Assignment(new StaticFieldRef(toRef("foo.Bar"), "f3"), new StringLiteral("f33"));
    Statement assignfOther =
        new Assignment(new StaticFieldRef(toRef("foo.Other"), "i"), new IntLiteral(1));
    zaz.stmts = list(assignf1, assignf2, assignf3, assignfOther);
    foo.classBody = list(foo.defaultCstr, (ClassBodyElement) f1, f2, f3, f4, zaz.build());

    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testFields");
    assertEquals(4, fooType.getFields().size());
  }

  public void testInterface() throws Exception {
    Option<Block> none = Option.apply(null);
    // are interface methods abstract? otherwise SourceGenerationVisitor fails
    MethodDef zaz =
        new MethodDef(set("abstract"), toRef("java.lang.String"), "zaz", list(new ParamDef("x",
            toRef("java.lang.Integer"))), none);
    InterfaceDef specialList =
        new InterfaceDef(set("private"), toRef("foo.SpecialList"), list(toRef("java.util.List")),
            list(zaz));

    JDeclaredType fooType = new JribbleAstBuilder().process(specialList).types.get(0);
    assertEquals(fooType, "testInterface");
    Assert.assertTrue(fooType.getMethods().get(1).isAbstract());
  }

  public void testArrays() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");

    Type string = toRef("java.lang.String");
    Type stringA = new Array(string);
    Type stringAA = new Array(new Array(string));

    Statement s1 =
        new VarDef(stringAA, "aa", new Some<Expression>(new NewArray(string, list(
            (Option<Expression>) new Some<Expression>(new IntLiteral(1)), new Some<Expression>(
                new IntLiteral(1))))));
    Statement s2 =
        new Assignment(new ArrayRef(new ArrayRef(new VarRef("aa"), new IntLiteral(0)),
            new IntLiteral(0)), new StringLiteral("s"));

    Statement s3 =
        new VarDef(stringA, "a", new Some<Expression>(new ArrayInitializer(string, list(
            (Expression) new StringLiteral("1"), new StringLiteral("2")))));
    Statement s4 =
        new Assignment(new ArrayRef(new VarRef("a"), new IntLiteral(0)), new StringLiteral("0"));

    zaz.stmts = list(s1, s2, s3, s4);
    foo.classBody = list(foo.defaultCstr, zaz.build());

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
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.params = list(new ParamDef("l", toRef("java.util.List")));
    Statement s1 =
        new MethodCall(new VarRef("l"), new Signature(toRef("java.util.List"), "add",
            list((Type) toRef("java.lang.Object")), new Primitive("void")),
            list((Expression) new VarRef("l")));
    zaz.stmts = list(s1);
    foo.classBody = list(foo.defaultCstr, zaz.build());
    jab.process(foo.build());
    // now do another unit that implements java.util.List, that requires it to be an interface
    ClassDefBuilder foo2 = new ClassDefBuilder("foo.Bar2");
    foo2.impls = list(toRef("java.util.List"));
    JDeclaredType foo2Type = process(foo2);
    assertEquals(foo2Type.getImplements().get(0).getName(), "java.util.List");
  }

  public void testMethodArgNames() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("zaz");
    zaz.params = list(new ParamDef("i", new Primitive("int")));
    foo.classBody = list(foo.defaultCstr, zaz.build());

    MethodArgNamesLookup methodArgNames =
        new JribbleAstBuilder().process(foo.build()).methodArgNames;
    Assert.assertEquals("foo.Bar.Bar()V", methodArgNames.getMethods()[0]);
    Assert.assertEquals("foo.Bar.zaz(I)V", methodArgNames.getMethods()[1]);
    Assert.assertEquals("i", methodArgNames.lookup("foo.Bar.zaz(I)V")[0]);
  }

  public void testSuperCall() throws Exception {
    ClassDefBuilder foo = new ClassDefBuilder("foo.Bar");
    MethodDefBuilder zaz = new MethodDefBuilder("toString");
    Statement superCall =
        new MethodCall(superRef, new Signature(toRef("java.lang.Object"), "toString", noTypes,
            toRef("java.lang.String")), noExpressions);
    zaz.stmts = list(superCall);
    zaz.returnType = toRef("java.lang.String");
    foo.classBody = list(foo.defaultCstr, zaz.build());

    assertEquals(process(foo), "testSuperCall");
  }

  private static Statement newWindowAlert(String message) {
    Expression alertParam = new StringLiteral(message);
    return new StaticMethodCall(toRef("gwt.Window"), new Signature(toRef("gwt.Window"), "alert",
        list((Type) toRef("java.lang.String")), new Primitive("void")), list(alertParam));
  }

  private static Statement newWindowAlertVar(String varName) {
    Expression alertParam = new VarRef(varName);
    return new StaticMethodCall(toRef("gwt.Window"), new Signature(toRef("gwt.Window"), "alert",
        list((Type) toRef("java.lang.String")), new Primitive("void")), list(alertParam));
  }

  private static ConstructorCall newSuperCstrCall(String superClassName) {
    // jribble uses "super" when calling super constructors, e.g.:
    // (Ljava/lang/Object;::super()V;)();
    return new ConstructorCall(new Signature(toRef(superClassName), "super", noTypes,
        new Primitive("void")), noExpressions);
  }

  private static class MethodDefBuilder {
    private final String name;
    Set<String> modifs = new HashSet<String>();
    List<ParamDef> params = list();
    List<Statement> stmts = list();
    Type returnType = new Primitive("void");

    private MethodDefBuilder(String name) {
      this.name = name;
    }

    private ClassBodyElement build() {
      return new MethodDef(modifs, returnType, name, params, new Some<Block>(new Block(stmts)));
    }

    private ClassBodyElement buildCstr() {
      return new Constructor(modifs, name, params, new Block(stmts));
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

  private static class ClassDefBuilder {
    private final Ref name;
    private Set<String> modifs = new HashSet<String>();
    private Option<Ref> ext = new Some<Ref>(toRef("java.lang.Object"));
    private List<Ref> impls = list();
    private List<ClassBodyElement> classBody = list();
    private ClassBodyElement defaultCstr;

    private ClassDefBuilder(String name) {
      this.name = toRef(name);
      MethodDefBuilder cstr = new MethodDefBuilder("this");
      cstr.stmts = list((Statement) newSuperCstrCall("java.lang.Object"));
      defaultCstr = cstr.buildCstr();
    }

    private ClassDef build() {
      return new ClassDef(modifs, name, ext, impls, classBody);
    }
  }

  private static <T> List<T> list() {
    return List$.MODULE$.empty();
  }

  private static <T> List<T> list(T t) {
    List<T> result = List$.MODULE$.empty();
    result = new $colon$colon<T>(t, result);
    return result;
  }

  private static <T> List<T> list(T... ts) {
    List<T> result = List$.MODULE$.empty();
    for (int i = ts.length; i > 0; i--) {
      result = new $colon$colon<T>(ts[i - 1], result);
    }
    return result;
  }

  private static <T> Set<T> set(T... ts) {
    return list(ts).toSet();
  }

  private static JDeclaredType process(ClassDefBuilder foo) {
    return new JribbleAstBuilder().process(foo.build()).types.get(0);
  }

}
