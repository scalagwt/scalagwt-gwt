/*
 * Copyright 2010 Google Inc.
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
package com.google.gwt.requestfactory.server;

import com.google.gwt.requestfactory.shared.Id;
import com.google.gwt.requestfactory.shared.SimpleEnum;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Size;

/**
 * Domain object for SimpleFooRequest.
 */
public class SimpleFoo {
  /**
   * DO NOT USE THIS UGLY HACK DIRECTLY! Call {@link #get} instead.
   */
  private static SimpleFoo jreTestSingleton = new SimpleFoo();

  private static Long nextId = 1L;

  public static Long countSimpleFoo() {
    return 1L;
  }

  public static List<SimpleFoo> findAll() {
    return Collections.singletonList(get());
  }

  public static SimpleFoo findSimpleFoo(String id) {
    return findSimpleFooById(id);
  }

  public static SimpleFoo findSimpleFooById(String id) {
    get().setId(id);
    return get();
  }

  public static synchronized SimpleFoo get() {
    HttpServletRequest req = RequestFactoryServlet.getThreadLocalRequest();
    if (req == null) {
      // May be in a JRE test case, use the singleton
      return jreTestSingleton;
    } else {
      /*
       * This will not behave entirely correctly unless we have a servlet filter
       * that doesn't allow any requests to be processed unless they're
       * associated with an existing session.
       */
      SimpleFoo value = (SimpleFoo) req.getSession().getAttribute(
          SimpleFoo.class.getCanonicalName());
      if (value == null) {
        value = reset();
      }
      return value;
    }
  }

  public static SimpleFoo getSingleton() {
    return get();
  }

  public static synchronized SimpleFoo reset() {
    SimpleFoo instance = new SimpleFoo();
    HttpServletRequest req = RequestFactoryServlet.getThreadLocalRequest();
    if (req == null) {
      jreTestSingleton = instance;
    } else {
      req.getSession().setAttribute(SimpleFoo.class.getCanonicalName(),
          instance);
    }
    return instance;
  }

  @SuppressWarnings("unused")
  private static Integer privateMethod() {
    return 0;
  }

  @Id
  private String id = "1L";

  Integer version = 1;

  @Size(min = 3, max = 30)
  private String userName;
  private String password;

  private Character charField;
  private Long longField;

  private BigDecimal bigDecimalField;

  private BigInteger bigIntField;
  private Integer intId = -1;
  private Short shortField;

  private Byte byteField;

  private Date created;
  private Double doubleField;

  private Float floatField;

  private SimpleEnum enumField;
  private Boolean boolField;

  private Boolean otherBoolField;
  private Integer pleaseCrashField;

  private SimpleBar barField;
  private SimpleFoo fooField;

  private String nullField;
  private SimpleBar barNullField;

  public SimpleFoo() {
    intId = 42;
    version = 1;
    userName = "GWT";
    longField = 8L;
    enumField = SimpleEnum.FOO;
    created = new Date();
    barField = SimpleBar.getSingleton();
    boolField = true;
    nullField = null;
    barNullField = null;
    pleaseCrashField = 0;
  }

  public Long countSimpleFooWithUserNameSideEffect() {
    get().setUserName(userName);
    return 1L;
  }

  public SimpleBar getBarField() {
    return barField;
  }

  public SimpleBar getBarNullField() {
    return barNullField;
  }

  /**
   * @return the bigDecimalField
   */
  public BigDecimal getBigDecimalField() {
    return bigDecimalField;
  }

  /**
   * @return the bigIntegerField
   */
  public BigInteger getBigIntField() {
    return bigIntField;
  }

  public Boolean getBoolField() {
    return boolField;
  }

  /**
   * @return the byteField
   */
  public Byte getByteField() {
    return byteField;
  }

  /**
   * @return the charField
   */
  public Character getCharField() {
    return charField;
  }

  public Date getCreated() {
    return created;
  }

  /**
   * @return the doubleField
   */
  public Double getDoubleField() {
    return doubleField;
  }

  public SimpleEnum getEnumField() {
    return enumField;
  }

  /**
   * @return the floatField
   */
  public Float getFloatField() {
    return floatField;
  }

  public SimpleFoo getFooField() {
    return fooField;
  }

  public String getId() {
    return id;
  }

  public Integer getIntId() {
    return intId;
  }

  public Long getLongField() {
    return longField;
  }

  public String getNullField() {
    return nullField;
  }

  /**
   * @return the otherBoolField
   */
  public Boolean getOtherBoolField() {
    return otherBoolField;
  }

  public String getPassword() {
    return password;
  }

  public Integer getPleaseCrash() {
    return pleaseCrashField;
  }

  /**
   * @return the shortField
   */
  public Short getShortField() {
    return shortField;
  }

  public String getUserName() {
    return userName;
  }

  public Integer getVersion() {
    return version;
  }

  public String hello(SimpleBar bar) {
    return "Greetings " + bar.getUserName() + " from " + getUserName();
  }

  public void persist() {
    setId(Long.toString(nextId++) + "L");
  }

  public SimpleFoo persistAndReturnSelf() {
    persist();
    return this;
  }

  public void setBarField(SimpleBar barField) {
    this.barField = barField;
  }

  public void setBarNullField(SimpleBar barNullField) {
    this.barNullField = barNullField;
  }

  /**
   * @param bigDecimalField the bigDecimalField to set
   */
  public void setBigDecimalField(BigDecimal bigDecimalField) {
    this.bigDecimalField = bigDecimalField;
  }

  /**
   * @param bigIntegerField the bigIntegerField to set
   */
  public void setBigIntField(BigInteger bigIntegerField) {
    this.bigIntField = bigIntegerField;
  }

  public void setBoolField(Boolean bool) {
    boolField = bool;
  }

  /**
   * @param byteField the byteField to set
   */
  public void setByteField(Byte byteField) {
    this.byteField = byteField;
  }

  /**
   * @param charField the charField to set
   */
  public void setCharField(Character charField) {
    this.charField = charField;
  }

  public void setCreated(Date created) {
    this.created = created;
  }

  /**
   * @param doubleField the doubleField to set
   */
  public void setDoubleField(Double doubleField) {
    this.doubleField = doubleField;
  }

  public void setEnumField(SimpleEnum enumField) {
    this.enumField = enumField;
  }

  /**
   * @param floatField the floatField to set
   */
  public void setFloatField(Float floatField) {
    this.floatField = floatField;
  }

  public void setFooField(SimpleFoo fooField) {
    this.fooField = fooField;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setIntId(Integer id) {
    this.intId = id;
  }

  public void setLongField(Long longField) {
    this.longField = longField;
  }

  public void setNullField(String nullField) {
    this.nullField = nullField;
  }

  /**
   * @param otherBoolField the otherBoolField to set
   */
  public void setOtherBoolField(Boolean otherBoolField) {
    this.otherBoolField = otherBoolField;
  }

  public void setPleaseCrash(Integer crashIf42) {
    if (crashIf42 == 42) {
      throw new UnsupportedOperationException("THIS EXCEPTION IS EXPECTED BY A TEST");
    }
    pleaseCrashField = crashIf42;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * @param shortField the shortField to set
   */
  public void setShortField(Short shortField) {
    this.shortField = shortField;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }
}
