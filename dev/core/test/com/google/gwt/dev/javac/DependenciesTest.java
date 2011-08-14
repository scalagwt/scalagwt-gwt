package com.google.gwt.dev.javac;

import static com.google.gwt.thirdparty.guava.common.collect.Lists.newArrayList;
import static com.google.gwt.thirdparty.guava.common.collect.Sets.newHashSet;

import junit.framework.Assert;
import junit.framework.TestCase;

import static java.util.Collections.emptySet;

public class DependenciesTest extends TestCase {

  public void testFromApiRefs() {
    Dependencies d = Dependencies.buildFromApiRefs("foo.bar", newArrayList("foo.bar.Zaz"));
    Assert.assertEquals(emptySet(), d.simple.keySet());
    Assert.assertEquals(newHashSet("foo", "foo.bar", "foo.bar.Zaz"), d.qualified.keySet());
  }

  public void testFromApiRefsWithJavaLangRef() {
    Dependencies d = Dependencies.buildFromApiRefs("foo.bar", newArrayList("foo.bar.Zaz", "java.lang.String"));
    Assert.assertEquals(newHashSet("String"), d.simple.keySet());
    Assert.assertEquals(newHashSet("foo", "foo.bar", "foo.bar.Zaz", "java", "java.lang", "java.lang.String"), d.qualified.keySet());
  }

}
