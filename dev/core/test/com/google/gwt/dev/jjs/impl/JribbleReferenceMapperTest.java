package com.google.gwt.dev.jjs.impl;

import static com.google.gwt.dev.jjs.impl.AstUtils.toRef;
import static com.google.gwt.thirdparty.guava.common.collect.Sets.newHashSet;

import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.jribble.ast.Array;
import com.google.jribble.ast.ClassDef;
import com.google.jribble.ast.Primitive;

import junit.framework.Assert;
import junit.framework.TestCase;

public class JribbleReferenceMapperTest extends TestCase {

  public void testTouchedTypes() {
    JribbleReferenceMapper m = new JribbleReferenceMapper();
    m.getType(toRef("foo.T1"));
    m.getType(new Primitive("void"));
    m.getType(new Primitive("I"));
    m.getType(new Array(toRef("foo.T4")));
    m.getType(new Array(new Primitive("D")));
    m.getClassType("foo.T2");
    m.getInterfaceType("foo.T3");
    Assert.assertEquals(newHashSet("foo.T1", "foo.T2", "foo.T3", "foo.T4"), m.getTouchedTypes());

    m.clearSource();
    Assert.assertEquals(newHashSet(), m.getTouchedTypes());

    m.getType(toRef("foo.T1"));
    m.getType(new Array(new Primitive("D")));
    Assert.assertEquals(newHashSet("foo.T1"), m.getTouchedTypes());
  }

  public void testSourceTypeIsNotConsideredTouched() {
    JribbleReferenceMapper m = new JribbleReferenceMapper();
    JClassType gwtType = new JClassType(SourceOrigin.UNKNOWN, "foo.T5", false, false);
    m.setSourceType(new ClassDef(null, AstUtils.toRef("foo.T5"), null, null, null), gwtType);
    Assert.assertEquals(newHashSet(), m.getTouchedTypes());
  }

}
