/*
 * Copyright 2009 Google Inc.
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
package com.google.gwt.dev.javac.asm;

import com.google.gwt.dev.asm.AnnotationVisitor;
import com.google.gwt.dev.asm.Label;
import com.google.gwt.dev.asm.Opcodes;
import com.google.gwt.dev.asm.Type;
import com.google.gwt.dev.asm.commons.EmptyVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects data from a single method.
 */
public class CollectMethodData extends EmptyVisitor {

  private static final String[] EMPTY_STRING_ARRAY = new String[0];

  private final List<CollectAnnotationData> annotations = new ArrayList<CollectAnnotationData>();
  private final String name;
  private final String desc;
  private final String signature;
  private final String[] exceptions;
  private Type[] argTypes;
  private final String[] argNames;
  private final List<CollectAnnotationData>[] paramAnnots;
  private boolean actualArgNames = false;
  private final int access;

  /**
   * Prepare to collect data for a method from bytecode.
   * 
   * @param classType
   * @param access
   * @param name
   * @param desc
   * @param signature
   * @param exceptions
   */
  @SuppressWarnings("unchecked")
  // for new List[]
  public CollectMethodData(CollectClassData.ClassType classType, int access,
      String name, String desc, String signature, String[] exceptions) {
    this.access = access;
    this.name = name;
    this.desc = desc;
    this.signature = signature;
    this.exceptions = exceptions;
    argTypes = Type.getArgumentTypes(desc);

    argNames = new String[argTypes.length];
    paramAnnots = new List[argTypes.length];
    for (int i = 0; i < argNames.length; ++i) {
      argNames[i] = "arg" + i;
      paramAnnots[i] = new ArrayList<CollectAnnotationData>();
    }
    if (argNames.length == 0) {
      // save some work later if there aren't any parameters
      actualArgNames = true;
    }
  }

  /**
   * @return the access
   */
  public int getAccess() {
    return access;
  }

  /**
   * @return the annotations
   */
  public List<CollectAnnotationData> getAnnotations() {
    return annotations;
  }

  public List<CollectAnnotationData>[] getArgAnnotations() {
    return paramAnnots;
  }

  /**
   * @return the argNames
   */
  public String[] getArgNames() {
    return argNames;
  }

  /**
   * @return the argTypes
   */
  public Type[] getArgTypes() {
    return argTypes;
  }

  /**
   * @return the desc
   */
  public String getDesc() {
    return desc;
  }

  /**
   * @return the exceptions
   */
  public String[] getExceptions() {
    return exceptions == null ? EMPTY_STRING_ARRAY : exceptions;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @return the signature
   */
  public String getSignature() {
    return signature;
  }

  /**
   * @return the actualArgNames
   */
  public boolean hasActualArgNames() {
    return actualArgNames;
  }

  @Override
  public String toString() {
    return "method " + name;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    CollectAnnotationData av = new CollectAnnotationData(desc, visible);
    annotations.add(av);
    return av;
  }

  @Override
  public void visitLocalVariable(String name, String desc, String signature,
      Label start, Label end, int index) {
    if ((access & Opcodes.ACC_STATIC) == 0) {
      // adjust for "this"
      // TODO(jat): do we need to account for this$0 in inner classes?
      --index;
    }
    // TODO(jat): is it safe to assume parameter slots don't get reused?
    // Do we need to check if the name has already been assigned?
    if (index >= 0 && index < argNames.length) {
      actualArgNames = true;
      argNames[index] = name;
    }
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(int parameter, String desc,
      boolean visible) {
    CollectAnnotationData av = new CollectAnnotationData(desc, visible);
    paramAnnots[parameter].add(av);
    return av;
  }
}
