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

package com.google.gwt.dev.jjs.impl.jribble;

import com.google.gwt.dev.javac.MethodArgNamesLookup;
import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.jjs.SourceInfo;
import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.AccessModifier;
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
import com.google.gwt.dev.jjs.ast.JField;
import com.google.gwt.dev.jjs.ast.JField.Disposition;
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
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.ArrayLength;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.ArrayRef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Assignment;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Binary;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Block;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Break;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Case;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Cast;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Catch;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Conditional;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Continue;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Declaration;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Declaration.DeclarationType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.DeclaredType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Expr;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Expr.ExprType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.FieldDef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.FieldRef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.GlobalName;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.If;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.InstanceOf;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.LabelledStat;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Literal;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Method;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.MethodCall;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.MethodSignature;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Modifiers;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.NewArray;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.NewObject;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.ParamDef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Return;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Statement;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Statement.StatementType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Switch;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Throw;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Try;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Type;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Unary;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.VarDef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.VarRef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.While;
import com.google.gwt.dev.util.StringInterner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Class that transforms jribble AST into a per-CompilationUnit GWT mini AST.
 * 
 * TODO(stephenh) Interning https://github.com/scalagwt/scalagwt-gwt/issues/7
 */
public class JribbleAstBuilder {

  /**
   * The results from processing one DeclaredType.
   */
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

  /**
   * Given a top-level ClassDef/InterfaceDef, creates a full GWT AST.
   */
  private class AstWalker {

    private JArrayLength arrayLength(ArrayLength expr, LocalStack local) {
      JExpression on = expression(expr.getArray(), local);
      return new JArrayLength(UNKNOWN, on);
    }

    private JArrayRef arrayRef(ArrayRef expr, LocalStack local) {
      return new JArrayRef(UNKNOWN, expression(expr.getArray(), local),
          expression(expr.getIndex(), local));
    }

    private JExpression assignment(Assignment assignment, LocalStack local) {
      JExpression lhs = expression(assignment.getLhs(), local);
      JExpression rhs = expression(assignment.getRhs(), local);
      return new JBinaryOperation(UNKNOWN, lhs.getType(), JBinaryOperator.ASG,
          lhs, rhs);
    }

    private JBinaryOperation binaryOp(Binary op, LocalStack local) {
      JExpression lhs = expression(op.getLhs(), local);
      JExpression rhs = expression(op.getRhs(), local);
      JBinaryOperator jop = null;
      JType type = mapper.getType(op.getTpe());
      switch (op.getOp()) {
      case And:
        jop = JBinaryOperator.AND;
        break;
      case BitAnd:
        jop = JBinaryOperator.BIT_AND;
        break;
      case BitLeftShift:
        jop = JBinaryOperator.SHL;
        break;
      case BitOr:
        jop = JBinaryOperator.BIT_OR;
        break;
      case BitRightShift:
        jop = JBinaryOperator.SHR;
        break;
      case BitUnsignedRightShift:
        jop = JBinaryOperator.SHRU;
        break;
      case BitXor:
        jop = JBinaryOperator.BIT_XOR;
        break;
      case Concat:
        jop = JBinaryOperator.CONCAT;
        break;
      case Divide:
        jop = JBinaryOperator.DIV;
        break;
      case Equal:
        jop = JBinaryOperator.EQ;
        break;
      case Greater:
        jop = JBinaryOperator.GT;
        break;
      case GreaterOrEqual:
        jop = JBinaryOperator.GTE;
        break;
      case Lesser:
        jop = JBinaryOperator.LT;
        break;
      case LesserOrEqual:
        jop = JBinaryOperator.LTE;
        break;
      case Minus:
        jop = JBinaryOperator.SUB;
        break;
      case Modulus:
        jop = JBinaryOperator.MOD;
        break;
      case Multiply:
        jop = JBinaryOperator.MUL;
        break;
      case NotEqual:
        jop = JBinaryOperator.NEQ;
        break;
      case Or:
        jop = JBinaryOperator.OR;
        break;
      case Plus:
        jop = JBinaryOperator.ADD;
        break;
      default:
        throw new InternalCompilerException("Uknown jribble binary operation "
            + op.getOp().getValueDescriptor().getFullName());
      }
      return new JBinaryOperation(UNKNOWN, type, jop, lhs, rhs);
    }

    private void block(Block block, JBlock jblock, LocalStack local) {
      local.pushBlock();
      for (Statement x : block.getStatementList()) {
        final JStatement js;
        if (isConstructorCall(x)) {
          MethodCall m = x.getExpr().getMethodCall();
          js = constructorCall(m.getSignature(), m.getArgumentList(), local)
              .makeStatement();
        } else {
          js = methodStatement(x, local);
        }
        jblock.addStmt(js);
      }
      local.popBlock();
    }

    private JBreakStatement breakStmt(Break s, LocalStack local) {
      JLabel label = null;
      if (s.hasLabel()) {
        label = local.getLabel(s.getLabel());
      }
      return new JBreakStatement(UNKNOWN, label);
    }

    private JCastOperation cast(Cast cast, LocalStack local) {
      JExpression on = expression(cast.getExpr(), local);
      return new JCastOperation(UNKNOWN, mapper.getType(cast.getTpe()), on);
    }

    private void classDef(DeclaredType def) {
      assert !def.getIsInterface();
      JClassType clazz = (JClassType) mapper.getClassType(javaName(def
          .getName()));
      assert !clazz.isExternal();
      try {
        for (Declaration x : def.getMemberList()) {
          switch (x.getType()) {
          case Field:
            fieldDef(x, def, clazz);
            break;
          case Method:
            if (x.getMethod().getIsConstructor()) {
              constructor(x, def, clazz);
            } else {
              methodDef(x, clazz, def);
            }
          }
        }
        addClinitSuperCall(clazz);
        implementGetClass(clazz);
      } catch (InternalCompilerException e) {
        e.addNode(clazz);
        throw e;
      }
    }

    private JConditional conditional(Conditional conditional, LocalStack local) {
      JExpression condition = expression(conditional.getCondition(), local);
      JExpression then = expression(conditional.getThen(), local);
      JExpression elsee = expression(conditional.getElsee(), local);
      return new JConditional(UNKNOWN, mapper.getType(conditional.getTpe()),
          condition, then, elsee);
    }

    private void constructor(Declaration decl, DeclaredType classDef,
        JClassType enclosingClass) {
      assert decl.getType() == DeclarationType.Method;
      Method m = decl.getMethod();
      assert m.getIsConstructor();
      JMethod jc = mapper.getMethod(signature(classDef, m), false, true);
      Map<String, JParameter> params = new HashMap<String, JParameter>();
      for (JParameter x : jc.getParams()) {
        params.put(x.getName(), x);
      }
      JMethodBody body = (JMethodBody) jc.getBody();
      LocalStack local = new LocalStack(enclosingClass, body, params);
      local.pushBlock();
      JBlock jblock = body.getBlock();
      if (!isAuxiliaryConstructor(classDef, m)) {
        // primary constructors should call synthetic init method
        JMethod initMethod = jc.getEnclosingType().getMethods().get(1);
        jblock.addStmt(new JMethodCall(UNKNOWN, thisRef((JClassType) jc
            .getEnclosingType()), initMethod).makeStatement());
      }
      flatten(m.getBody(), jblock, local);
    }

    private JMethodCall constructorCall(MethodSignature signature,
        List<Expr> args, LocalStack local) {
      assert signature.getName().equals("new");
      JMethod method = mapper.getMethod(signature, false, true);
      List<JExpression> params = params(signature.getParamTypeList(), args,
          local);
      JMethodCall jcall = new JMethodCall(UNKNOWN,
          thisRef(local.getEnclosingType()), method);
      // not sure why this is needed; inspired by
      // JavaASTGenerationVisitor.processConstructor
      jcall.setStaticDispatchOnly();
      jcall.addArgs(params);
      return jcall;
    }

    private JContinueStatement continueStmt(Continue s, LocalStack local) {
      JLabel label = null;
      if (s.hasLabel()) {
        label = local.getLabel(s.getLabel());
      }
      return new JContinueStatement(UNKNOWN, label);
    }

    private JExpression expression(Expr expr, LocalStack local) {
      switch (expr.getType()) {
      case Literal:
        return literal(expr.getLiteral());
      case VarRef:
        return varRef(expr.getVarRef(), local);
      case ThisRef:
        return thisRef(local.getEnclosingType());
      case MethodCall:
        return methodCall(expr.getMethodCall(), local);
      case NewObject:
        return newCall(expr.getNewObject(), local);
      case NewArray:
        return newArray(expr.getNewArray(), local);
      case Conditional:
        return conditional(expr.getConditional(), local);
      case Cast:
        return cast(expr.getCast(), local);
      case Binary:
        return binaryOp(expr.getBinary(), local);
      case FieldRef:
        return fieldRef(expr.getFieldRef(), local);
      case ArrayRef:
        return arrayRef(expr.getArrayRef(), local);
      case ArrayLength:
        return arrayLength(expr.getArrayLength(), local);
      case InstanceOf:
        return instanceOf(expr.getInstanceOf(), local);
      case ClassLiteral:
        return new JClassLiteral(UNKNOWN, mapper.getType(expr.getClassLiteral()
            .getTpe()));
      case Unary:
        return unaryOp(expr.getUnary(), local);
      case SuperRef:
        return superRef(local);
      case Assignment:
        return assignment(expr.getAssignment(), local);
      default:
        throw new InternalCompilerException("Unknown jribble expression type "
            + expr.getType().getValueDescriptor().getFullName());
      }
    }

    private void fieldDef(Declaration decl, DeclaredType classDef,
        JClassType enclosingClass) {
      assert !classDef.getIsInterface();
      assert decl.getType() == DeclarationType.Field;
      FieldDef fieldDef = decl.getFieldDef();
      JField field = mapper.getField(javaName(classDef.getName()),
          fieldDef.getName(), decl.getModifiers().getIsStatic(),
          mapper.getType(fieldDef.getTpe()));
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
      if (fieldDef.hasInitializer()) {
        LocalStack local = new LocalStack(enclosingClass, body,
            new HashMap<String, JParameter>());
        local.pushBlock();
        JExpression expr = expression(fieldDef.getInitializer(), local);
        JStatement declStmt = new JDeclarationStatement(UNKNOWN, fieldRef, expr);
        body.getBlock().addStmt(declStmt);
      }
    }

    private JFieldRef fieldRef(FieldRef expr, LocalStack local) {
      JExpression on = null;
      if (expr.hasQualifier()) {
        on = expression(expr.getQualifier(), local);
      }
      JClassType typ = mapper.getClassType(expr.getEnclosingType());
      boolean isStatic = !expr.hasQualifier();
      JField field = mapper.getField(typ.getName(), expr.getName(), isStatic,
          mapper.getType(expr.getTpe()));
      return new JFieldRef(UNKNOWN, on, field, local.getEnclosingType());
    }

    private void flatten(Statement stmt, JBlock jblock, LocalStack local) {
      if (stmt.getType() == StatementType.Block) {
        block(stmt.getBlock(), jblock, local);
      } else {
        jblock.addStmt(methodStatement(stmt, local));
      }
    }

    private JIfStatement ifStmt(If statement, LocalStack local) {
      JExpression condition = expression(statement.getCondition(), local);

      final JBlock then = new JBlock(UNKNOWN);
      then.addStmt(methodStatement(statement.getThen(), local));
      JBlock elsee = null;
      if (statement.hasElsee()) {
        elsee = new JBlock(UNKNOWN);
        elsee.addStmt(methodStatement(statement.getElsee(), local));
      }
      return new JIfStatement(UNKNOWN, condition, then, elsee);
    }

    private JInstanceOf instanceOf(InstanceOf expr, LocalStack local) {
      JExpression on = expression(expr.getExpr(), local);
      return new JInstanceOf(UNKNOWN, (JReferenceType) mapper.getType(expr
          .getTpe()), on);
    }

    private JLabeledStatement labelledStmt(LabelledStat stmt, LocalStack local) {
      JLabel label = new JLabel(UNKNOWN, stmt.getLabel());
      local.pushLabel(label);
      JStatement jStmt = methodStatement(stmt.getStatement(), local);
      local.popLabel(label.getName());
      return new JLabeledStatement(UNKNOWN, label, jStmt);
    }

    private JLiteral literal(Literal literal) {
      switch (literal.getType()) {
      case String:
        return new JStringLiteral(UNKNOWN, literal.getStringValue(),
            javaLangString);
      case Boolean:
        return JBooleanLiteral.get(literal.getBoolValue());
      case Char:
        return JCharLiteral.get((char) literal.getCharValue());
      case Double:
        return JDoubleLiteral.get(literal.getDoubleValue());
      case Float:
        return JFloatLiteral.get(literal.getFloatValue());
      case Int:
        return JIntLiteral.get(literal.getIntValue());
      case Long:
        return JLongLiteral.get(literal.getLongValue());
      case Null:
        return JNullLiteral.INSTANCE;
      case Short:
        // in Java there are no short literals, only int literals
        return JIntLiteral.get(literal.getShortValue());
      case Byte:
        // in Java there are no byte literals, only int literals
        return JIntLiteral.get(literal.getByteValue());
      default:
        throw new InternalCompilerException("Uknown jribble literal "
            + literal.getType().getValueDescriptor().getFullName());
      }
    }

    private JMethodCall methodCall(MethodCall call, LocalStack local) {
      boolean isStatic = !call.hasReceiver();
      boolean isConstructor = call.getSignature().getName().equals("new");
      JMethod method = mapper.getMethod(call.getSignature(), isStatic,
          isConstructor);
      JExpression on = null;
      if (call.hasReceiver()) {
        on = expression(call.getReceiver(), local);
      }
      List<JExpression> params = params(call.getSignature().getParamTypeList(),
          call.getArgumentList(), local);
      JMethodCall jcall = new JMethodCall(UNKNOWN, on, method);
      jcall.addArgs(params);
      if (call.getReceiver().getType() == ExprType.SuperRef || isConstructor) {
        jcall.setStaticDispatchOnly();
      }
      return jcall;
    }

    private void methodDef(Declaration def, JClassType enclosingClass,
        DeclaredType classDef) {
      assert def.getType() == DeclarationType.Method;
      Method jrMethod = def.getMethod();
      boolean isStatic = def.getModifiers().getIsStatic();
      JMethod m = mapper.getMethod(signature(classDef, jrMethod), isStatic,
          false);
      Map<String, JParameter> params = new HashMap<String, JParameter>();
      for (JParameter x : m.getParams()) {
        params.put(x.getName(), x);
      }
      if (jrMethod.hasBody()) {
        JMethodBody body = (JMethodBody) m.getBody();
        LocalStack local = new LocalStack(enclosingClass, body, params);
        local.pushBlock();
        JBlock block = body.getBlock();
        flatten(jrMethod.getBody(), block, local);
      }
    }

    private JStatement methodStatement(Statement s, LocalStack local) {
      switch (s.getType()) {
      case Block:
        JBlock block = new JBlock(UNKNOWN);
        block(s.getBlock(), block, local);
        return block;
      case Break:
        return breakStmt(s.getBreak(), local);
      case Continue:
        return continueStmt(s.getContinueStat(), local);
      case Expr:
        return expression(s.getExpr(), local).makeStatement();
      case If:
        return ifStmt(s.getIfStat(), local);
      case LabelledStat:
        return labelledStmt(s.getLabelledStat(), local);
      case Return:
        return returnStmt(s.getReturnStat(), local);
      case Switch:
        return switchStmt(s.getSwitchStat(), local);
      case Throw:
        return throwStmt(s.getThrowStat(), local);
      case Try:
        return tryStmt(s.getTryStat(), local);
      case VarDef:
        return varDef(s.getVarDef(), local);
      case While:
        return whileStmt(s.getWhileStat(), local);
      default:
        throw new InternalCompilerException("Uknown jribble statement "
            + s.getType().getValueDescriptor().getFullName());
      }
    }

    private JNewArray newArray(NewArray expr, LocalStack local) {
      JType type = mapper.getType(expr.getElementType()); // this is the element
                                                          // type
      assert (expr.getInitExprCount() > 0 && expr.getDimensions() == 1)
          || expr.getInitExprCount() == 0;
      if (expr.getInitExprCount() > 0) {
        JArrayType arrayType = new JArrayType(type);
        List<JExpression> initializers = new LinkedList<JExpression>();
        for (Expr e : expr.getInitExprList()) {
          initializers.add(expression(e, local));
        }
        return JNewArray.createInitializers(UNKNOWN, arrayType, initializers);
      } else {
        for (int i = 0; i < expr.getDimensions(); i++) {
          type = new JArrayType(type);
        }
        List<JExpression> dims = new LinkedList<JExpression>();
        for (Expr i : expr.getDimensionExprList()) {
          dims.add(expression(i, local));
        }
        for (int i = 0; i < expr.getDimensions() - expr.getDimensionExprCount(); i++) {
          dims.add(JAbsentArrayDimension.INSTANCE);
        }
        return JNewArray.createDims(UNKNOWN, (JArrayType) type, dims);
      }
    }

    private JNewInstance newCall(NewObject call, LocalStack local) {
      JMethodCall methodCall = constructorCall(call.getSignature(),
          call.getArgumentList(), local);
      JNewInstance jnew = new JNewInstance(UNKNOWN,
          (JConstructor) methodCall.getTarget(), local.getEnclosingType());
      jnew.addArgs(methodCall.getArgs());
      return jnew;
    }

    private List<JExpression> params(List<Type> paramTypes, List<Expr> params,
        LocalStack local) {
      assert paramTypes.size() == params.size() : "Mismatched params";
      List<JExpression> result = new LinkedList<JExpression>();
      for (int i = 0; i < params.size(); i++) {
        JExpression expr = expression(params.get(i), local);
        result.add(expr);
      }
      return result;
    }

    private JReturnStatement returnStmt(Return s, LocalStack local) {
      JExpression expression = null;
      if (s.hasExpression()) {
        expression = expression(s.getExpression(), local);
      }
      return new JReturnStatement(UNKNOWN, expression);
    }

    private JExpression superRef(LocalStack local) {
      // Oddly enough, super refs can be modeled as a this refs.
      // here we follow the logic from GenerateJavaAST class.
      return thisRef(local.getEnclosingType());
    }

    private JSwitchStatement switchStmt(Switch s, LocalStack local) {
      JExpression expr = expression(s.getExpression(), local);
      JBlock block = new JBlock(UNKNOWN);
      for (Case x : s.getCaseList()) {
        JLiteral literal = literal(x.getConstant());
        JCaseStatement caseStmt = new JCaseStatement(UNKNOWN, literal);
        JBlock caseBlock = new JBlock(UNKNOWN);
        caseBlock.addStmt(caseStmt);
        caseBlock.addStmt(methodStatement(x.getStatement(), local));
        block.addStmts(caseBlock.getStatements());
      }
      if (s.hasDefaultCase()) {
        JCaseStatement caseStmt = new JCaseStatement(UNKNOWN, null);
        JBlock caseBlock = new JBlock(UNKNOWN);
        caseBlock.addStmt(caseStmt);
        caseBlock.addStmt(methodStatement(s.getDefaultCase(), local));
        block.addStmts(caseBlock.getStatements());
      }
      return new JSwitchStatement(UNKNOWN, expr, block);
    }

    private JThrowStatement throwStmt(Throw s, LocalStack local) {
      JExpression expression = expression(s.getExpression(), local);
      return new JThrowStatement(UNKNOWN, expression);
    }

    private JTryStatement tryStmt(Try s, LocalStack localStack) {
      JBlock block = new JBlock(UNKNOWN);
      flatten(s.getBlock(), block, localStack);
      List<JLocalRef> catchVars = new LinkedList<JLocalRef>();
      List<JBlock> catchBlocks = new LinkedList<JBlock>();
      // introduce block context for catch variables so they can be discarded
      // properly
      localStack.pushBlock();
      for (Catch x : s.getCatchList()) {
        JLocal local = JProgram.createLocal(UNKNOWN, x.getParam(),
            mapper.getClassType(x.getTpe()), false,
            localStack.getEnclosingBody());
        localStack.addVar(x.getParam(), local);
        JLocalRef ref = new JLocalRef(UNKNOWN, local);
        JBlock catchBlock = new JBlock(UNKNOWN);
        flatten(x.getBody(), catchBlock, localStack);
        catchBlocks.add(catchBlock);
        catchVars.add(ref);
      }
      localStack.popBlock();
      JBlock finallyBlock = null;
      if (s.hasFinalizer()) {
        finallyBlock = new JBlock(UNKNOWN);
        flatten(s.getFinalizer(), finallyBlock, localStack);
      }
      return new JTryStatement(UNKNOWN, block, catchVars, catchBlocks,
          finallyBlock);
    }

    private JExpression unaryOp(Unary jrOp, LocalStack local) {
      JUnaryOperator op;
      switch (jrOp.getOp()) {
      case Neg:
        op = JUnaryOperator.NEG;
        break;
      case Not:
        op = JUnaryOperator.NOT;
        break;
      case BitNot:
        op = JUnaryOperator.BIT_NOT;
        break;
      default:
        throw new InternalCompilerException("Unknown jribble unaryOp "
            + jrOp.getOp().getValueDescriptor().getFullName());
      }
      return new JPrefixOperation(UNKNOWN, op,
          expression(jrOp.getExpr(), local));
    }

    private JDeclarationStatement varDef(VarDef def, LocalStack localStack) {
      // TODO: modifs for isFinal
      boolean isFinal = false;
      JLocal local = JProgram.createLocal(UNKNOWN, def.getName(),
          mapper.getType(def.getTpe()), isFinal, localStack.enclosingBody);
      localStack.addVar(def.getName(), local);
      JLocalRef ref = new JLocalRef(UNKNOWN, local);
      JExpression expr = null;
      if (def.hasInitializer()) {
        expr = expression(def.getInitializer(), localStack);
      }
      return new JDeclarationStatement(UNKNOWN, ref, expr);
    }

    private JVariableRef varRef(VarRef ref, LocalStack local) {
      assert ref.getName() != null;
      return local.resolveLocal(ref.getName());
    }

    private JStatement whileStmt(While s, LocalStack local) {
      JExpression cond = expression(s.getCondition(), local);
      JStatement body = methodStatement(s.getBody(), local);
      return new JWhileStatement(UNKNOWN, cond, body);
    }
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

    public JMethodBody getEnclosingBody() {
      return enclosingBody;
    }

    public JClassType getEnclosingType() {
      return enclosingType;
    }

    public JLabel getLabel(String name) {
      if (!labels.containsKey(name)) {
        throw new InternalCompilerException(String.format("Failed to find %1s",
            name));
      }
      return labels.get(name);
    }

    public void popBlock() {
      varStack.pop();
    }

    public void popLabel(String name) {
      assert labels.containsKey(name);
      labels.remove(name);
    }

    public void pushBlock() {
      varStack.push(new HashMap<String, JLocal>());
    }

    public void pushLabel(JLabel x) {
      assert !labels.containsKey(x.getName());
      labels.put(x.getName(), x);
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
      throw new InternalCompilerException(String.format("Failed to find %1s",
          name));
    }
  }

  private static final StringInterner stringInterner = StringInterner.get();
  private static final SourceOrigin UNKNOWN = SourceOrigin.UNKNOWN;

  private static AccessModifier access(Modifiers m) {
    if (m.getIsPublic())
      return AccessModifier.PUBLIC;
    else if (m.getIsProtected())
      return AccessModifier.PROTECTED;
    else if (m.getIsPrivate())
      return AccessModifier.PRIVATE;
    else
      return AccessModifier.DEFAULT;
  }

  private static void addClinitSuperCall(JDeclaredType type) {
    JMethod myClinit = type.getMethods().get(0);
    // make an external clinit (with 1 class per compilation unit,
    // type.getSuperClass is guaranteed
    // to be external, otherwise we'd have to use mapper.getMethod)
    JMethod superClinit = createSyntheticMethod(UNKNOWN, "$clinit",
        type.getSuperClass(), JPrimitiveType.VOID, false, true, true, true);
    JMethodCall superClinitCall = new JMethodCall(myClinit.getSourceInfo(),
        null, superClinit);
    JMethodBody body = (JMethodBody) myClinit.getBody();
    body.getBlock().addStmt(0, superClinitCall.makeStatement());
  }

  private static JMethod createSyntheticMethod(SourceInfo info, String name,
      JDeclaredType enclosingType, JType returnType, boolean isAbstract,
      boolean isStatic, boolean isFinal, boolean isPrivate) {
    AccessModifier access = isPrivate ? AccessModifier.PRIVATE
        : AccessModifier.PUBLIC;
    JMethod method = new JMethod(info, name, enclosingType, returnType,
        isAbstract, isStatic, isFinal, access);
    method.freezeParamTypes();
    method.setSynthetic();
    method.setBody(new JMethodBody(info));
    enclosingType.addMethod(method);
    return method;
  }

  private static Disposition getFieldDisposition(Declaration decl) {
    assert decl.getType() == DeclarationType.Field;
    Modifiers m = decl.getModifiers();
    // COMPILE_TIME_CONSTANT?
    if (m.getIsFinal()) {
      return Disposition.FINAL;
    } else if (m.getIsVolatile()) {
      return Disposition.VOLATILE;
    } else {
      return Disposition.NONE;
    }
  }

  /**
   * Checks if <code>s</code> has a call to constructor for class represented by
   * classDef.
   */
  private static boolean hasConstructorCall(DeclaredType classDef, Statement s) {
    // TODO(grek): Do we need to traverse whole AST for constructor call?
    if (s.getType() == StatementType.Block) {
      Block block = s.getBlock();
      for (Statement x : block.getStatementList()) {
        if (hasConstructorCall(classDef, x)) {
          return true;
        }
      }
      return false;
    } else if (s.getType() == StatementType.Expr) {
      return isConstructorCall(s)
          && (s.getExpr().getMethodCall().getSignature().getOwner()
              .equals(classDef.getName()));
    }
    return false;
  }

  private static void implementGetClass(JDeclaredType type) {
    JMethod method = type.getMethods().get(2);
    assert ("getClass".equals(method.getName()));
    ((JMethodBody) method.getBody()).getBlock().addStmt(
        new JReturnStatement(SourceOrigin.UNKNOWN, new JClassLiteral(
            SourceOrigin.UNKNOWN, type)));
  }

  private static String intern(String s) {
    return stringInterner.intern(s);
  }

  /**
   * Checks if method <code>m</code> is auxiliary constructor of
   * <code>classDef</code>.
   */
  private static boolean isAuxiliaryConstructor(DeclaredType classDef, Method m) {
    assert m.getIsConstructor();
    return hasConstructorCall(classDef, m.getBody());
  }

  private static boolean isConstructorCall(Statement x) {
    if (x.getType() == StatementType.Expr
        && x.getExpr().getType() == ExprType.MethodCall) {
      return x.getExpr().getMethodCall().getSignature().getName() == "new";
    } else {
      return false;
    }
  }

  private static String javaName(GlobalName name) {
    if (name.hasPkg()) {
      return intern(name.getPkg() + "." + name.getName());
    } else {
      return intern(name.getName());
    }
  }

  private static MethodSignature signature(DeclaredType enclosing, Method m) {
    MethodSignature.Builder b = MethodSignature.newBuilder();
    b.setName(m.getName());
    b.setOwner(enclosing.getName());
    for (ParamDef pd : m.getParamDefList()) {
      b.addParamType(pd.getTpe());
    }
    b.setReturnType(m.getReturnType());
    return b.build();
  }

  private static JThisRef thisRef(JClassType enclosingType) {
    return new JThisRef(UNKNOWN, enclosingType);
  }

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
      return new Result(newTypes, mapper.getTouchedTypes(), methodArgNames);
    } finally {
      // Clean up.
      mapper.clearSource();
      methodArgNames = null;
      newTypes = null;
    }
  }

  private void buildTheCode(DeclaredType jrType) {
    if (!jrType.getIsInterface()) {
      new AstWalker().classDef(jrType);
    }
    // interfaces already have their methods created from createMembers
  }

  private void createDeclaration(JDeclaredType enclosingType,
      DeclaredType jrDeclType, Declaration decl) {
    Modifiers mod = decl.getModifiers();
    boolean isAbstract = mod.getIsAbstract() || jrDeclType.getIsInterface();
    boolean isStatic = mod.getIsStatic();
    boolean isFinal = mod.getIsFinal();
    switch (decl.getType()) {
    /* Creates a field, without the allocation yet. */
    case Field:
      FieldDef jrField = decl.getFieldDef();
      JType type = mapper.getType(jrField.getTpe());
      JField field = new JField(UNKNOWN, intern(jrField.getName()),
          enclosingType, type, isStatic, getFieldDisposition(decl));
      enclosingType.addField(field);
      mapper.setSourceField(jrDeclType, jrField, field);
      break;
    /* Creates a source method/constructor, without the code inside yet. */
    case Method:
      Method m = decl.getMethod();
      JMethod method;
      if (m.getIsConstructor()) {
        method = new JConstructor(UNKNOWN, (JClassType) enclosingType);
        method.setBody(new JMethodBody(UNKNOWN));
        // constructor logic
      } else {
        method = new JMethod(UNKNOWN, m.getName(), enclosingType,
            mapper.getType(m.getReturnType()), isAbstract, isStatic, isFinal,
            access(mod));
        if (!jrDeclType.getIsInterface()) {
          method.setBody(new JMethodBody(UNKNOWN));
        }
      }
      for (ParamDef param : m.getParamDefList()) {
        // param modifs?
        method.addParam(new JParameter(UNKNOWN, param.getName(), mapper
            .getType(param.getTpe()), false, false, method));
      }
      method.freezeParamTypes();
      enclosingType.addMethod(method);
      methodArgNames.store(enclosingType.getName(), method);
      mapper.setSourceMethod(signature(jrDeclType, m), method);
    }
  }

  private void createMembers(DeclaredType jrType) {
    JDeclaredType gwtType;
    if (!jrType.getIsInterface()) {
      gwtType = mapper.getClassType(javaName(jrType.getName()));
    } else {
      gwtType = mapper.getInterfaceType(javaName(jrType.getName()));
    }

    assert gwtType.getMethods().size() == 0;
    createSyntheticMethod(UNKNOWN, "$clinit", gwtType, JPrimitiveType.VOID,
        false, true, true, true);

    if (gwtType instanceof JClassType) {
      assert gwtType.getMethods().size() == 1;
      createSyntheticMethod(UNKNOWN, "$init", gwtType, JPrimitiveType.VOID,
          false, false, true, true);

      // TODO Check JSORestrictionsChecker if this is a JSO
      // https://github.com/scalagwt/scalagwt-gwt/issues/8
      assert gwtType.getMethods().size() == 2;
      createSyntheticMethod(UNKNOWN, "getClass", gwtType, javaLangClass, false,
          false, false, false);

      for (Declaration m : jrType.getMemberList()) {
        createDeclaration(gwtType, jrType, m);
      }
    } else if (gwtType instanceof JInterfaceType) {
      for (Declaration m : jrType.getMemberList()) {
        assert m.getType() == DeclarationType.Method;
        createDeclaration(gwtType, jrType, m);
      }
    } else {
      throw new RuntimeException("Unhandled type " + gwtType);
    }
    // if we had multiple types in the CompilationUnits, create members for each
    // of them here
  }

  /**
   * Creates a non-external type AST and puts it in the mapper.
   */
  private void createType(DeclaredType jrType) {
    JDeclaredType gwtType;
    if (!jrType.getIsInterface()) {
      boolean isAbstract = jrType.getModifiers().getIsAbstract();
      boolean isFinal = jrType.getModifiers().getIsFinal();
      gwtType = new JClassType(UNKNOWN, javaName(jrType.getName()), isAbstract,
          isFinal);
    } else {
      gwtType = new JInterfaceType(UNKNOWN, javaName(jrType.getName()));
    }
    // would add inner classes here if we had them all in a single
    // CompilationUnit
    mapper.setSourceType(jrType, gwtType);
    newTypes.add(gwtType);
  }

  /**
   * Creates potentially-external ASTs for our AST's super class and interfaces.
   */
  private void resolveTypeRefs(DeclaredType jrType) {
    if (!jrType.getIsInterface()) {
      JClassType gwtClassType = mapper.getClassType(javaName(jrType.getName()));
      if (jrType.hasExt()) {
        gwtClassType
            .setSuperClass(mapper.getClassType(javaName(jrType.getExt())));
      } else {
        // package objects sometimes? don't have super classes but should in GWT
        gwtClassType.setSuperClass(mapper.getClassType("java.lang.Object"));
      }
      for (GlobalName name : jrType.getImplementsList()) {
        gwtClassType.addImplements(mapper.getInterfaceType(javaName(name)));
      }
    } else {
      JInterfaceType gwtIntType = mapper.getInterfaceType(javaName(jrType
          .getName()));
      for (GlobalName name : jrType.getImplementsList()) {
        gwtIntType.addImplements(mapper.getInterfaceType(javaName(name)));
      }
    }
  }
}
