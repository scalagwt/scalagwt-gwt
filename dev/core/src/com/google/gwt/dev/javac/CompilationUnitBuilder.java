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
package com.google.gwt.dev.javac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.core.compiler.CategorizedProblem;

import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.resource.Resource;
import com.google.gwt.dev.util.Util;

/**
 * Builds a {@link CompilationUnit}.
 */
public abstract class CompilationUnitBuilder {

  static class GeneratedCompilationUnitBuilder extends CompilationUnitBuilder {
    private final GeneratedUnit generatedUnit;

    private GeneratedCompilationUnitBuilder(GeneratedUnit generatedUnit) {
      this.generatedUnit = generatedUnit;
    }

    @Override
    public ContentId getContentId() {
      return new ContentId(getTypeName(), generatedUnit.getStrongHash());
    }

    @Override
    public String getLocation() {
      return getLocationFor(generatedUnit);
    }
    
    @Override
    public long getLastModified() {
      return generatedUnit.creationTime();
    }

    @Override
    public String getTypeName() {
      return generatedUnit.getTypeName();
    }

    @Override
    public boolean isJribble() {
      return false;
    }

    @Override
    protected String doGetSource() {
      return generatedUnit.getSource();
    }

    @Override
    protected CompilationUnit makeUnit(List<CompiledClass> compiledClasses,
        List<JDeclaredType> types, Dependencies dependencies,
        Collection<? extends JsniMethod> jsniMethods, MethodArgNamesLookup methodArgs,
        CategorizedProblem[] problems) {
      return new GeneratedCompilationUnit(generatedUnit, compiledClasses, types, dependencies,
          jsniMethods, methodArgs, problems);
    }

    @Override
    boolean isGenerated() {
      return true;
    }    
  }

  static class ResourceCompilationUnitBuilder extends CompilationUnitBuilder {
    /**
     * Not valid until source has been read.
     */
    private ContentId contentId;

    private long lastModifed = -1;

    private final Resource resource;

    private final String typeName;

    private ResourceCompilationUnitBuilder(Resource resource) {
      this.typeName = Shared.toTypeName(resource.getPath());
      this.resource = resource;
    }

    @Override
    public ContentId getContentId() {
      if (contentId == null) {
        getSource();
      }
      return contentId;
    }

    @Override
    public long getLastModified() {
      if (lastModifed < 0) {
        return resource.getLastModified();
      } else {
        // Value when the source was actually read.
        return lastModifed;
      }
    }

    @Override
    public String getLocation() {
      return resource.getLocation();
    }

    public Resource getResource() {
      return resource;
    }

    @Override
    public String getTypeName() {
      return typeName;
    }
    
    @Override
    public boolean isJribble() {
      return resource.getPath().endsWith(".jribble");
    }
    
    public InputStream readSourceBinary() throws IOException {
      return resource.openContents();
    }

    @Override
    protected String doGetSource() {
      /*
       * Pin the mod date first to be conservative, we'd rather a unit be seen
       * as too stale than too fresh.
       */
      lastModifed = resource.getLastModified();
      ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
      try {
        InputStream in = resource.openContents();
        Util.copy(in, out);
      } catch (IOException e) {
        throw new RuntimeException("Unexpected error reading resource '" + resource + "'", e);
      }
      byte[] content = out.toByteArray();
      contentId = new ContentId(getTypeName(), Util.computeStrongName(content));
      return Util.toString(content);
    }

    @Override
    protected CompilationUnit makeUnit(List<CompiledClass> compiledClasses,
        List<JDeclaredType> types, Dependencies dependencies,
        Collection<? extends JsniMethod> jsniMethods, MethodArgNamesLookup methodArgs,
        CategorizedProblem[] problems) {
      return new SourceFileCompilationUnit(getResource(), getContentId(), compiledClasses, types,
          dependencies, jsniMethods, methodArgs, problems, getLastModified());
    }
  }

  private static final class GeneratedCompilationUnit extends CompilationUnitImpl {
    private final GeneratedUnit generatedUnit;

    public GeneratedCompilationUnit(GeneratedUnit generatedUnit,
        List<CompiledClass> compiledClasses, List<JDeclaredType> types, Dependencies dependencies,
        Collection<? extends JsniMethod> jsniMethods, MethodArgNamesLookup methodArgs,
        CategorizedProblem[] problems) {
      super(compiledClasses, types, dependencies, jsniMethods, methodArgs, problems);
      this.generatedUnit = generatedUnit;
    }

    @Override
    public CachedCompilationUnit asCachedCompilationUnit() {
      long sourceToken = generatedUnit.getSourceToken();
      assert sourceToken >= 0;
      return new CachedCompilationUnit(this, sourceToken, astToken);
    }

    @Override
    public long getLastModified() {
      return generatedUnit.creationTime();
    }

    @Override
    public String getResourceLocation() {
      return getLocationFor(generatedUnit);
    }

    @Override
    public String getResourcePath() {
      return Shared.toPath(generatedUnit.getTypeName());
    }

    @Deprecated
    @Override
    public String getSource() {
      return generatedUnit.getSource();
    }

    @Override
    public String getTypeName() {
      return generatedUnit.getTypeName();
    }

    @Deprecated
    @Override
    public boolean isGenerated() {
      return true;
    }

    @Deprecated
    @Override
    public boolean isSuperSource() {
      return false;
    }

    @Override
    ContentId getContentId() {
      return new ContentId(getTypeName(), generatedUnit.getStrongHash());
    }
  }

  public static CompilationUnitBuilder create(GeneratedUnit generatedUnit) {
    return new GeneratedCompilationUnitBuilder(generatedUnit);
  }

  public static CompilationUnitBuilder create(Resource resource) {
    return new ResourceCompilationUnitBuilder(resource);
  }

  public static String makeContentId(String typeName, String strongHash) {
    return typeName + ':' + strongHash;
  }

  static String getLocationFor(GeneratedUnit generatedUnit) {
    String location = generatedUnit.optionalFileLocation();
    if (location != null) {
      return location;
    }
    return "generated://" + generatedUnit.getStrongHash() + "/"
        + Shared.toPath(generatedUnit.getTypeName());
  }

  private List<CompiledClass> compiledClasses;
  private Dependencies dependencies;
  private Collection<? extends JsniMethod> jsniMethods;
  private MethodArgNamesLookup methodArgs;
  private CategorizedProblem[] problems;

  /**
   * Caches source until JSNI methods can be collected.
   */
  private transient String source;

  private List<JDeclaredType> types;

  protected CompilationUnitBuilder() {
  }

  public CompilationUnit build() {
    // Free the source now.
    source = null;
    assert compiledClasses != null;
    assert types != null;
    assert dependencies != null;
    assert jsniMethods != null;
    assert methodArgs != null;
    return makeUnit(compiledClasses, types, dependencies, jsniMethods, methodArgs, problems);
  }

  public abstract ContentId getContentId();

  public abstract String getLocation();
  
  public abstract long getLastModified();

  public String getSource() {
    if (source == null) {
      source = doGetSource();
    }
    return source;
  }

  public abstract String getTypeName();

  public abstract boolean isJribble();
  
  /**
   * Read in the source of this unit as binary. Only available if
   * {@link #isJribble} is true.
   */
  public InputStream readSourceBinary() throws IOException {
    throw new InternalCompilerException("readBinary() is not supported on this class");
  }

  public CompilationUnitBuilder setClasses(List<CompiledClass> compiledClasses) {
    this.compiledClasses = compiledClasses;
    return this;
  }

  public CompilationUnitBuilder setDependencies(Dependencies dependencies) {
    this.dependencies = dependencies;
    return this;
  }

  public CompilationUnitBuilder setJsniMethods(Collection<? extends JsniMethod> jsniMethods) {
    this.jsniMethods = jsniMethods;
    return this;
  }

  public CompilationUnitBuilder setMethodArgs(MethodArgNamesLookup methodArgs) {
    this.methodArgs = methodArgs;
    return this;
  }

  public CompilationUnitBuilder setProblems(CategorizedProblem[] problems) {
    this.problems = problems;
    return this;
  }

  public CompilationUnitBuilder setSource(String source) {
    this.source = source;
    return this;
  }

  public CompilationUnitBuilder setTypes(List<JDeclaredType> types) {
    this.types = types;
    return this;
  }

  @Override
  public final String toString() {
    return getLocation();
  }

  protected abstract String doGetSource();

  protected abstract CompilationUnit makeUnit(List<CompiledClass> compiledClasses,
      List<JDeclaredType> types, Dependencies dependencies,
      Collection<? extends JsniMethod> jsniMethods, MethodArgNamesLookup methodArgs,
      CategorizedProblem[] errors);

  /**
   * This only matters for {@link ArtificialRescueChecker}.
   */
  boolean isGenerated() {
    return false;
  }
}
