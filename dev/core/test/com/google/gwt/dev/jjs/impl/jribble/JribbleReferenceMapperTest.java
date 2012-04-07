package com.google.gwt.dev.jjs.impl.jribble;

import static com.google.gwt.dev.jjs.impl.jribble.AstUtils.*;
import static com.google.gwt.thirdparty.guava.common.collect.Sets.newHashSet;

import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleReferenceMapper;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.*;

import junit.framework.Assert;
import junit.framework.TestCase;

public class JribbleReferenceMapperTest extends TestCase {

  public void testTouchedTypes() {
    JribbleReferenceMapper m = new JribbleReferenceMapper();
    m.getType(toGlobalNameType("foo.T1"));
    m.getType(voidType());
    m.getType(primitive(PrimitiveType.Int));
    m.getType(arrayType(toGlobalNameType(("foo.T4"))));
    m.getType(arrayType(primitive(PrimitiveType.Double)));
    m.getClassType("foo.T2");
    m.getInterfaceType("foo.T3");
    Assert.assertEquals(newHashSet("foo.T1", "foo.T2", "foo.T3", "foo.T4"), m.getTouchedTypes());

    m.clearSource();
    Assert.assertEquals(newHashSet(), m.getTouchedTypes());

    m.getType(toGlobalNameType("foo.T1"));
    m.getType(arrayType(primitive(PrimitiveType.Double)));
    Assert.assertEquals(newHashSet("foo.T1"), m.getTouchedTypes());
  }

  public void testSourceTypeIsNotConsideredTouched() {
    JribbleReferenceMapper m = new JribbleReferenceMapper();
    JClassType gwtType = new JClassType(SourceOrigin.UNKNOWN, "foo.T5", false, false);
    DeclaredType decl = DeclaredType.newBuilder().
      setName(toGlobalName("foo.T5")).setModifiers(Modifiers.getDefaultInstance()).build();
    m.setSourceType(decl, gwtType);
    Assert.assertEquals(newHashSet(), m.getTouchedTypes());
  }

}
