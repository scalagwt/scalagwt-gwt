/*
 * Copyright 2012 Google Inc.
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

import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleAstBuilder;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.DeclaredType;
import com.google.gwt.dev.protobuf.CodedInputStream;
import com.google.gwt.dev.resource.Resource;
import com.google.gwt.dev.util.Util;

import org.eclipse.jdt.core.compiler.CategorizedProblem;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A compilation unit that is loaded from a .jribble file.
 */
public class JribbleCompilationUnit extends CompilationUnit {
  private final CompiledClass compiledClass;
  private final ContentId contentId;
  private JDeclaredType declaredType;
  private Dependencies dependencies;
  private MethodArgNamesLookup methodArgNamesLookup;
  private final Resource resource;

  public JribbleCompilationUnit(Resource resource, CompiledClass compiledClass) {
    this.resource = resource;
    this.compiledClass = compiledClass;

    try {
      this.contentId =
          new ContentId(getTypeName(), Util.computeStrongName(Util.readStreamAsBytes(resource
              .openContents())));
    } catch (IOException ex) {
      throw new RuntimeException("Unexpected error while hashing a Jribble file", ex);
    }
    compiledClass.initUnit(this);
  }

  @Override
  public CachedCompilationUnit asCachedCompilationUnit() {
    return null;
  }

  @Override
  public Collection<CompiledClass> getCompiledClasses() {
    return Collections.singletonList(compiledClass);
  }

  @Override
  public List<JsniMethod> getJsniMethods() {
    return Collections.emptyList();
  }

  @Override
  public long getLastModified() {
    return resource.getLastModified();
  }

  @Override
  public MethodArgNamesLookup getMethodArgs() {
    if (methodArgNamesLookup == null) {
      loadFromJribble();
    }
    return methodArgNamesLookup;
  }

  @Override
  public String getResourceLocation() {
    return resource.getLocation();
  }

  @Override
  public String getResourcePath() {
    return resource.getPath();
  }

  @Override
  public String getTypeName() {
    return Shared.toTypeName(resource.getPath());
  }

  @Override
  public List<JDeclaredType> getTypes() {
    if (declaredType == null) {
      loadFromJribble();
    }
    JDeclaredType result = declaredType;

    /*
     * Only use it once per call, because the compiler will mutate it.
     */
    declaredType = null;

    return Collections.singletonList(result);
  }

  @Override
  public boolean isError() {
    // Compilation errors should be caught by scalac, so there won't be any here
    return false;
  }

  @Override
  public boolean isGenerated() {
    return false;
  }

  @Override
  public boolean isSuperSource() {
    return false;
  }

  @Override
  public boolean shouldBePersisted() {
    /*
     * It should be faster to load Jribble from a .jribble file than via Java
     * serialization.
     */
    return false;
  }

  @Override
  ContentId getContentId() {
    return contentId;
  }

  @Override
  Dependencies getDependencies() {
    if (dependencies == null) {
      loadFromJribble();
    }
    return dependencies;
  }

  @Override
  CategorizedProblem[] getProblems() {
    return new CategorizedProblem[0];
  }

  /**
   * Load information from the Jribble file.
   */
  private void loadFromJribble() {
    try {
      String typeName = getTypeName();
      String packageName = "";
      if (typeName.contains(".")) {
        packageName = typeName.substring(0, typeName.lastIndexOf('.'));
      }

      JribbleProtos.DeclaredType proto = readProto();
      JribbleAstBuilder.Result result = new JribbleAstBuilder().process(proto);
      assert result.types.size() == 1;
      dependencies =
          Dependencies.buildFromApiRefs(packageName, new ArrayList<String>(result.apiRefs));
      methodArgNamesLookup = result.methodArgNames;
      declaredType = result.types.get(0);
    } catch (IOException ex) {
      throw new RuntimeException("Unexpected error while deserializing a Jribble file", ex);
    }
  }

  private JribbleProtos.DeclaredType readProto() throws IOException {
    InputStream source = resource.openContents();
    JribbleProtos.DeclaredType proto;
    if (resource.getPath().endsWith(".jribble")) {
      // Binary format
      CodedInputStream codedSource = CodedInputStream.newInstance(source);
      codedSource.setRecursionLimit(400);
      proto = JribbleProtos.DeclaredType.parseFrom(codedSource);
    } else {
      // Text format
      Reader reader = new InputStreamReader(source, Util.DEFAULT_ENCODING);
      DeclaredType.Builder b = DeclaredType.newBuilder();
      com.google.gwt.dev.protobuf.TextFormat.merge(reader, b);
      proto = b.build();
    }
    source.close();
    return proto;
  }
}
