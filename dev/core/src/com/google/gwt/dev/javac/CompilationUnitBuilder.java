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

import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.jjs.ast.JDeclaredType;
import com.google.gwt.dev.resource.Resource;
import com.google.gwt.dev.util.Util;

import org.eclipse.jdt.core.compiler.CategorizedProblem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

/**
 * Builds a {@link CompilationUnit}.
 */
public abstract class CompilationUnitBuilder {

  static final class GeneratedCompilationUnit extends CompilationUnitImpl {
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
      return new CachedCompilationUnit(this, astToken);
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

    String getSource() {
      return generatedUnit.getSource();
    }
  }

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
    public long getLastModified() {
      return generatedUnit.creationTime();
    }
    
    @Override
    public String getLocation() {
      return getLocationFor(generatedUnit);
    }

    @Override
    public String getTypeName() {
      return generatedUnit.getTypeName();
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

  static class JribbleCompilationUnitBuilder extends CompilationUnitBuilder {
    private final ClassLoader classLoader;
    private final Resource resource;

    public JribbleCompilationUnitBuilder(ClassLoader classLoader, Resource jribbleResource) {
      this.classLoader = classLoader;
      this.resource = jribbleResource;
    }

    public JribbleCompilationUnit build() {
      String internalName = getTypeName().replace('.', '/');
      /*
       * Based on the comments, it's not clear that tracking whether a class is
       * local is important. For now, always say false.
       */
      boolean isLocal = false;

      byte[] classBytes = Util.readURLAsBytes(classLoader.getResource(internalName + ".class"));

      CompiledClass compiledClass =
          new CompiledClass(classBytes, isLocal, internalName, resource.getLastModified());

      JribbleCompilationUnit unit = new JribbleCompilationUnit(resource, compiledClass);
      return unit;
    }

    @Override
    public ContentId getContentId() {
      shouldNotBeCalled();
      return null;
    }

    @Override
    public long getLastModified() {
      return resource.getLastModified();
    }

    @Override
    public String getLocation() {
      return resource.getLocation();
    }

    @Override
    public String getTypeName() {
      return Shared.toTypeName(resource.getPath());
    }

    @Override
    public boolean isJribble() {
      return true;
    }

    @Override
    protected String doGetSource() {
      shouldNotBeCalled();
      return null;
    }

    @Override
    protected CompilationUnit makeUnit(List<CompiledClass> compiledClasses,
        List<JDeclaredType> types, Dependencies dependencies,
        Collection<? extends JsniMethod> jsniMethods, MethodArgNamesLookup methodArgs,
        CategorizedProblem[] errors) {
      shouldNotBeCalled();
      return null;
    }

    /**
     * It would be good to refactor the CompilationUnitBuilder hierarchy so that
     * this method is never used, but that would substantially increase the size
     * of the initial Jribble patch. Leaving it alone for now.
     */
    private void shouldNotBeCalled() {
      throw new RuntimeException("Should not be called");
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

  public static CompilationUnitBuilder create(GeneratedUnit generatedUnit) {
    return new GeneratedCompilationUnitBuilder(generatedUnit);
  }

  public static CompilationUnitBuilder create(Resource resource) {
    if (resource.getPath().endsWith(".jribble") || resource.getPath().endsWith(".jribbletxt")) {
      return new JribbleCompilationUnitBuilder(Thread.currentThread().getContextClassLoader(),
          resource);
    } else {
      return new ResourceCompilationUnitBuilder(resource);
    }
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

  public abstract long getLastModified();
  
  public abstract String getLocation();

  public String getSource() {
    if (source == null) {
      source = doGetSource();
    }
    return source;
  }

  public abstract String getTypeName();
  
  /**
   * Whether this unit started as Java source code or as a Jribble protobuf
   * message.
   */
  public boolean isJribble() {
    return false;
  }
  
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
