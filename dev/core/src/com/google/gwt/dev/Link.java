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
package com.google.gwt.dev;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.SelectionProperty;
import com.google.gwt.core.ext.linker.impl.StandardCompilationResult;
import com.google.gwt.core.ext.linker.impl.StandardLinkerContext;
import com.google.gwt.dev.CompileTaskRunner.CompileTask;
import com.google.gwt.dev.cfg.BindingProperty;
import com.google.gwt.dev.cfg.ModuleDef;
import com.google.gwt.dev.cfg.ModuleDefLoader;
import com.google.gwt.dev.cfg.StaticPropertyOracle;
import com.google.gwt.dev.util.Util;
import com.google.gwt.dev.util.arg.ArgHandlerExtraDir;
import com.google.gwt.dev.util.arg.ArgHandlerOutDir;
import com.google.gwt.dev.util.arg.OptionExtraDir;
import com.google.gwt.dev.util.arg.OptionOutDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Performs the last phase of compilation, merging the compilation outputs.
 */
public class Link {
  /**
   * Options for Link.
   */
  public interface LinkOptions extends CompileTaskOptions, OptionExtraDir,
      OptionOutDir {
  }

  static class ArgProcessor extends CompileArgProcessor {
    public ArgProcessor(LinkOptions options) {
      super(options);
      registerHandler(new ArgHandlerExtraDir(options));
      registerHandler(new ArgHandlerOutDir(options));
    }

    @Override
    protected String getName() {
      return Link.class.getName();
    }
  }

  /**
   * Concrete class to implement link options.
   */
  static class LinkOptionsImpl extends CompileTaskOptionsImpl implements
      LinkOptions {

    private File extraDir;
    private File outDir;

    public LinkOptionsImpl() {
    }

    public LinkOptionsImpl(LinkOptions other) {
      copyFrom(other);
    }

    public void copyFrom(LinkOptions other) {
      super.copyFrom(other);
      setExtraDir(other.getExtraDir());
      setOutDir(other.getOutDir());
    }

    public File getExtraDir() {
      return extraDir;
    }

    public File getOutDir() {
      return outDir;
    }

    public void setExtraDir(File extraDir) {
      this.extraDir = extraDir;
    }

    public void setOutDir(File outDir) {
      this.outDir = outDir;
    }
  }

  public static ArtifactSet link(TreeLogger logger, ModuleDef module,
      Precompilation precompilation, File[] resultFiles)
      throws UnableToCompleteException {
    StandardLinkerContext linkerContext = new StandardLinkerContext(logger,
        module, precompilation.getUnifiedAst().getOptions());
    return doLink(logger, linkerContext, precompilation, resultFiles);
  }

  public static void link(TreeLogger logger, ModuleDef module,
      Precompilation precompilation, File[] resultFiles, File outDir,
      File extrasDir) throws UnableToCompleteException {
    StandardLinkerContext linkerContext = new StandardLinkerContext(logger,
        module, precompilation.getUnifiedAst().getOptions());
    ArtifactSet artifacts = doLink(logger, linkerContext, precompilation,
        resultFiles);
    doProduceOutput(logger, artifacts, linkerContext, module, outDir, extrasDir);
  }

  public static void main(String[] args) {
    /*
     * NOTE: main always exits with a call to System.exit to terminate any
     * non-daemon threads that were started in Generators. Typically, this is to
     * shutdown AWT related threads, since the contract for their termination is
     * still implementation-dependent.
     */
    final LinkOptions options = new LinkOptionsImpl();
    if (new ArgProcessor(options).processArgs(args)) {
      CompileTask task = new CompileTask() {
        public boolean run(TreeLogger logger) throws UnableToCompleteException {
          return new Link(options).run(logger);
        }
      };
      if (CompileTaskRunner.runWithAppropriateLogger(options, task)) {
        // Exit w/ success code.
        System.exit(0);
      }
    }
    // Exit w/ non-success code.
    System.exit(1);
  }

  private static ArtifactSet doLink(TreeLogger logger,
      StandardLinkerContext linkerContext, Precompilation precompilation,
      File[] resultFiles) throws UnableToCompleteException {
    Permutation[] perms = precompilation.getPermutations();
    if (perms.length != resultFiles.length) {
      throw new IllegalArgumentException(
          "Mismatched resultFiles.length and permutation count");
    }

    for (int i = 0; i < perms.length; ++i) {
      finishPermuation(logger, perms[i], resultFiles[i], linkerContext);
    }

    linkerContext.addOrReplaceArtifacts(precompilation.getGeneratedArtifacts());
    return linkerContext.invokeLink(logger);
  }

  private static void doProduceOutput(TreeLogger logger, ArtifactSet artifacts,
      StandardLinkerContext linkerContext, ModuleDef module, File outDir,
      File extraDir) throws UnableToCompleteException {
    boolean warnOnExtra = false;
    File moduleExtraDir;
    if (extraDir == null) {
      /*
       * Legacy behavior for backwards compatibility; if the extra directory is
       * not specified, make it a sibling to the deploy directory, with -aux.
       */
      String deployDir = module.getDeployTo();
      deployDir = deployDir.substring(0, deployDir.length() - 1) + "-aux";
      moduleExtraDir = new File(outDir, deployDir);

      /*
       * Only warn when we create a new legacy extra dir.
       */
      warnOnExtra = !moduleExtraDir.exists();
    } else {
      moduleExtraDir = new File(extraDir, module.getDeployTo());
    }

    File moduleOutDir = new File(outDir, module.getDeployTo());
    Util.recursiveDelete(moduleOutDir, true);
    Util.recursiveDelete(moduleExtraDir, true);
    linkerContext.produceOutputDirectory(logger, artifacts, moduleOutDir,
        moduleExtraDir);

    /*
     * Warn on legacy extra directory, but only if: 1) It didn't exist before.
     * 2) We just created it.
     */
    if (warnOnExtra && moduleExtraDir.exists()) {
      logger.log(
          TreeLogger.WARN,
          "Non-public artificats were produced in '"
              + moduleExtraDir.getAbsolutePath()
              + "' within the public output folder; use -extra to specify an alternate location");
    }
    logger.log(TreeLogger.INFO, "Link succeeded");
  }

  private static void finishPermuation(TreeLogger logger, Permutation perm,
      File jsFile, StandardLinkerContext linkerContext)
      throws UnableToCompleteException {
    StandardCompilationResult compilation = linkerContext.getCompilation(
        logger, jsFile);
    StaticPropertyOracle[] propOracles = perm.getPropertyOracles();
    for (StaticPropertyOracle propOracle : propOracles) {
      BindingProperty[] orderedProps = propOracle.getOrderedProps();
      String[] orderedPropValues = propOracle.getOrderedPropValues();
      Map<SelectionProperty, String> unboundProperties = new HashMap<SelectionProperty, String>();
      for (int i = 0; i < orderedProps.length; i++) {
        SelectionProperty key = linkerContext.getProperty(orderedProps[i].getName());
        if (key.tryGetValue() != null) {
          /*
           * The view of the Permutation doesn't include properties with defined
           * values.
           */
          continue;
        }
        unboundProperties.put(key, orderedPropValues[i]);
      }
      compilation.addSelectionPermutation(unboundProperties);
    }
  }

  private ModuleDef module;

  private final LinkOptionsImpl options;

  public Link(LinkOptions options) {
    this.options = new LinkOptionsImpl(options);
  }

  public boolean run(TreeLogger logger) throws UnableToCompleteException {
    module = ModuleDefLoader.loadFromClassPath(logger, options.getModuleName());

    File precompilationFile = new File(options.getCompilerWorkDir(),
        Precompile.PRECOMPILATION_FILENAME);
    if (!precompilationFile.exists()) {
      logger.log(TreeLogger.ERROR, "File not found '"
          + precompilationFile.getAbsolutePath()
          + "'; please run Precompile first");
      return false;
    }

    Precompilation precompilation;
    try {
      precompilation = Util.readFileAsObject(precompilationFile,
          Precompilation.class);
    } catch (ClassNotFoundException e) {
      logger.log(TreeLogger.ERROR, "Unable to deserialize '"
          + precompilationFile.getAbsolutePath() + "'", e);
      return false;
    }
    Permutation[] perms = precompilation.getPermutations();
    File[] resultFiles = new File[perms.length];
    for (int i = 0; i < perms.length; ++i) {
      resultFiles[i] = CompilePerms.makePermFilename(
          options.getCompilerWorkDir(), i);
      if (!resultFiles[i].exists()) {
        logger.log(TreeLogger.ERROR, "File not found '"
            + precompilationFile.getAbsolutePath()
            + "'; please compile all permutations");
        return false;
      }
    }

    TreeLogger branch = logger.branch(TreeLogger.INFO, "Linking module "
        + module.getName());
    StandardLinkerContext linkerContext = new StandardLinkerContext(branch,
        module, precompilation.getUnifiedAst().getOptions());
    ArtifactSet artifacts = doLink(branch, linkerContext, precompilation,
        resultFiles);

    doProduceOutput(branch, artifacts, linkerContext, module,
        options.getOutDir(), options.getExtraDir());
    return true;
  }
}
