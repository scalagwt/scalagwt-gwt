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

import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.AccessModifier;
import com.google.gwt.dev.jjs.ast.JArrayType;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.ast.JConstructor;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.jjs.ast.JField;
import com.google.gwt.dev.jjs.ast.JField.Disposition;
import com.google.gwt.dev.jjs.ast.JInterfaceType;
import com.google.gwt.dev.jjs.ast.JMethod;
import com.google.gwt.dev.jjs.ast.JNullType;
import com.google.gwt.dev.jjs.ast.JParameter;
import com.google.gwt.dev.jjs.ast.JPrimitiveType;
import com.google.gwt.dev.jjs.ast.JReferenceType;
import com.google.gwt.dev.jjs.ast.JType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.DeclaredType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.FieldDef;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.GlobalName;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.MethodSignature;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.PrimitiveType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Type;
import com.google.gwt.dev.util.StringInterner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Creates unresolved references to types, fields, and methods.
 * 
 * An instance of this class can be reused to create several mini ASTs, as we
 * can reuse the external JFields, JMethods, and JReferenceTypes across
 * compilation units. The non-external ASTs are flushed after each
 * CompilationUnit is built.
 * 
 * Closely modeled after the canonical Java {@ReferenceMapper}
 * .
 * 
 * todo: interning.
 */
public class JribbleReferenceMapper {

  private static final StringInterner stringInterner = StringInterner.get();

  private static String intern(String s) {
    return stringInterner.intern(s);
  }

  private static boolean isEffectivelyExternal(JType type) {
    return type instanceof JPrimitiveType
        || type == JNullType.INSTANCE
        || type.isExternal()
        || (type instanceof JArrayType && isEffectivelyExternal(((JArrayType) type)
            .getElementType()));
  }

  private static String javaName(GlobalName name) {
    if (name.hasPkg()) {
      return intern(name.getPkg() + "." + name.getName());
    } else {
      return intern(name.getName());
    }
  }

  private static String javaName(PrimitiveType type) {
    switch (type) {
    case Boolean:
      return "Z";
    case Byte:
      return "B";
    case Char:
      return "C";
    case Double:
      return "D";
    case Float:
      return "F";
    case Int:
      return "I";
    case Long:
      return "J";
    case Short:
      return "S";
    default:
      throw new InternalCompilerException("Unknown jribble primitive type "
          + type.getValueDescriptor().getFullName());
    }
  }

  private static String javaName(Type type) {
    switch (type.getType()) {
    case Named:
      return javaName(type.getNamedType());
    case Array:
      return "[" + javaName(type.getArrayElementType());
    case Primitive:
      return javaName(type.getPrimitiveType());
    case Void:
      return "void";
    default:
      throw new InternalCompilerException("Unknown jribble TypeType "
          + type.getType().getValueDescriptor().getFullName());
    }
  }

  private static String key(MethodSignature s, boolean isCstr) {
    StringBuilder sb = new StringBuilder();
    sb.append(javaName(s.getOwner()));
    sb.append('.');
    // jribble Constructor's signature() method uses the type name
    // as its method name. However, other places uses "this" as
    // cstr method names. So we always key off of "this" as the
    // method name if a cstr.
    if (isCstr) {
      sb.append("this");
    } else {
      sb.append(s.getName());
    }
    sb.append('(');
    for (Type paramType : s.getParamTypeList()) {
      sb.append(javaName(paramType));
    }
    sb.append(')');
    sb.append(javaName(s.getReturnType()));
    return sb.toString();
  }

  private static String signature(DeclaredType jrDeclaredType, FieldDef jrField) {
    StringBuilder sb = new StringBuilder();
    sb.append(javaName(jrDeclaredType.getName()));
    sb.append('.');
    sb.append(jrField.getName());
    sb.append(':');
    // GwtAstBuilder had field type here--why?
    // sb.append(javaName(jrField.typ()));
    return sb.toString();
  }

  // source instances, flushed per CompilationUnit
  private final Map<String, JField> sourceFields = new HashMap<String, JField>();

  private final Map<String, JMethod> sourceMethods = new HashMap<String, JMethod>();

  private final Map<String, JReferenceType> sourceTypes = new HashMap<String, JReferenceType>();

  // keep a list of touched types for Dependencies purposes, flushed per
  // CompilationUnit
  private final Set<String> touchedTypes = new HashSet<String>();

  // external instances, kept across CompilationUnits
  private final Map<String, JField> fields = new HashMap<String, JField>();

  private final Map<String, JMethod> methods = new HashMap<String, JMethod>();

  private final Map<String, JType> types = new HashMap<String, JType>();

  {
    put(JPrimitiveType.BOOLEAN, JPrimitiveType.BYTE, JPrimitiveType.CHAR,
        JPrimitiveType.DOUBLE, JPrimitiveType.FLOAT, JPrimitiveType.INT,
        JPrimitiveType.LONG, JPrimitiveType.SHORT, JPrimitiveType.VOID,
        JNullType.INSTANCE);
  }

  public void clearSource() {
    sourceFields.clear();
    sourceMethods.clear();
    sourceTypes.clear();
    touchedTypes.clear();
  }

  /**
   * Creates an external {@link JClassType}, returning a previously returned one
   * for the same class if possible.
   */
  public JClassType getClassType(GlobalName name) {
    return getClassType(javaName(name));
  }

  /**
   * Creates an external {@link JClassType}, returning a previously returned one
   * for the same class if possible.
   */
  public JClassType getClassType(String name) {
    JClassType existing = (JClassType) getTypeIfExists(name);
    if (existing != null) {
      return existing;
    }
    name = intern(name);
    JClassType newExternal = JClassType.newExternal(name);
    assert newExternal.isExternal();
    types.put(name, newExternal);
    touchedTypes.add(name);
    return newExternal;
  }

  public JField getField(String typeName, String fieldName, boolean isStatic,
      JType type) {
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
    JField newExternal = new JField(SourceOrigin.UNKNOWN, fieldName,
        (JDeclaredType) getType(typeName), type, isStatic, Disposition.NONE);
    assert newExternal.isExternal();
    fields.put(key, newExternal);
    return newExternal;
  }

  /**
   * Returns an external {@link JInterfaceType} with the given name, returning a
   * pre-existing one when possible.
   */
  public JInterfaceType getInterfaceType(String name) {
    JDeclaredType existing = (JDeclaredType) getTypeIfExists(name);
    /*
     * While building a different mini AST, this type name may have been assumed
     * to be a JClassType when now we see it's not. If so, ignore the old one
     * and start using a new JInterfaceType external. UnifyAst will clean the
     * old reference up later.
     */
    if (existing != null && existing instanceof JInterfaceType) {
      return (JInterfaceType) existing;
    }
    name = intern(name);
    JInterfaceType newExternal = JInterfaceType.newExternal(name);
    assert newExternal.isExternal();
    types.put(name, newExternal);
    touchedTypes.add(name);
    return newExternal;
  }

  public JMethod getMethod(MethodSignature s, boolean isStatic, boolean isCstr) {
    String key = key(s, isCstr);
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
      newExternal = new JConstructor(SourceOrigin.UNKNOWN,
          (JClassType) getType(javaName(s.getOwner())));
    } else {
      // assume public is okay for the AccessModifier, will be fixed by UnifyAst
      newExternal = new JMethod(SourceOrigin.UNKNOWN, s.getName(),
          (JDeclaredType) getType(javaName(s.getOwner())),
          getType(s.getReturnType()), false, isStatic, false,
          AccessModifier.PUBLIC);
    }
    int i = 0;
    for (Type type : s.getParamTypeList()) {
      newExternal.addParam(new JParameter(SourceOrigin.UNKNOWN, "x" + (i++),
          getType(type), false, false, newExternal));
    }
    newExternal.freezeParamTypes();
    assert newExternal.isExternal();
    methods.put(key, newExternal);
    return newExternal;
  }

  public Set<String> getTouchedTypes() {
    return touchedTypes;
  }

  /**
   * Returns an external type for the given Jribble name.
   */
  public JType getType(String name) {
    JType existing = getTypeIfExists(name);
    if (existing != null) {
      return existing;
    }
    if (name.startsWith("[")) {
      JArrayType newArrayType = new JArrayType(getType(name.substring(1)));
      types.put(name, newArrayType);
      /*
       * Don't put into touched types as array types don't need to be in the
       * dependencies
       */
      return newArrayType;
    }
    /*
     * If no existing type, assume a new external JClassType is okay. Even if
     * this was supposed to be a JInterfaceType, since it is external, UnifyAst
     * will fix things up for us later.
     */
    return getClassType(name);
  }

  /**
   * Return a GWT type for the given Jribble type.
   */
  public JType getType(Type type) {
    return getType(javaName(type));
  }

  public void setSourceField(DeclaredType jrClassDef, FieldDef jrFieldDef,
      JField field) {
    assert !jrClassDef.getIsInterface();
    assert !field.isExternal();
    sourceFields.put(signature(jrClassDef, jrFieldDef), field);
  }

  public void setSourceMethod(MethodSignature signature, JMethod method) {
    assert !method.isExternal();
    sourceMethods.put(key(signature, method instanceof JConstructor), method);
  }

  public void setSourceType(DeclaredType type, JDeclaredType jtype) {
    assert !jtype.isExternal();
    sourceTypes.put(javaName(type.getName()), jtype);
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
      if (!(externalType instanceof JPrimitiveType
          || externalType == JNullType.INSTANCE || externalType instanceof JArrayType)) {
        touchedTypes.add(intern(name));
      }
      return externalType;
    }
    return null;
  }

  private void put(JType... baseTypes) {
    for (JType type : baseTypes) {
      // jribble uses "I" for int, "void" for void, so store both
      types.put(type.getJavahSignatureName(), type);
      types.put(type.getName(), type);
    }
  }

}
