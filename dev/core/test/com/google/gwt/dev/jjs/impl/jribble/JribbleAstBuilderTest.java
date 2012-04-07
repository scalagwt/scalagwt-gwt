/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.jjs.impl.jribble;

import static com.google.gwt.dev.jjs.impl.jribble.AstUtils.*;

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
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Block;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Catch;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Declaration;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.DeclaredType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Expr;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Expr.ExprType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.GlobalName;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Literal;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Literal.LiteralType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Method;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.MethodCall;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.MethodSignature;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Modifiers;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.ParamDef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.PrimitiveType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Statement;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Statement.StatementType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Type;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.VarRef;
import com.google.gwt.dev.util.AbstractTextOutput;
import com.google.gwt.dev.util.DefaultTextOutput;
import com.google.gwt.dev.util.TextOutput;
import com.google.gwt.thirdparty.guava.common.base.Charsets;
import com.google.gwt.thirdparty.guava.common.io.Resources;

import junit.framework.Assert;
import junit.framework.TestCase;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link JribbleAstBuilder}.
 */
public class JribbleAstBuilderTest extends TestCase {

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
      Statement stmt = Statement.newBuilder().setType(StatementType.Block)
        .setBlock(block).build();
      b.setBody(stmt);
      return b.build();
    }

    private Method buildCstr() {
      b.setIsConstructor(true);
      b.setReturnType(returnType);
      b.setName(name);
      b.addAllParamDef(params);
      Block block = Block.newBuilder().addAllStatement(stmts).build();
      Statement stmt = Statement.newBuilder().setType(StatementType.Block)
        .setBlock(block).build();
      b.setBody(stmt);
      return b.build();
    }
  }
  private static final List<Type> noTypes = Collections.emptyList();

  private static final Expr superRef =
    Expr.newBuilder().setType(ExprType.SuperRef).build();

  private static final Expr thisRef =
    Expr.newBuilder().setType(ExprType.ThisRef).build();

  private static Expr newSuperCstrCall(String superClassName) {
    // jribble uses "super" when calling super constructors, e.g.:
    // (Ljava/lang/Object;::super()V;)();
    MethodSignature.Builder msb = MethodSignature.newBuilder();
    msb.setName("new");
    msb.setOwner(toGlobalName(superClassName));
    msb.setReturnType(voidType());
    MethodCall.Builder mb = MethodCall.newBuilder();
    mb.setSignature(msb.build());
    return Expr.newBuilder().setType(ExprType.MethodCall)
      .setMethodCall(mb.build()).build();
  }

  private static Statement newWindowAlert(Expr message) {
    MethodCall.Builder b = MethodCall.newBuilder();
    MethodSignature s = signature("gwt.Window", "alert",
        asList(toGlobalNameType("java.lang.String")), voidType());
    b.setSignature(s);
    b.addArgument(message);
    return statement(expr(b.build()));
  }

  private static Statement newWindowAlert(String message) {
    Literal lit = Literal.newBuilder().setType(LiteralType.String)
      .setStringValue(message).build();
    Expr alertParam = Expr.newBuilder().setType(ExprType.Literal)
      .setLiteral(lit).build();
    return newWindowAlert(alertParam);
  }

  private static Statement newWindowAlertVar(String varName) {
    VarRef varRef = VarRef.newBuilder().setName(varName).build();
    return newWindowAlert(expr(varRef));
  }

  private static JDeclaredType process(DeclaredTypeBuilder foo) {
    return new JribbleAstBuilder().process(foo.build()).types.get(0);
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

    Statement s5 = varDef(stringA, "b", arrayInitializer(stringType()));

    zaz.stmts = asList(s1, s2, s3, s4, s5);
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
    // String[] {}
    JDeclarationStatement decl3 = (JDeclarationStatement) ((JMethodBody) zazMethod.getBody()).getStatements().get(4);
    Assert.assertEquals(null, ((JNewArray) decl3.getInitializer()).dims);
    Assert.assertEquals(Collections.EMPTY_LIST, ((JNewArray) decl3.getInitializer()).initializers);
    Assert.assertEquals(1, ((JNewArray) decl3.getInitializer()).getArrayType().getDims());
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

  public void testEmptyClass() throws Exception {
    DeclaredTypeBuilder foo = new DeclaredTypeBuilder("foo.Bar", false);
    foo.ext = null;
    foo.classBody = asList(foo.defaultCstr);
    JDeclaredType fooType = process(foo);
    assertEquals(fooType, "testEmptyClass");
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
        assignment(fieldRef(thisRef, "foo.Bar", "f1", stringType()), literal("f11")));
    
    Statement assignf2 = statement(
        assignment(fieldRef(varRef("other"), "foo.Bar", "f2", stringType()), literal("f22")));
    
    Statement assignf3 = statement(
        assignment(fieldRef(null, "foo.Bar", "f3", stringType()), literal("f33")));
    
    Statement assignfOther = statement(
        assignment(fieldRef(null, "foo.Other", "i", intType()), literal(1)));

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

  /**
   * Dumps @{code node} and checks if result is equal to one stored
   * in file described by @{code name}. In case both dumps are not
   * equal the actual result is dumped to file so it can be further
   * inspected.
   * @param node AST to be dumped
   * @param name part of file name that describes the dump.
   * @throws IOException
   */
  private void assertEquals(JNode node, String name) throws IOException {
    TextOutput out = new DefaultTextOutput(false);
    SourceGenerationVisitor v = new SourceGenerationVisitor(out);
    v.accept(node);
    final String actual = out.toString();

    final String expected;
    {
      String resourceName =
        getClass().getName().replace('.', '/') + "." + name + ".ast";
      java.net.URL url = Resources.getResource(resourceName);
      expected = Resources.toString(url, Charsets.UTF_8);
    }

    try {
      Assert.assertEquals(expected, actual);
    } catch (AssertionError ex) {
      dump(node, name);
      throw ex;
    }
  }

  /**
   * Dumps {@code node} to file relative to current directory.
   * @param node AST to be dumped.
   * @param name part of file name that describes the dump.
   * @throws IOException
   */
  private void dump(JNode node, String name) throws IOException {
    String dumpFilePath =
        getClass().getName().replace('.', '/') + "." + name + ".ast.actual";
    File dumpFile = new File(dumpFilePath);
    System.out.println("Dumping ast to " + dumpFile.getCanonicalPath());
    dumpFile.getParentFile().mkdirs();
    dumpFile.createNewFile();

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

}
