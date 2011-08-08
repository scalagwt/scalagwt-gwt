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
package com.google.gwt.dev.javac;

import com.google.gwt.dev.javac.TypeOracleMediator.TypeData;
import com.google.gwt.dev.javac.asm.CollectClassData;
import com.google.gwt.dev.util.DiskCache;
import com.google.gwt.dev.util.DiskCacheToken;
import com.google.gwt.dev.util.Name.InternalName;
import com.google.gwt.dev.util.StringInterner;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;

import java.io.Serializable;
import java.util.Map;

/**
 * Encapsulates the state of a single compiled class file.
 */
public final class CompiledClass implements Serializable {

  private static final DiskCache diskCache = DiskCache.INSTANCE;

  private final CompiledClass enclosingClass;
  private final String internalName;
  private final boolean isLocal;
  private transient TypeData typeData;
  private CompilationUnit unit;
  private String signatureHash;

  /**
   * A token to retrieve this object's bytes from the disk cache. byte code is
   * placed in the cache when the object is deserialized.
   */
  private final DiskCacheToken classBytesToken;
  private transient NameEnvironmentAnswer nameEnvironmentAnswer;

  /**
   * Create a compiled class from raw class bytes.
   * 
   * @param classBytes - byte code for this class
   * @param enclosingClass - outer class
   * @param isLocal Is this class a local class? (See the JLS rev 2 section
   *          14.3)
   * @param internalName the internal binary name for this class. e.g.
   *          {@code java/util/Map$Entry}. See
   *          {@link "http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#14757"}
   */
  CompiledClass(byte[] classBytes, CompiledClass enclosingClass, boolean isLocal,
      String internalName) {
    this.enclosingClass = enclosingClass;
    this.internalName = StringInterner.get().intern(internalName);
    this.classBytesToken = new DiskCacheToken(diskCache.writeByteArray(classBytes));
    this.isLocal = isLocal;
  }

  /**
   * Returns the bytes of the compiled class.
   */
  public byte[] getBytes() {
    return classBytesToken.readByteArray();
  }

  public CompiledClass getEnclosingClass() {
    return enclosingClass;
  }

  /**
   * Returns the class internal binary name for this type, e.g.
   * {@code java/util/Map$Entry}.
   */
  public String getInternalName() {
    return internalName;
  }

  /**
   * Returns the enclosing package, e.g. {@code java.util}.
   */
  public String getPackageName() {
    return Shared.getPackageNameFromBinary(internalName);
  }

  /**
   * Returns a hash code on the byte code of the class.
   */
  public String getSignatureHash() {
    if (signatureHash == null) {
      signatureHash = BytecodeSignatureMaker.getCompileDependencySignature(getBytes());
    }
    return signatureHash;
  }

  /**
   * Accurately derives the source name from our internal name, using
   * {@code classFileMap} to recursively resolve outer class names.
   *
   * @return the fully qualified source name
   */
  public String getSourceName(Map<String, CompiledClass> classFileMap) {
    CollectClassData cd = getTypeData().getCollectClassData();
    if (cd.getInnerClass() == null) {
      // This manual internal -> source replace is safe because any "$"
      // characters have been split up by ASM building the CollectClassDatas
      return cd.getName().replace('/', '.');
    } else {
      CompiledClass outer = classFileMap.get(cd.getOuterClass());
      // TODO(stephenh) Uncomment assertion when RedBlack is fixed
      // assert outer != null : "outer class not found for " + cd.getInnerClass();
      if (outer == null) {
        // TODO(stephenh) Remove this when RedBlack is fixed
        // https://github.com/scalagwt/scalagwt-gwt/issues/4
        return InternalName.toSourceName(cd.getOuterClass());
      }
      // recurse for nested inner types, e.g. foo.Bar.Zaz.Zip
      return outer.getSourceName(classFileMap) + "." + cd.getInnerClass();
    }
  }

  public TypeData getTypeData() {
    if (typeData == null) {
      assert unit != null : "initUnit has not been called yet";
      typeData =
          new TypeData(getPackageName(), getInternalName(), null, getBytes(),
              getUnit().getLastModified());
    }
    return typeData;
  }

  public CompilationUnit getUnit() {
    return unit;
  }

  /**
   * Returns <code>true</code> if this is a local type, or if this type is
   * nested inside of any local type.
   */
  public boolean isLocal() {
    return isLocal;
  }

  @Override
  public String toString() {
    return internalName;
  }

  NameEnvironmentAnswer getNameEnvironmentAnswer() throws ClassFormatException {
    if (nameEnvironmentAnswer == null) {
      ClassFileReader cfr =
          new ClassFileReader(getBytes(), unit.getResourceLocation().toCharArray(), true);
      nameEnvironmentAnswer = new NameEnvironmentAnswer(cfr, null);
    }
    return nameEnvironmentAnswer;
  }

  void initUnit(CompilationUnit unit) {
    this.unit = unit;
  }

}
