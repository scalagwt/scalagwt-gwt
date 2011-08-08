/*
 * Copyright 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.jjs.impl;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.jdt.RebindPermutationOracle;
import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.jjs.ast.Context;
import com.google.gwt.dev.jjs.ast.HasName;
import com.google.gwt.dev.jjs.ast.JClassLiteral;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.jjs.ast.JExpression;
import com.google.gwt.dev.jjs.ast.JGwtCreate;
import com.google.gwt.dev.jjs.ast.JMethod;
import com.google.gwt.dev.jjs.ast.JMethodCall;
import com.google.gwt.dev.jjs.ast.JModVisitor;
import com.google.gwt.dev.jjs.ast.JNameOf;
import com.google.gwt.dev.jjs.ast.JNode;
import com.google.gwt.dev.jjs.ast.JNullLiteral;
import com.google.gwt.dev.jjs.ast.JProgram;
import com.google.gwt.dev.jjs.ast.JReferenceType;
import com.google.gwt.dev.jjs.ast.JStringLiteral;
import com.google.gwt.dev.util.JsniRef;
import com.google.gwt.dev.util.Name.BinaryName;
import com.google.gwt.dev.util.log.speedtracer.CompilerEventType;
import com.google.gwt.dev.util.log.speedtracer.SpeedTracerLogger;
import com.google.gwt.dev.util.log.speedtracer.SpeedTracerLogger.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces any "GWT.create()" calls with a special node.
 */
public class ReplaceRebinds {

  private class RebindVisitor extends JModVisitor {

    private JDeclaredType currentClass;
    private final JMethod nameOfMethod;
    private final JMethod rebindCreateMethod;

    public RebindVisitor(JMethod nameOfMethod, JMethod rebindCreateMethod) {
      this.nameOfMethod = nameOfMethod;
      this.rebindCreateMethod = rebindCreateMethod;
    }

    @Override
    public void endVisit(JMethodCall x, Context ctx) {
      JMethod method = x.getTarget();
      if (method == nameOfMethod) {
        replaceImplNameOf(x, ctx);

      } else if (method == rebindCreateMethod) {
        replaceGwtCreate(x, ctx);
      }
    }

    @Override
    public boolean visit(JMethod x, Context ctx) {
      currentClass = x.getEnclosingType();
      return true;
    }

    private void replaceGwtCreate(JMethodCall x, Context ctx) {
      assert (x.getArgs().size() == 1);
      JExpression arg = x.getArgs().get(0);
      assert (arg instanceof JClassLiteral);
      JClassLiteral classLiteral = (JClassLiteral) arg;
      JReferenceType sourceType = (JReferenceType) classLiteral.getRefType();
      List<JClassType> allRebindResults = getAllPossibleRebindResults(sourceType);
      JGwtCreate gwtCreate =
          new JGwtCreate(x.getSourceInfo(), sourceType, allRebindResults, program
              .getTypeJavaLangObject(), currentClass);
      if (allRebindResults.size() == 1) {
        // Just replace with the instantiation expression.
        ctx.replaceMe(gwtCreate.getInstantiationExpressions().get(0));
      } else {
        ctx.replaceMe(gwtCreate);
      }
    }

    private void replaceImplNameOf(JMethodCall x, Context ctx) {
      JExpression arg0 = x.getArgs().get(0);
      assert arg0 instanceof JStringLiteral;
      JStringLiteral stringLiteral = (JStringLiteral) arg0;
      String stringValue = stringLiteral.getValue();

      JNode node = null;

      JDeclaredType refType;
      JsniRef ref = JsniRef.parse(stringValue);

      if (ref != null) {
        final List<String> errors = new ArrayList<String>();
        node = JsniRefLookup.findJsniRefTarget(ref, program, new JsniRefLookup.ErrorReporter() {
          public void reportError(String error) {
            errors.add(error);
          }
        });
        if (!errors.isEmpty()) {
          for (String error : errors) {
            logger.log(TreeLogger.ERROR, error);
          }
        }
      } else {
        // See if it's just @foo.Bar, which would result in the class seed
        node =
            program.getFromTypeMap(stringValue.charAt(0) == '@' ? stringValue.substring(1)
                : stringValue);
      }
      if (node == null) {
        // Not found, must be null
        ctx.replaceMe(JNullLiteral.INSTANCE);
      } else {
        ctx.replaceMe(new JNameOf(x.getSourceInfo(), program.getTypeJavaLangString(),
            (HasName) node));
      }
    }
  }

  public static boolean exec(TreeLogger logger, JProgram program, RebindPermutationOracle rpo) {
    Event replaceRebindsEvent = SpeedTracerLogger.start(CompilerEventType.REPLACE_REBINDS);
    boolean didChange = new ReplaceRebinds(logger, program, rpo).execImpl();
    replaceRebindsEvent.end();
    return didChange;
  }

  private final TreeLogger logger;
  private final JProgram program;
  private final RebindPermutationOracle rpo;

  private ReplaceRebinds(TreeLogger logger, JProgram program, RebindPermutationOracle rpo) {
    this.logger = logger;
    this.program = program;
    this.rpo = rpo;
  }

  protected List<JClassType> getAllPossibleRebindResults(JReferenceType type) {
    // Rebinds are always on a source type name.
    String reqType = BinaryName.toSourceName(type.getName());
    String[] answers;
    try {
      answers = rpo.getAllPossibleRebindAnswers(logger, reqType);
    } catch (UnableToCompleteException e) {
      // Should never happen.
      throw new InternalCompilerException("Unexpected failure to get possible rebind answers for '"
          + reqType + "'");
    }
    List<JClassType> rebindAnswers = new ArrayList<JClassType>();
    for (String answer : answers) {
      JReferenceType result = program.getFromTypeMap(answer);
      assert (result != null);
      rebindAnswers.add((JClassType) result);
    }
    assert rebindAnswers.size() > 0;
    return rebindAnswers;
  }

  private boolean execImpl() {
    RebindVisitor rebinder =
        new RebindVisitor(program.getIndexedMethod("Impl.getNameOf"), program
            .getIndexedMethod("GWT.create"));
    rebinder.accept(program);
    return rebinder.didChange();
  }

}
