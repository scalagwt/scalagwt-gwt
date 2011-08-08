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

import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.JArrayType;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.ast.JConstructor;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.jjs.ast.JField;
import com.google.gwt.dev.jjs.ast.JField.Disposition;
import com.google.gwt.dev.jjs.ast.AccessModifier;
import com.google.gwt.dev.jjs.ast.JInterfaceType;
import com.google.gwt.dev.jjs.ast.JMethod;
import com.google.gwt.dev.jjs.ast.JNullType;
import com.google.gwt.dev.jjs.ast.JParameter;
import com.google.gwt.dev.jjs.ast.JPrimitiveType;
import com.google.gwt.dev.jjs.ast.JReferenceType;
import com.google.gwt.dev.jjs.ast.JType;
import com.google.gwt.dev.util.StringInterner;
import com.google.jribble.ast.Array;
import com.google.jribble.ast.ClassDef;
import com.google.jribble.ast.DeclaredType;
import com.google.jribble.ast.FieldDef;
import com.google.jribble.ast.Primitive;
import com.google.jribble.ast.Ref;
import com.google.jribble.ast.Signature;
import com.google.jribble.ast.Type;
import com.google.jribble.ast.Void$;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Creates unresolved references to types, fields, and methods.
 * 
 * An instance of this class can be reused to create several mini ASTs, as we can reuse the external
 * JFields, JMethods, and JReferenceTypes across compilation units. The non-external ASTs are
 * flushed after each CompilationUnit is built.
 * 
 * Closely modeled after the canonical Java {@ReferenceMapper}.
 * 
 * todo: interning.
 */
public class JribbleReferenceMapper {

  private static final StringInterner stringInterner = StringInterner.get();
  // source instances, flushed per CompilationUnit
  private final Map<String, JField> sourceFields = new HashMap<String, JField>();
  private final Map<String, JMethod> sourceMethods = new HashMap<String, JMethod>();
  private final Map<String, JReferenceType> sourceTypes = new HashMap<String, JReferenceType>();
  // keep a list of touched types for Dependencies purposes, flushed per CompilationUnit
  private final Set<String> touchedTypes = new HashSet<String>();
  // external instances, kept across CompilationUnits
  private final Map<String, JField> fields = new HashMap<String, JField>();
  private final Map<String, JMethod> methods = new HashMap<String, JMethod>();
  private final Map<String, JType> types = new HashMap<String, JType>();

  {
    put(JPrimitiveType.BOOLEAN, JPrimitiveType.BYTE, JPrimitiveType.CHAR, JPrimitiveType.DOUBLE,
        JPrimitiveType.FLOAT, JPrimitiveType.INT, JPrimitiveType.LONG, JPrimitiveType.SHORT,
        JPrimitiveType.VOID, JNullType.INSTANCE);
  }

  public void clearSource() {
    sourceFields.clear();
    sourceMethods.clear();
    sourceTypes.clear();
    touchedTypes.clear();
  }

  /** @return an existing source or exiting|new external JType */
  public JType getType(Type type) {
    return getType(javaName(type));
  }

  /** @return an existing source or exiting|new external JType */
  public JType getType(String name) {
    JType existing = getTypeIfExists(name);
    if (existing != null) {
      return existing;
    }
    if (name.startsWith("[")) {
      JArrayType newArrayType = new JArrayType(getType(name.substring(1)));
      types.put(name, newArrayType);
      // don't put into touched types as array types don't need to be in the dependencies
      return newArrayType;
    }
    // If no existing type, assume a new external JClassType is okay.
    // Even if this was supposed to be a JInterfaceType, since it
    // is external, UnifyAst will fix things up for us later.
    return getClassType(name);
  }

  /** @return an existing source or exiting|new external JClassType */
  public JClassType getClassType(String name) {
    JClassType existing = (JClassType) getTypeIfExists(name);
    if (existing != null) {
      return existing;
    }
    name = intern(name);
    JClassType newExternal = new JClassType(name);
    assert newExternal.isExternal();
    types.put(name, newExternal);
    touchedTypes.add(name);
    return newExternal;
  }

  /** @return an existing source or exiting|new external JInterfaceType */
  public JInterfaceType getInterfaceType(String name) {
    JDeclaredType existing = (JDeclaredType) getTypeIfExists(name);
    // While building a different mini AST, this type name may have been assumed to
    // be a JClassType when now we see it's not. If so, ignore the old one and start
    // using a new JInterfaceType external. UnifyAst will clean the old reference up
    // later.
    if (existing != null && existing instanceof JInterfaceType) {
      return (JInterfaceType) existing;
    }
    name = intern(name);
    JInterfaceType newExternal = new JInterfaceType(name);
    assert newExternal.isExternal();
    types.put(name, newExternal);
    touchedTypes.add(name);
    return newExternal;
  }

  public Set<String> getTouchedTypes() {
    return touchedTypes;
  }

  // searches sourceTypes then external types else null
  private JType getTypeIfExists(String name) {
    JType sourceType = sourceTypes.get(name);
    if (sourceType != null) {
      assert !sourceType.isExternal();
      return sourceType;
    }
    JType externalType = types.get(name);
    if (externalType != null) {
      assert isEffectivelyExternal(externalType);
      if (!(externalType instanceof JPrimitiveType || externalType == JNullType.INSTANCE || externalType instanceof JArrayType)) {
        touchedTypes.add(intern(name));
      }
      return externalType;
    }
    return null;
  }

  private static boolean isEffectivelyExternal(JType type) {
    return type instanceof JPrimitiveType
        || type == JNullType.INSTANCE
        || type.isExternal()
        || (type instanceof JArrayType && isEffectivelyExternal(((JArrayType) type) .getElementType()));
  }

  private static String key(Signature signature, boolean isCstr) {
    StringBuilder sb = new StringBuilder();
    sb.append(signature.on().javaName());
    sb.append('.');
    // jribble Constructor's signature() method uses the type name
    // as its method name. However, other places uses "this" as
    // cstr method names. So we always key off of "this" as the
    // method name if a cstr.
    if (isCstr) {
      sb.append("this");
    } else {
      sb.append(signature.name());
    }
    sb.append('(');
    for (Type paramType : signature.jparamTypes()) {
      sb.append(javaName(paramType));
    }
    sb.append(')');
    sb.append(javaName(signature.returnType()));
    return sb.toString();
  }

  private static String javaName(Type type) {
    if (type instanceof Ref) {
      return ((Ref) type).javaName();
    } else if (type instanceof Array) {
      return "[" + javaName(((Array) type).typ());
    } else if (type instanceof Primitive) {
      return ((Primitive) type).name();
    } else if (type == Void$.MODULE$) {
      return "void";
    } else {
      throw new RuntimeException("Unhandled type " + type);
    }
  }

  public JField getField(String typeName, String fieldName, boolean isStatic) {
    String key = typeName + "." + fieldName + ":";
    JField sourceField = sourceFields.get(key);
    if (sourceField != null) {
      assert !sourceField.isExternal();
      return sourceField;
    }
    JField externalField = fields.get(key);
    if (externalField != null) {
      assert externalField.isExternal();
      return externalField;
    }
    JField newExternal =
        new JField(SourceOrigin.UNKNOWN, fieldName, (JDeclaredType) getType(typeName),
            JPrimitiveType.VOID, isStatic, Disposition.NONE);
    assert newExternal.isExternal();
    fields.put(key, newExternal);
    return newExternal;
  }

  public JMethod getMethod(Signature signature, boolean isStatic, boolean isCstr) {
    String key = key(signature, isCstr);
    JMethod sourceMethod = sourceMethods.get(key);
    if (sourceMethod != null) {
      assert !sourceMethod.isExternal();
      return sourceMethod;
    }
    JMethod externalMethod = methods.get(key);
    if (externalMethod != null) {
      assert externalMethod.isExternal();
      return externalMethod;
    }
    JMethod newExternal;
    if (isCstr) {
      newExternal =
          new JConstructor(SourceOrigin.UNKNOWN, (JClassType) getType(signature.on().javaName()));
    } else {
      // assume public is okay for the AccessModifier, will be fixed by UnifyAst
      newExternal =
          new JMethod(SourceOrigin.UNKNOWN, signature.name(), (JDeclaredType) getType(signature
              .on().javaName()), getType(signature.returnType()), false, isStatic, false, AccessModifier.PUBLIC);
    }
    int i = 0;
    for (Type type : signature.jparamTypes()) {
      newExternal.addParam(new JParameter(SourceOrigin.UNKNOWN, "x" + (i++), getType(type), false, false, newExternal));
    }
    newExternal.freezeParamTypes();
    assert newExternal.isExternal();
    methods.put(key, newExternal);
    return newExternal;
  }

  public void setSourceMethod(Signature signature, JMethod method) {
    assert !method.isExternal();
    sourceMethods.put(key(signature, method instanceof JConstructor), method);
  }

  public void setSourceField(ClassDef jrClassDef, FieldDef jrFieldDef, JField field) {
    assert !field.isExternal();
    sourceFields.put(signature(jrClassDef, jrFieldDef), field);
  }

  public void setSourceType(DeclaredType type, JDeclaredType jtype) {
    assert !jtype.isExternal();
    sourceTypes.put(type.name().javaName(), jtype);
  }

  private static String intern(String s) {
    return stringInterner.intern(s);
  }

  private void put(JType... baseTypes) {
    for (JType type : baseTypes) {
      // jribble uses "I" for int, "void" for void, so store both
      types.put(type.getJavahSignatureName(), type);
      types.put(type.getName(), type);
    }
  }

  private static String signature(ClassDef jrClassDef, FieldDef jrField) {
    StringBuilder sb = new StringBuilder();
    sb.append(jrClassDef.name().javaName());
    sb.append('.');
    sb.append(jrField.name());
    sb.append(':');
    // GwtAstBuilder had field type here--why?
    // sb.append(javaName(jrField.typ()));
    return sb.toString();
  }

}
