package com.google.gwt.dev.jjs.impl.jribble;

import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.*;

import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Declaration.DeclarationType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Expr.ExprType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Literal.LiteralType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Statement.StatementType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Type.TypeType;
import com.google.gwt.dev.protobuf.Descriptors.FieldDescriptor;

import static java.util.Arrays.asList;

import java.util.List;

public class AstUtils {
  
  static GlobalName toGlobalName(String name) {
    GlobalName.Builder b = GlobalName.newBuilder();
    int i = name.lastIndexOf(".");
    if (i == -1) {
      b.setName(name);
    } else {
      b.setPkg(name.substring(0, i));
      b.setName(name.substring(i + 1));
    }
    return b.build();
  }
  
  static Type toGlobalNameType(String name) {
    return Type.newBuilder().setType(TypeType.Named).
      setNamedType(toGlobalName(name)).build();
  }
  
  static Type voidType() {
    return Type.newBuilder().setType(TypeType.Void).build();
  }
  
  static Type stringType() {
    return toGlobalNameType("java.lang.String");
  }
  
  static Type intType() {
    return Type.newBuilder().setType(TypeType.Primitive).
      setPrimitiveType(PrimitiveType.Int).build();
  }

  static MethodSignature signature(String owner, String name, List<Type> paramType, 
      Type returnType) {
    return MethodSignature.newBuilder().setOwner(toGlobalName(owner)).setName(name).
      addAllParamType(paramType).setReturnType(returnType).build();
  }
  
  static Expr expr(MethodCall methodCall) {
    return Expr.newBuilder().setType(ExprType.MethodCall).
      setMethodCall(methodCall).build();
  }
  
  static Expr expr(VarRef varRef) {
    return Expr.newBuilder().setType(ExprType.VarRef).setVarRef(varRef).
      build();
  }
  
  static Statement statement(Expr expr) {
    return Statement.newBuilder().setType(StatementType.Expr).
      setExpr(expr).build();
  }
  
  static Declaration declaration(Method method) {
    return Declaration.newBuilder().setType(DeclarationType.Method).
      setModifiers(Modifiers.getDefaultInstance()).setMethod(method).build();
  }
  
  static Declaration declaration(Modifiers mods, Method method) {
    return Declaration.newBuilder().setType(DeclarationType.Method).
      setModifiers(mods).setMethod(method).build();
  }
  
  static Declaration declaration(Modifiers mods, FieldDef field) {
    return Declaration.newBuilder().setType(DeclarationType.Field).
      setModifiers(mods).setFieldDef(field).build();
  }
  
  static ParamDef paramDef(String name, Type tpe) {
    return ParamDef.newBuilder().setName(name).setTpe(tpe).build();
  }
  
  static Type primitive(PrimitiveType tpe) {
    return Type.newBuilder().setType(TypeType.Primitive).
      setPrimitiveType(tpe).build();
  }
  
  static Expr methodCall(Expr receiver, MethodSignature signature, Expr... args) {
    MethodCall.Builder b = MethodCall.newBuilder();
    if (receiver != null) {
      b.setReceiver(receiver);
    }
    b.setSignature(signature);
    b.addAllArgument(asList(args));
    return expr(b.build());
  }
  
  static Expr varRef(String name) {
    return expr(VarRef.newBuilder().setName(name).build());
  }
  
  static Type arrayType(Type elementType) {
    return Type.newBuilder().setType(TypeType.Array).
      setArrayElementType(elementType).build();
  }
  
  static Statement varDef(Type tpe, String name, Expr initializer) {
    VarDef.Builder b = VarDef.newBuilder();
    b.setTpe(tpe).setName(name);
    if (initializer != null) {
      b.setInitializer(initializer);
    }
    return Statement.newBuilder().setType(StatementType.VarDef).
      setVarDef(b.build()).build();
  }
  
  static Expr newArray(Type elementType, Expr... dims) {
    NewArray n = NewArray.newBuilder().setElementType(elementType).
      setDimensions(dims.length).addAllDimensionExpr(asList(dims)).build();
    return Expr.newBuilder().setType(ExprType.NewArray).setNewArray(n).build();
  }
  
  static Expr arrayInitializer(Type elementType, Expr... values) {
    NewArray n = NewArray.newBuilder().setElementType(elementType).
      setDimensions(1).addAllInitExpr(asList(values)).build();
  return Expr.newBuilder().setType(ExprType.NewArray).setNewArray(n).build();
  }
  
  static Expr literal(int x) {
    Literal l = Literal.newBuilder().setType(LiteralType.Int).
      setIntValue(x).build();
    return Expr.newBuilder().setType(ExprType.Literal).setLiteral(l).build();
  }
  
  static Expr literal(String x) {
    Literal l = Literal.newBuilder().setType(LiteralType.String).
      setStringValue(x).build();
    return Expr.newBuilder().setType(ExprType.Literal).setLiteral(l).build();
  }
  
  static Expr assignment(Expr lhs, Expr rhs) {
    Assignment a = Assignment.newBuilder().setLhs(lhs).setRhs(rhs).build();
    return Expr.newBuilder().setType(ExprType.Assignment).setAssignment(a).
      build();
  }
  
  static Expr arrayRef(Expr array, Expr index) {
    ArrayRef ref = ArrayRef.newBuilder().setArray(array).setIndex(index).build();
    return Expr.newBuilder().setType(ExprType.ArrayRef).setArrayRef(ref).build();
  }
  
  static FieldDef fieldDef(Type tpe, String name, Expr initializer) {
    FieldDef.Builder b = FieldDef.newBuilder();
    b.setTpe(tpe).setName(name);
    if (initializer != null) {
      b.setInitializer(initializer);
    }
    return b.build();
  }
  
  static String capitalize(String x) {
    return Character.toUpperCase(x.charAt(0)) + x.substring(1);
  }
  
  static Modifiers modifiers(String... modifs) {
    Modifiers.Builder b = Modifiers.newBuilder();
    for (String x : asList(modifs)) {
      FieldDescriptor desc = Modifiers.getDescriptor().
        findFieldByName("is" + capitalize(x));
      b.setField(desc, Boolean.TRUE);
    }
    return b.build();
  }
  
  static Expr fieldRef(Expr qualifier, String enclosingType, String name, Type tpe) {
    FieldRef.Builder b = FieldRef.newBuilder();
    b.setEnclosingType(toGlobalName(enclosingType)).setName(name);
    b.setTpe(tpe);
    if (qualifier != null) {
      b.setQualifier(qualifier);
    }
    return Expr.newBuilder().setType(ExprType.FieldRef).
      setFieldRef(b.build()).build();
  }
  
  static Statement block(Statement... stmts) {
    Block b = Block.newBuilder().addAllStatement(asList(stmts)).build();
    return Statement.newBuilder().setType(StatementType.Block).
      setBlock(b).build();
  }
  
  static Expr newObject(String clazz, MethodSignature signature, Expr... args) {
    assert signature.getName().equals("new");
    NewObject x = NewObject.newBuilder().setClazz(toGlobalName(clazz)).
      setSignature(signature).addAllArgument(asList(args)).build();
    return Expr.newBuilder().setType(ExprType.NewObject).setNewObject(x).build();
  }
  
  static Catch catchh(String tpe, String param, Statement body) {
    return Catch.newBuilder().setTpe(toGlobalName(tpe)).setParam(param).
      setBody(body).build();
  }
  
  static Statement tryy(Statement block, Statement finalizer, Catch... catches) {
    Try.Builder b = Try.newBuilder();
    b.setBlock(block).addAllCatch(asList(catches));
    if (finalizer != null) {
      b.setFinalizer(finalizer);
    }
    return Statement.newBuilder().setType(StatementType.Try).setTryStat(b.build()).build();
  }
  
  static Statement returnn(Expr expr) {
    Return.Builder b = Return.newBuilder();
    if (expr != null) {
      b.setExpression(expr);
    }
    return Statement.newBuilder().setType(StatementType.Return).
      setReturnStat(b.build()).build();
  }

}
