/*
 * Copyright 2010 Google Inc.
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

package com.google.gwt.dev.jjs.impl;

import com.google.gwt.dev.javac.MethodArgNamesLookup;
import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.jjs.SourceInfo;
import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.JAbsentArrayDimension;
import com.google.gwt.dev.jjs.ast.JArrayLength;
import com.google.gwt.dev.jjs.ast.JArrayRef;
import com.google.gwt.dev.jjs.ast.JArrayType;
import com.google.gwt.dev.jjs.ast.JBinaryOperation;
import com.google.gwt.dev.jjs.ast.JBinaryOperator;
import com.google.gwt.dev.jjs.ast.JBlock;
import com.google.gwt.dev.jjs.ast.JBooleanLiteral;
import com.google.gwt.dev.jjs.ast.JBreakStatement;
import com.google.gwt.dev.jjs.ast.JCaseStatement;
import com.google.gwt.dev.jjs.ast.JCastOperation;
import com.google.gwt.dev.jjs.ast.JCharLiteral;
import com.google.gwt.dev.jjs.ast.JClassLiteral;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.ast.JConditional;
import com.google.gwt.dev.jjs.ast.JConstructor;
import com.google.gwt.dev.jjs.ast.JContinueStatement;
import com.google.gwt.dev.jjs.ast.JDeclarationStatement;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.jjs.ast.JDoubleLiteral;
import com.google.gwt.dev.jjs.ast.JExpression;
import com.google.gwt.dev.jjs.ast.JExpressionStatement;
import com.google.gwt.dev.jjs.ast.JField;
import com.google.gwt.dev.jjs.ast.JField.Disposition;
import com.google.gwt.dev.jjs.ast.AccessModifier;
import com.google.gwt.dev.jjs.ast.JFieldRef;
import com.google.gwt.dev.jjs.ast.JFloatLiteral;
import com.google.gwt.dev.jjs.ast.JIfStatement;
import com.google.gwt.dev.jjs.ast.JInstanceOf;
import com.google.gwt.dev.jjs.ast.JIntLiteral;
import com.google.gwt.dev.jjs.ast.JInterfaceType;
import com.google.gwt.dev.jjs.ast.JLabel;
import com.google.gwt.dev.jjs.ast.JLabeledStatement;
import com.google.gwt.dev.jjs.ast.JLiteral;
import com.google.gwt.dev.jjs.ast.JLocal;
import com.google.gwt.dev.jjs.ast.JLocalRef;
import com.google.gwt.dev.jjs.ast.JLongLiteral;
import com.google.gwt.dev.jjs.ast.JMethod;
import com.google.gwt.dev.jjs.ast.JMethodBody;
import com.google.gwt.dev.jjs.ast.JMethodCall;
import com.google.gwt.dev.jjs.ast.JNewArray;
import com.google.gwt.dev.jjs.ast.JNewInstance;
import com.google.gwt.dev.jjs.ast.JNullLiteral;
import com.google.gwt.dev.jjs.ast.JParameter;
import com.google.gwt.dev.jjs.ast.JParameterRef;
import com.google.gwt.dev.jjs.ast.JPrefixOperation;
import com.google.gwt.dev.jjs.ast.JPrimitiveType;
import com.google.gwt.dev.jjs.ast.JProgram;
import com.google.gwt.dev.jjs.ast.JReferenceType;
import com.google.gwt.dev.jjs.ast.JReturnStatement;
import com.google.gwt.dev.jjs.ast.JStatement;
import com.google.gwt.dev.jjs.ast.JStringLiteral;
import com.google.gwt.dev.jjs.ast.JSwitchStatement;
import com.google.gwt.dev.jjs.ast.JThisRef;
import com.google.gwt.dev.jjs.ast.JThrowStatement;
import com.google.gwt.dev.jjs.ast.JTryStatement;
import com.google.gwt.dev.jjs.ast.JType;
import com.google.gwt.dev.jjs.ast.JUnaryOperator;
import com.google.gwt.dev.jjs.ast.JVariableRef;
import com.google.gwt.dev.jjs.ast.JWhileStatement;
import com.google.gwt.dev.util.StringInterner;
import com.google.gwt.dev.util.Name.BinaryName;
import com.google.jribble.ast.And;
import com.google.jribble.ast.ArrayInitializer;
import com.google.jribble.ast.ArrayLength;
import com.google.jribble.ast.ArrayRef;
import com.google.jribble.ast.Assignment;
import com.google.jribble.ast.BinaryOp;
import com.google.jribble.ast.BitAnd;
import com.google.jribble.ast.BitLShift;
import com.google.jribble.ast.BitNot;
import com.google.jribble.ast.BitOr;
import com.google.jribble.ast.BitRShift;
import com.google.jribble.ast.BitUnsignedRShift;
import com.google.jribble.ast.BitXor;
import com.google.jribble.ast.Block;
import com.google.jribble.ast.BooleanLiteral;
import com.google.jribble.ast.Break;
import com.google.jribble.ast.Cast;
import com.google.jribble.ast.CharLiteral;
import com.google.jribble.ast.ClassDef;
import com.google.jribble.ast.ClassOf;
import com.google.jribble.ast.Conditional;
import com.google.jribble.ast.Constructor;
import com.google.jribble.ast.ConstructorCall;
import com.google.jribble.ast.Continue;
import com.google.jribble.ast.DeclaredType;
import com.google.jribble.ast.Divide;
import com.google.jribble.ast.DoubleLiteral;
import com.google.jribble.ast.Equal;
import com.google.jribble.ast.Expression;
import com.google.jribble.ast.FieldDef;
import com.google.jribble.ast.FieldRef;
import com.google.jribble.ast.FloatLiteral;
import com.google.jribble.ast.Greater;
import com.google.jribble.ast.GreaterOrEqual;
import com.google.jribble.ast.If;
import com.google.jribble.ast.InstanceOf;
import com.google.jribble.ast.IntLiteral;
import com.google.jribble.ast.InterfaceDef;
import com.google.jribble.ast.Lesser;
import com.google.jribble.ast.LesserOrEqual;
import com.google.jribble.ast.Literal;
import com.google.jribble.ast.LongLiteral;
import com.google.jribble.ast.MethodCall;
import com.google.jribble.ast.MethodDef;
import com.google.jribble.ast.Minus;
import com.google.jribble.ast.Modulus;
import com.google.jribble.ast.Multiply;
import com.google.jribble.ast.NewArray;
import com.google.jribble.ast.NewCall;
import com.google.jribble.ast.Not;
import com.google.jribble.ast.NotEqual;
import com.google.jribble.ast.NullLiteral$;
import com.google.jribble.ast.Or;
import com.google.jribble.ast.ParamDef;
import com.google.jribble.ast.Plus;
import com.google.jribble.ast.Ref;
import com.google.jribble.ast.Return;
import com.google.jribble.ast.Signature;
import com.google.jribble.ast.Statement;
import com.google.jribble.ast.StaticFieldRef;
import com.google.jribble.ast.StaticMethodCall;
import com.google.jribble.ast.StringLiteral;
import com.google.jribble.ast.SuperRef$;
import com.google.jribble.ast.Switch;
import com.google.jribble.ast.ThisRef$;
import com.google.jribble.ast.Throw;
import com.google.jribble.ast.Try;
import com.google.jribble.ast.Type;
import com.google.jribble.ast.UnaryMinus;
import com.google.jribble.ast.UnaryOp;
import com.google.jribble.ast.VarDef;
import com.google.jribble.ast.VarRef;
import com.google.jribble.ast.While;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import scala.Option;
import scala.Tuple2;
import scala.Tuple3;

/**
 * Class that transforms jribble AST into a per-CompilationUnit GWT mini AST.
 *
 * TODO(stephenh) Interning
 * https://github.com/scalagwt/scalagwt-gwt/issues/7
 */
public class JribbleAstBuilder {

  public static class Result {
    public final List<JDeclaredType> types;
    public final Set<String> apiRefs;
    public final MethodArgNamesLookup methodArgNames;

    public Result(List<JDeclaredType> types, Set<String> apiRefs,
        MethodArgNamesLookup methodArgNames) {
      this.types = types;
      this.apiRefs = apiRefs;
      this.methodArgNames = methodArgNames;
    }
  }

  private static final StringInterner stringInterner = StringInterner.get();
  private static final SourceOrigin UNKNOWN = SourceOrigin.UNKNOWN;
  private final JribbleReferenceMapper mapper = new JribbleReferenceMapper();
  // reset on each invocation of process
  private ArrayList<JDeclaredType> newTypes;
  private MethodArgNamesLookup methodArgNames;
  private JClassType javaLangClass = mapper.getClassType("java.lang.Class");
  private JClassType javaLangString = mapper.getClassType("java.lang.String");

  public Result process(DeclaredType declaredType) {
    newTypes = new ArrayList<JDeclaredType>();
    methodArgNames = new MethodArgNamesLookup();
    try {
      // Create the new source type
      createType(declaredType);
      // Resolve super type / interface relationships.
      resolveTypeRefs(declaredType);
      // Create methods, cstrs, and fields as non-external
      createMembers(declaredType);
      // Fill in the methods
      buildTheCode(declaredType);
      return new Result(newTypes, binaryToInternal(mapper.getTouchedTypes()), methodArgNames);
    } finally {
      // Clean up.
      mapper.clearSource();
      methodArgNames = null;
      newTypes = null;
    }
  }

  /** Creates a non-external type AST and puts it in the mapper. */
  private void createType(DeclaredType jrType) {
    JDeclaredType gwtType;
    if (jrType instanceof ClassDef) {
      ClassDef jrClassDef = (ClassDef) jrType;
      boolean isAbstract = jrClassDef.modifs().contains("abstract");
      boolean isFinal = jrClassDef.modifs().contains("final");
      gwtType = new JClassType(UNKNOWN, intern(jrClassDef.name().javaName()), isAbstract, isFinal);
    } else if (jrType instanceof InterfaceDef) {
      gwtType = new JInterfaceType(UNKNOWN, intern(jrType.name().javaName()));
    } else {
      throw new RuntimeException("Unhandled type " + jrType);
    }
    // would add inner classes here if we had them all in a single CompilationUnit
    mapper.setSourceType(jrType, gwtType);
    newTypes.add(gwtType);
  }

  /** Creates potentially-external ASTs for our AST's super class and interfaces. */
  private void resolveTypeRefs(DeclaredType jrType) {
    if (jrType instanceof ClassDef) {
      ClassDef jrClassDef = (ClassDef) jrType;
      JClassType gwtClassType = mapper.getClassType(jrType.name().javaName());
      if (jrClassDef.ext().isDefined()) {
        gwtClassType.setSuperClass(mapper.getClassType(jrClassDef.ext().get().javaName()));
      } else {
        // package objects sometimes? don't have super classes but should in GWT
        gwtClassType.setSuperClass(mapper.getClassType("java.lang.Object"));
      }
      for (Ref ref : jrClassDef.jimplements()) {
        gwtClassType.addImplements(mapper.getInterfaceType(ref.javaName()));
      }
    } else if (jrType instanceof InterfaceDef) {
      InterfaceDef jrIntDef = (InterfaceDef) jrType;
      JInterfaceType gwtIntType = mapper.getInterfaceType(jrType.name().javaName());
      for (Ref ref : jrIntDef.jext()) {
        gwtIntType.addImplements(mapper.getInterfaceType(ref.javaName()));
      }
    } else {
      throw new RuntimeException("Unhandled type " + jrType);
    }
  }

  private void createMembers(DeclaredType jrType) {
    JDeclaredType gwtType;
    if (jrType instanceof ClassDef) {
      gwtType = mapper.getClassType(jrType.name().javaName());
    } else if (jrType instanceof InterfaceDef) {
      gwtType = mapper.getInterfaceType(jrType.name().javaName());
    } else {
      throw new RuntimeException("Unhandled type " + jrType);
    }

    assert gwtType.getMethods().size() == 0;
    createSyntheticMethod(UNKNOWN, "$clinit", gwtType, JPrimitiveType.VOID, false, true, true, true);

    if (gwtType instanceof JClassType) {
      assert gwtType.getMethods().size() == 1;
      createSyntheticMethod(UNKNOWN, "$init", gwtType, JPrimitiveType.VOID, false, false, true,
          true);

      // TODO Check JSORestrictionsChecker if this is a JSO
      // https://github.com/scalagwt/scalagwt-gwt/issues/8
      assert gwtType.getMethods().size() == 2;
      createSyntheticMethod(UNKNOWN, "getClass", gwtType, javaLangClass, false, false, false, false);

      for (FieldDef jrField : ((ClassDef) jrType).jfieldDefs()) {
        createField(gwtType, (ClassDef) jrType, jrField);
      }
      for (MethodDef jrMethod : ((ClassDef) jrType).jmethodDefs()) {
        createMethod(gwtType, jrType, jrMethod);
      }
      for (Constructor cstr : ((ClassDef) jrType).jconstructors()) {
        createConstructor(gwtType, jrType, cstr);
      }
    } else if (gwtType instanceof JInterfaceType) {
      for (MethodDef jrMethod : ((InterfaceDef) jrType).jbody()) {
        createMethod(gwtType, jrType, jrMethod);
      }
    } else {
      throw new RuntimeException("Unhandled type " + gwtType);
    }
    // if we had multiple types in the CompilationUnits, create members for each of them here
  }

  private void buildTheCode(DeclaredType jrType) {
    if (jrType instanceof ClassDef) {
      new AstWalker().classDef((ClassDef) jrType);
    } else if (jrType instanceof InterfaceDef) {
      // interfaces already have their methods created from createMembers
    } else {
      throw new RuntimeException("Unhandled type " + jrType);
    }
  }

  /** Creates a field, without the allocation yet. */
  private void createField(JDeclaredType enclosingType, ClassDef jrClassDef, FieldDef jrField) {
    boolean isStatic = jrField.modifs().contains("static");
    JType type = mapper.getType(jrField.typ());
    JField field =
        new JField(UNKNOWN, intern(jrField.name()), enclosingType, type, isStatic,
            getFieldDisposition(jrField));
    enclosingType.addField(field);
    mapper.setSourceField(jrClassDef, jrField, field);
  }

  /** Creates a source method, without the code inside yet. */
  private void createMethod(JDeclaredType gwtType, DeclaredType jrType, MethodDef jrMethod) {
    boolean isAbstract = jrMethod.modifs().contains("abstract") || jrType instanceof InterfaceDef;
    boolean isStatic = jrMethod.modifs().contains("static");
    boolean isFinal = jrMethod.modifs().contains("final");
    JMethod method =
        new JMethod(UNKNOWN, jrMethod.name(), gwtType, mapper.getType(jrMethod.returnType()),
            isAbstract, isStatic, isFinal, access(jrMethod.modifs()));
    for (ParamDef param : jrMethod.jparams()) {
      // param modifs?
      method.addParam(new JParameter(UNKNOWN, param.name(), mapper.getType(param.typ()), false,
          false, method));
    }
    method.freezeParamTypes();
    if (jrType instanceof ClassDef) {
      method.setBody(new JMethodBody(UNKNOWN));
    }
    gwtType.addMethod(method);
    methodArgNames.store(gwtType.getName(), method);
    mapper.setSourceMethod(jrMethod.signature(jrType.name()), method);
  }

  /** Creates a source cstr, without the code inside yet. */
  private void createConstructor(JDeclaredType gwtType, DeclaredType jrType, Constructor cstr) {
    JConstructor method = new JConstructor(UNKNOWN, (JClassType) gwtType);
    for (ParamDef param : cstr.jparams()) {
      // param modifs?
      method.addParam(new JParameter(UNKNOWN, param.name(), mapper.getType(param.typ()), false,
          false, method));
    }
    method.freezeParamTypes();
    gwtType.addMethod(method);
    methodArgNames.store(gwtType.getName(), method);
    method.setBody(new JMethodBody(UNKNOWN));
    mapper.setSourceMethod(cstr.signature(jrType.name()), method);
  }

  private static final class LocalStack {
    private final Stack<Map<String, JLocal>> varStack = new Stack<Map<String, JLocal>>();
    private final Map<String, JParameter> params;
    private final Map<String, JLabel> labels = new HashMap<String, JLabel>();
    private final JClassType enclosingType;
    private final JMethodBody enclosingBody;

    public LocalStack(JClassType enclosingType, JMethodBody enclosingBody,
        Map<String, JParameter> params) {
      this.enclosingType = enclosingType;
      this.enclosingBody = enclosingBody;
      this.params = params;
    }

    public void addVar(String name, JLocal x) {
      Map<String, JLocal> peak = varStack.peek();
      assert !peak.containsKey(name) : "redeclared variable " + name;
      peak.put(name, x);
    }

    public void pushLabel(JLabel x) {
      assert !labels.containsKey(x.getName());
      labels.put(x.getName(), x);
    }

    public void popLabel(String name) {
      assert labels.containsKey(name);
      labels.remove(name);
    }

    public JLabel getLabel(String name) {
      if (!labels.containsKey(name)) {
        throw new InternalCompilerException(String.format("Failed to find %1s", name));
      }
      return labels.get(name);
    }

    public void pushBlock() {
      varStack.push(new HashMap<String, JLocal>());
    }

    public void popBlock() {
      varStack.pop();
    }

    public JVariableRef resolveLocal(String name) {
      for (int i = varStack.size() - 1; i >= 0; i--) {
        JLocal local = varStack.get(i).get(name);
        if (local != null) {
          return new JLocalRef(UNKNOWN, local);
        }
      }
      JParameter param = params.get(name);
      if (param != null) {
        return new JParameterRef(UNKNOWN, param);
      }
      throw new InternalCompilerException(String.format("Failed to find %1s", name));
    }

    public JClassType getEnclosingType() {
      return enclosingType;
    }

    public JMethodBody getEnclosingBody() {
      return enclosingBody;
    }
  }

  /** Given a top-level ClassDef/InterfaceDef, creates a full GWT AST. */
  private class AstWalker {

    private final Expression superRef = SuperRef$.MODULE$;

    private JExpressionStatement assignment(Assignment assignment, LocalStack local) {
      JExpression lhs = expression(assignment.lhs(), local);
      JExpression rhs = expression(assignment.rhs(), local);
      return JProgram.createAssignmentStmt(UNKNOWN, lhs, rhs);
    }

    private void block(Block block, JBlock jblock, LocalStack local) {
      local.pushBlock();
      for (Statement x : block.jstatements()) {
        final JStatement js;
        if (x instanceof ConstructorCall) {
          js = constructorCall((ConstructorCall) x, local).makeStatement();
        } else {
          js = methodStatement(x, local);
        }
        jblock.addStmt(js);
      }
      local.popBlock();
    }

    private void classDef(ClassDef def) {
      JClassType clazz = (JClassType) mapper.getClassType(def.name().javaName());
      assert !clazz.isExternal();
      try {
        for (Constructor i : def.jconstructors()) {
          constructor(i, def, clazz);
        }
        for (MethodDef i : def.jmethodDefs()) {
          methodDef(i, clazz, def);
        }
        for (FieldDef i : def.jfieldDefs()) {
          fieldDef(i, def, clazz);
        }
        addClinitSuperCall(clazz);
        implementGetClass(clazz);
      } catch (InternalCompilerException e) {
        e.addNode(clazz);
        throw e;
      }
    }

    private void fieldDef(FieldDef fieldDef, ClassDef classDef, JClassType enclosingClass) {
      JField field =
          mapper.getField(classDef.name().javaName(), fieldDef.name(), fieldDef.modifs().contains(
              "static"));
      assert !field.isExternal();
      JMethod method;
      JFieldRef fieldRef;
      if (field.isStatic()) {
        fieldRef = new JFieldRef(UNKNOWN, null, field, enclosingClass);
        method = enclosingClass.getMethods().get(0);
        assert method.getName().equals("$clinit");
      } else {
        JExpression on = thisRef(enclosingClass);
        fieldRef = new JFieldRef(UNKNOWN, on, field, enclosingClass);
        method = enclosingClass.getMethods().get(1);
        assert method.getName().equals("$init");
      }
      JMethodBody body = (JMethodBody) method.getBody();
      if (fieldDef.value().isDefined()) {
        LocalStack local = new LocalStack(enclosingClass, body, new HashMap<String, JParameter>());
        local.pushBlock();
        JExpression expr = expression(fieldDef.value().get(), local);
        JStatement decl = new JDeclarationStatement(UNKNOWN, fieldRef, expr);
        body.getBlock().addStmt(decl);
      }
    }

    private JConditional conditional(Conditional conditional, LocalStack local) {
      JExpression condition = expression(conditional.condition(), local);
      JExpression then = expression(conditional.then(), local);
      JExpression elsee = expression(conditional.elsee(), local);
      return new JConditional(UNKNOWN, mapper.getType(conditional.typ()), condition, then, elsee);
    }

    private void constructor(Constructor constructor, ClassDef classDef, JClassType enclosingClass) {
      JMethod jc = mapper.getMethod(constructor.signature(classDef.name()), false, true);
      Map<String, JParameter> params = new HashMap<String, JParameter>();
      for (JParameter x : jc.getParams()) {
        params.put(x.getName(), x);
      }
      JMethodBody body = (JMethodBody) jc.getBody();
      LocalStack local = new LocalStack(enclosingClass, body, params);
      local.pushBlock();
      JBlock jblock = body.getBlock();
      if (!hasConstructorCall(classDef, constructor.body())) {
        JMethod initMethod = jc.getEnclosingType().getMethods().get(1);
        jblock.addStmt(new JMethodCall(UNKNOWN, thisRef((JClassType) jc.getEnclosingType()),
            initMethod).makeStatement());
      }
      block(constructor.body(), jblock, local);
    }

    private JExpression expression(Expression expr, LocalStack local) {
      if (expr instanceof Literal) {
        return literal((Literal) expr);
      } else if (expr instanceof VarRef) {
        return varRef((VarRef) expr, local);
      } else if (expr instanceof ThisRef$) {
        return thisRef(local.getEnclosingType());
      } else if (expr instanceof MethodCall) {
        return methodCall((MethodCall) expr, local);
      } else if (expr instanceof StaticMethodCall) {
        return staticMethodCall((StaticMethodCall) expr, local);
      } else if (expr instanceof VarRef) {
        return varRef((VarRef) expr, local);
      } else if (expr instanceof NewCall) {
        return newCall((NewCall) expr, local);
      } else if (expr instanceof Conditional) {
        return conditional((Conditional) expr, local);
      } else if (expr instanceof Cast) {
        return cast((Cast) expr, local);
      } else if (expr instanceof BinaryOp) {
        return binaryOp((BinaryOp) expr, local);
      } else if (expr instanceof FieldRef) {
        return fieldRef((FieldRef) expr, local);
      } else if (expr instanceof StaticFieldRef) {
        return staticFieldRef((StaticFieldRef) expr, local);
      } else if (expr instanceof ArrayRef) {
        return arrayRef((ArrayRef) expr, local);
      } else if (expr instanceof NewArray) {
        return newArray((NewArray) expr, local);
      } else if (expr instanceof ArrayLength) {
        return arrayLength((ArrayLength) expr, local);
      } else if (expr instanceof InstanceOf) {
        return instanceOf((InstanceOf) expr, local);
      } else if (expr instanceof ClassOf) {
        return new JClassLiteral(UNKNOWN, mapper.getType(((ClassOf) expr).typ()));
      } else if (expr instanceof ArrayInitializer) {
        return arrayInitializer((ArrayInitializer) expr, local);
      } else if (expr instanceof UnaryOp) {
        return unaryOp((UnaryOp) expr, local);
      } else if (expr instanceof SuperRef$) {
        return superRef(local);
      } else {
        throw new RuntimeException("to be implemented handling of " + expr);
      }
    }

    private JExpression superRef(LocalStack local) {
      // Oddly enough, super refs can be modeled as a this refs.
      // here we follow the logic from GenerateJavaAST class.
      return thisRef(local.getEnclosingType());
    }

    private JExpression unaryOp(UnaryOp expr, LocalStack local) {
      JUnaryOperator op;
      if (expr instanceof UnaryMinus) {
        op = JUnaryOperator.NEG;
      } else if (expr instanceof Not) {
        op = JUnaryOperator.NOT;
      } else if (expr instanceof BitNot) {
        op = JUnaryOperator.BIT_NOT;
      } else {
        throw new InternalCompilerException("Unsupported AST node " + expr);
      }
      return new JPrefixOperation(UNKNOWN, op, expression(expr.expression(), local));
    }

    private JNewArray arrayInitializer(ArrayInitializer expr, LocalStack local) {
      JArrayType arrayType = new JArrayType(mapper.getType(expr.typ()));
      List<JExpression> initializers = new LinkedList<JExpression>();
      for (Expression e : expr.jelements()) {
        initializers.add(expression(e, local));
      }
      return JNewArray.createInitializers(UNKNOWN, arrayType, initializers);
    }

    private JInstanceOf instanceOf(InstanceOf expr, LocalStack local) {
      JExpression on = expression(expr.on(), local);
      return new JInstanceOf(UNKNOWN, (JReferenceType) mapper.getType(expr.typ()), on);
    }

    private JArrayLength arrayLength(ArrayLength expr, LocalStack local) {
      JExpression on = expression(expr.on(), local);
      return new JArrayLength(UNKNOWN, on);
    }

    private JNewArray newArray(NewArray expr, LocalStack local) {
      JType type = mapper.getType(expr.typ()); // this is the element type
      for (int i = 0; i < expr.jdims().size(); i++) {
        type = new JArrayType(type);
      }
      List<JExpression> dims = new LinkedList<JExpression>();
      for (Option<Expression> i : expr.jdims()) {
        if (i.isDefined()) {
          dims.add(expression(i.get(), local));
        } else {
          dims.add(JAbsentArrayDimension.INSTANCE);
        }
      }
      return JNewArray.createDims(UNKNOWN, (JArrayType) type, dims);
    }

    private JArrayRef arrayRef(ArrayRef expr, LocalStack local) {
      return new JArrayRef(UNKNOWN, expression(expr.on(), local), expression(expr.index(), local));
    }

    private JFieldRef staticFieldRef(StaticFieldRef expr, LocalStack local) {
      JField field = mapper.getField(expr.on().javaName(), expr.name(), true);
      return new JFieldRef(UNKNOWN, null, field, local.getEnclosingType());
    }

    private JFieldRef fieldRef(FieldRef expr, LocalStack local) {
      JExpression on = expression(expr.on(), local);
      // TODO FieldRef.onType should be of type Ref and not Type
      JClassType typ = (JClassType) mapper.getType(expr.onType());
      JField field = mapper.getField(typ.getName(), expr.name(), false);
      return new JFieldRef(UNKNOWN, on, field, local.getEnclosingType());
    }

    private JBinaryOperation binaryOp(BinaryOp op, LocalStack local) {
      JExpression lhs = expression(op.lhs(), local);
      JExpression rhs = expression(op.rhs(), local);
      JBinaryOperator jop;
      JType type;
      // TODO(grek): Most of types below are wrong. It looks like we'll need
      // to store type information for operators too. :-(
      if (op instanceof Equal) {
        jop = JBinaryOperator.EQ;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof Multiply) {
        jop = JBinaryOperator.MUL;
        type = JPrimitiveType.INT;
      } else if (op instanceof Divide) {
        jop = JBinaryOperator.DIV;
        type = JPrimitiveType.INT;
      } else if (op instanceof Modulus) {
        jop = JBinaryOperator.MOD;
        type = JPrimitiveType.INT;
      } else if (op instanceof Minus) {
        jop = JBinaryOperator.SUB;
        type = JPrimitiveType.INT;
      } else if (op instanceof Plus) {
        if (lhs.getType() == javaLangString.getNonNull()
            || lhs.getType() == javaLangString
            || rhs.getType() == javaLangString.getNonNull()
            || rhs.getType() == javaLangString) {
          jop = JBinaryOperator.CONCAT;
          type = javaLangString;
        } else {
          jop = JBinaryOperator.ADD;
          type = JPrimitiveType.INT;
        }
      } else if (op instanceof Greater) {
        jop = JBinaryOperator.GT;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof GreaterOrEqual) {
        jop = JBinaryOperator.GTE;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof Lesser) {
        jop = JBinaryOperator.LT;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof LesserOrEqual) {
        jop = JBinaryOperator.LTE;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof NotEqual) {
        jop = JBinaryOperator.NEQ;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof And) {
        jop = JBinaryOperator.AND;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof Or) {
        jop = JBinaryOperator.OR;
        type = JPrimitiveType.BOOLEAN;
      } else if (op instanceof BitLShift) {
        jop = JBinaryOperator.SHL;
        type = JPrimitiveType.INT;
      } else if (op instanceof BitRShift) {
        jop = JBinaryOperator.SHR;
        type = JPrimitiveType.INT;
      } else if (op instanceof BitUnsignedRShift) {
        jop = JBinaryOperator.SHRU;
        type = JPrimitiveType.INT;
      } else if (op instanceof BitAnd) {
        jop = JBinaryOperator.BIT_AND;
        type = JPrimitiveType.INT;
      } else if (op instanceof BitOr) {
        jop = JBinaryOperator.BIT_OR;
        type = JPrimitiveType.INT;
      } else if (op instanceof BitXor) {
        jop = JBinaryOperator.BIT_XOR;
        type = JPrimitiveType.INT;
      } else {
        throw new RuntimeException("Uknown symbol " + op.symbol());
      }
      return new JBinaryOperation(UNKNOWN, type, jop, lhs, rhs);
    }

    private JCastOperation cast(Cast cast, LocalStack local) {
      JExpression on = expression(cast.on(), local);
      return new JCastOperation(UNKNOWN, mapper.getType(cast.typ()), on);
    }

    private JIfStatement ifStmt(If statement, LocalStack local) {
      JExpression condition = expression(statement.condition(), local);

      final JBlock then = new JBlock(UNKNOWN);
      block(statement.then(), then, local);
      JBlock elsee = null;
      if (statement.elsee().isDefined()) {
        elsee = new JBlock(UNKNOWN);
        block(statement.elsee().get(), elsee, local);
      }
      return new JIfStatement(UNKNOWN, condition, then, elsee);
    }

    private JLiteral literal(Literal literal) {
      if (literal instanceof StringLiteral) {
        return new JStringLiteral(UNKNOWN, ((StringLiteral) literal).v(), javaLangString);
      } else if (literal instanceof BooleanLiteral) {
        return JBooleanLiteral.get(((BooleanLiteral) literal).v());
      } else if (literal instanceof CharLiteral) {
        return JCharLiteral.get(((CharLiteral) literal).v());
      } else if (literal instanceof DoubleLiteral) {
        return JDoubleLiteral.get(((DoubleLiteral) literal).v());
      } else if (literal instanceof FloatLiteral) {
        return JFloatLiteral.get(((FloatLiteral) literal).v());
      } else if (literal instanceof IntLiteral) {
        return JIntLiteral.get(((IntLiteral) literal).v());
      } else if (literal instanceof LongLiteral) {
        return JLongLiteral.get(((LongLiteral) literal).v());
      } else if (literal instanceof NullLiteral$) {
        return JNullLiteral.INSTANCE;
      } else {
        throw new RuntimeException("to be implemented handling of " + literal);
      }
    }

    private JMethodCall methodCall(MethodCall call, LocalStack local) {
      JMethod method = mapper.getMethod(call.signature(), false, false);
      JExpression on = expression(call.on(), local);
      List<JExpression> params = params(call.signature().jparamTypes(), call.jparams(), local);
      JMethodCall jcall = new JMethodCall(UNKNOWN, on, method);
      jcall.addArgs(params);
      if (call.on() == superRef) {
        jcall.setStaticDispatchOnly();
      }
      return jcall;
    }

    private void methodDef(MethodDef def, JClassType enclosingClass, ClassDef classDef) {
      JMethod m =
          mapper.getMethod(def.signature(classDef.name()), def.modifs().contains("static"), false);
      Map<String, JParameter> params = new HashMap<String, JParameter>();
      for (JParameter x : m.getParams()) {
        params.put(x.getName(), x);
      }
      if (def.body().isDefined()) {
        JMethodBody body = (JMethodBody) m.getBody();
        LocalStack local = new LocalStack(enclosingClass, body, params);
        local.pushBlock();
        JBlock block = body.getBlock();
        block(def.body().get(), block, local);
      }
    }

    private JStatement methodStatement(Statement statement, LocalStack local) {
      if (statement instanceof VarDef) {
        return varDef((VarDef) statement, local);
      } else if (statement instanceof Assignment) {
        return assignment((Assignment) statement, local);
      } else if (statement instanceof Expression) {
        return expression((Expression) statement, local).makeStatement();
      } else if (statement instanceof If) {
        return ifStmt((If) statement, local);
      } else if (statement instanceof Return) {
        return returnStmt((Return) statement, local);
      } else if (statement instanceof Throw) {
        return throwStmt((Throw) statement, local);
      } else if (statement instanceof Try) {
        return tryStmt((Try) statement, local);
      } else if (statement instanceof While) {
        return whileStmt((While) statement, local);
      } else if (statement instanceof Block) {
        JBlock block = new JBlock(UNKNOWN);
        block((Block) statement, block, local);
        return block;
      } else if (statement instanceof Continue) {
        return continueStmt((Continue) statement, local);
      } else if (statement instanceof Break) {
        return breakStmt((Break) statement, local);
      } else if (statement instanceof Switch) {
        return switchStmt((Switch) statement, local);
      } else
        throw new RuntimeException("Unexpected case " + statement);
    }

    private JSwitchStatement switchStmt(Switch statement, LocalStack local) {
      JExpression expr = expression(statement.expression(), local);
      JBlock block = new JBlock(UNKNOWN);
      for (Tuple2<Literal, Block> x : statement.jgroups()) {
        JLiteral literal = literal(x._1);
        JCaseStatement caseStmt = new JCaseStatement(UNKNOWN, literal);
        JBlock caseBlock = new JBlock(UNKNOWN);
        caseBlock.addStmt(caseStmt);
        block(x._2, caseBlock, local);
        block.addStmts(caseBlock.getStatements());
      }
      if (statement.jdefault().isDefined()) {
        JCaseStatement caseStmt = new JCaseStatement(UNKNOWN, null);
        JBlock caseBlock = new JBlock(UNKNOWN);
        caseBlock.addStmt(caseStmt);
        block(statement.jdefault().get(), caseBlock, local);
        block.addStmts(caseBlock.getStatements());
      }
      return new JSwitchStatement(UNKNOWN, expr, block);
    }

    private JContinueStatement continueStmt(Continue statement, LocalStack local) {
      JLabel label = null;
      if (statement.label().isDefined()) {
        label = local.getLabel(statement.label().get());
      }
      return new JContinueStatement(UNKNOWN, label);
    }

    private JBreakStatement breakStmt(Break statement, LocalStack local) {
      JLabel label = null;
      if (statement.label().isDefined()) {
        label = local.getLabel(statement.label().get());
      }
      return new JBreakStatement(UNKNOWN, label);
    }

    private JStatement whileStmt(While statement, LocalStack local) {
      JExpression cond = expression(statement.condition(), local);
      JLabel label = null;
      if (statement.label().isDefined()) {
        label = new JLabel(UNKNOWN, statement.label().get());
        local.pushLabel(label);
      }
      JBlock block = new JBlock(UNKNOWN);
      block(statement.block(), block, local);
      if (label != null) {
        local.popLabel(label.getName());
      }
      JStatement jstatement = new JWhileStatement(UNKNOWN, cond, block);
      if (label != null) {
        jstatement = new JLabeledStatement(UNKNOWN, label, jstatement);
      }
      return jstatement;
    }

    private JTryStatement tryStmt(Try statement, LocalStack localStack) {
      JBlock block = new JBlock(UNKNOWN);
      block(statement.block(), block, localStack);
      List<JLocalRef> catchVars = new LinkedList<JLocalRef>();
      List<JBlock> catchBlocks = new LinkedList<JBlock>();
      // introduce block context for catch variables so they can be discarded properly
      localStack.pushBlock();
      for (Tuple3<Ref, String, Block> x : statement.jcatches()) {
        JLocal local = JProgram.createLocal(UNKNOWN, x._2(), mapper.getType(x._1()), false, localStack.getEnclosingBody());
        localStack.addVar(x._2(), local);
        JLocalRef ref = new JLocalRef(UNKNOWN, local);
        JBlock catchBlock = new JBlock(UNKNOWN);
        block(x._3(), catchBlock, localStack);
        catchBlocks.add(catchBlock);
        catchVars.add(ref);
      }
      localStack.popBlock();
      JBlock finallyBlock = null;
      if (statement.finalizer().isDefined()) {
        finallyBlock = new JBlock(UNKNOWN);
        block(statement.finalizer().get(), finallyBlock, localStack);
      }
      return new JTryStatement(UNKNOWN, block, catchVars, catchBlocks, finallyBlock);
    }

    private JThrowStatement throwStmt(Throw statement, LocalStack local) {
      JExpression expression = expression(statement.expression(), local);
      return new JThrowStatement(UNKNOWN, expression);
    }

    private JReturnStatement returnStmt(Return statement, LocalStack local) {
      JExpression expression = null;
      if (statement.expression().isDefined()) {
        expression = expression(statement.expression().get(), local);
      }
      return new JReturnStatement(UNKNOWN, expression);
    }

    private JNewInstance newCall(NewCall call, LocalStack local) {
      JMethodCall methodCall = constructorCall(call.constructor(), local);
      JNewInstance jnew =
          new JNewInstance(UNKNOWN, (JConstructor) methodCall.getTarget(), local.getEnclosingType());
      jnew.addArgs(methodCall.getArgs());
      return jnew;
    }

    private JMethodCall staticMethodCall(StaticMethodCall call, LocalStack local) {
      JMethod method = mapper.getMethod(call.signature(), true, false);
      List<JExpression> params = params(call.signature().jparamTypes(), call.jparams(), local);
      JMethodCall jcall = new JMethodCall(UNKNOWN, null, method);
      jcall.addArgs(params);
      return jcall;
    }

    private JMethodCall constructorCall(ConstructorCall call, LocalStack local) {
      Signature signature = call.signature();
      JMethod method = mapper.getMethod(signature, false, true);
      List<JExpression> params = params(signature.jparamTypes(), call.jparams(), local);
      JMethodCall jcall = new JMethodCall(UNKNOWN, thisRef(local.getEnclosingType()), method);
      // not sure why this is needed; inspired by JavaASTGenerationVisitor.processConstructor
      jcall.setStaticDispatchOnly();
      jcall.addArgs(params);
      return jcall;
    }

    private JDeclarationStatement varDef(VarDef def, LocalStack localStack) {
      // TODO: modifs for isFinal
      boolean isFinal = false;
      JLocal local =
          JProgram.createLocal(UNKNOWN, def.name(), mapper.getType(def.typ()), isFinal, localStack.enclosingBody);
      localStack.addVar(def.name(), local);
      JLocalRef ref = new JLocalRef(UNKNOWN, local);
      JExpression expr = null;
      if (def.value().isDefined()) {
        expr = expression(def.value().get(), localStack);
      }
      return new JDeclarationStatement(UNKNOWN, ref, expr);
    }

    private JVariableRef varRef(VarRef ref, LocalStack local) {
      assert ref.name() != null;
      return local.resolveLocal(ref.name());
    }

    private List<JExpression> params(List<Type> paramTypes, List<Expression> params,
        LocalStack local) {
      assert paramTypes.size() == params.size() : "Mismatched params";
      List<JExpression> result = new LinkedList<JExpression>();
      for (int i = 0; i < params.size(); i++) {
        JExpression expr = expression(params.get(i), local);
        result.add(expr);
      }
      return result;
    }
  }

  private static JThisRef thisRef(JClassType enclosingType) {
    return new JThisRef(UNKNOWN, enclosingType);
  }

  private static JMethod createSyntheticMethod(SourceInfo info, String name,
      JDeclaredType enclosingType, JType returnType, boolean isAbstract, boolean isStatic,
      boolean isFinal, boolean isPrivate) {
    AccessModifier access = isPrivate ? AccessModifier.PRIVATE : AccessModifier.PUBLIC;
    JMethod method =
        new JMethod(info, name, enclosingType, returnType, isAbstract, isStatic, isFinal, access);
    method.freezeParamTypes();
    method.setSynthetic();
    method.setBody(new JMethodBody(info));
    enclosingType.addMethod(method);
    return method;
  }

  private static Disposition getFieldDisposition(FieldDef jrField) {
    // COMPILE_TIME_CONSTANT?
    if (jrField.modifs().contains("final")) {
      return Disposition.FINAL;
    } else if (jrField.modifs().contains("volatile")) {
      return Disposition.VOLATILE;
    } else {
      return Disposition.NONE;
    }
  }

  private static void implementGetClass(JDeclaredType type) {
    JMethod method = type.getMethods().get(2);
    assert ("getClass".equals(method.getName()));
    ((JMethodBody) method.getBody()).getBlock().addStmt(
        new JReturnStatement(SourceOrigin.UNKNOWN, new JClassLiteral(SourceOrigin.UNKNOWN, type)));
  }

  private static void addClinitSuperCall(JDeclaredType type) {
    JMethod myClinit = type.getMethods().get(0);
    // make an external clinit (with 1 class per compilation unit, type.getSuperClass is guaranteed
    // to be external, otherwise we'd have to use mapper.getMethod)
    JMethod superClinit =
        createSyntheticMethod(UNKNOWN, "$clinit", type.getSuperClass(), JPrimitiveType.VOID, false,
            true, true, true);
    JMethodCall superClinitCall = new JMethodCall(myClinit.getSourceInfo(), null, superClinit);
    JMethodBody body = (JMethodBody) myClinit.getBody();
    body.getBlock().addStmt(0, superClinitCall.makeStatement());
  }

  private static boolean hasConstructorCall(ClassDef classDef, Block block) {
    for (Statement stmt : block.jstatements()) {
      if (stmt instanceof ConstructorCall
          && ((ConstructorCall) stmt).signature().on().equals(classDef.name())) {
        return true;
      }
    }
    return false;
  }

  private static String intern(String s) {
    return stringInterner.intern(s);
  }
  
  private static Set<String> binaryToInternal(Set<String> binaryNames) {
    Set<String> internalNames = new HashSet<String>(binaryNames.size());
    for (String binaryName : binaryNames) {
      internalNames.add(intern(BinaryName.toInternalName(binaryName)));
    }
    return internalNames;
  }

  private static AccessModifier access(scala.collection.immutable.Set<String> modifs) {
    for (AccessModifier access : AccessModifier.values()) {
      if (modifs.contains(access.name().toLowerCase())) {
        return access;
      }
    }
    return AccessModifier.DEFAULT;
  }

}
