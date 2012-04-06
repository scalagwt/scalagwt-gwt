/*
 * Copyright 2012 Google Inc.
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

import static com.google.gwt.dev.jjs.impl.jribble.AstUtils.*;
import static com.google.gwt.thirdparty.guava.common.collect.Sets.newHashSet;

import com.google.gwt.dev.jjs.SourceOrigin;
import com.google.gwt.dev.jjs.ast.JClassType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.DeclaredType;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.Modifiers;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.PrimitiveType;

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Tests for {@link JribbleReferenceMapper}.
 */
public class JribbleReferenceMapperTest extends TestCase {

  public void testSourceTypeIsNotConsideredTouched() {
    JribbleReferenceMapper m = new JribbleReferenceMapper();
    JClassType gwtType = new JClassType(SourceOrigin.UNKNOWN, "foo.T5", false, false);
    DeclaredType decl = DeclaredType.newBuilder()
      .setName(toGlobalName("foo.T5")).setModifiers(Modifiers.getDefaultInstance()).build();
    m.setSourceType(decl, gwtType);
    Assert.assertEquals(newHashSet(), m.getTouchedTypes());
  }

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

}
