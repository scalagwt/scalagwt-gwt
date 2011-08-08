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
package com.google.gwt.validation.rebind;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.UnsafeNativeLong;
import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JPrimitiveType;
import com.google.gwt.core.ext.typeinfo.JType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.dev.jjs.ast.JProgram;
import com.google.gwt.thirdparty.guava.common.base.Function;
import com.google.gwt.thirdparty.guava.common.base.Functions;
import com.google.gwt.thirdparty.guava.common.base.Joiner;
import com.google.gwt.thirdparty.guava.common.base.Predicate;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableList;
import com.google.gwt.thirdparty.guava.common.collect.ImmutableSet;
import com.google.gwt.thirdparty.guava.common.collect.Iterables;
import com.google.gwt.thirdparty.guava.common.collect.Maps;
import com.google.gwt.thirdparty.guava.common.collect.Ordering;
import com.google.gwt.thirdparty.guava.common.collect.Sets;
import com.google.gwt.thirdparty.guava.common.primitives.Primitives;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import com.google.gwt.validation.client.impl.AbstractGwtSpecificValidator;
import com.google.gwt.validation.client.impl.ConstraintDescriptorImpl;
import com.google.gwt.validation.client.impl.GwtBeanDescriptor;
import com.google.gwt.validation.client.impl.GwtBeanDescriptorImpl;
import com.google.gwt.validation.client.impl.GwtValidationContext;
import com.google.gwt.validation.client.impl.PropertyDescriptorImpl;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintViolation;
import javax.validation.Payload;
import javax.validation.UnexpectedTypeException;
import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.metadata.BeanDescriptor;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.metadata.PropertyDescriptor;

/**
 * <strong>EXPERIMENTAL</strong> and subject to change. Do not use this in
 * production code.
 * <p>
 * Creates a {@link com.google.gwt.validation.client.GwtSpecificValidator}.
 * <p>
 * This class is not thread safe.
 */
public class GwtSpecificValidatorCreator extends AbstractCreator {
  private static enum Stage {
    OBJECT, PROPERTY, VALUE
  }

  static final JType[] NO_ARGS = new JType[]{};

  private static final String DEFAULT_VIOLATION_VAR = "violations";

  private static final Annotation[] NO_ANNOTATIONS = new Annotation[]{};

  private static Function<java.beans.PropertyDescriptor, String>
      PROPERTY_DESCRIPTOR_TO_NAME =
          new Function<java.beans.PropertyDescriptor, String>() {
    public String apply(java.beans.PropertyDescriptor pd) {
      return pd.getName();
    }
  };

  private static Function<Object, String> TO_LITERAL = new Function<Object, String>() {

    public String apply(Object input) {
      return asLiteral(input);
    }
  };

  public static String asGetter(PropertyDescriptor p) {
    return "get" + capitalizeFirstLetter(p.getPropertyName());
  }

  /**
   * Returns the literal value of an object that is suitable for inclusion in
   * Java Source code.
   *
   * <p>
   * Supports all types that {@link Annotation) value can have.
   *
   *
   * @throws IllegalArgumentException if the type of the object does not have a java literal form.
   */
  public static String asLiteral(Object value) throws IllegalArgumentException {
    Class<?> clazz = value.getClass();
    JProgram jProgram = new JProgram(null);

    if (clazz.isArray()) {
      StringBuilder sb = new StringBuilder();
      Object[] array = (Object[]) value;

      sb.append("new " + clazz.getComponentType().getCanonicalName() + "[] ");
      sb.append("{");
      boolean first = true;
      for (Object object : array) {
        if (first) {
          first = false;
        } else {
          sb.append(",");
        }
        sb.append(asLiteral(object));
      }
      sb.append("}");
      return sb.toString();
    }

    if (value instanceof Boolean) {
      return jProgram.getLiteralBoolean(((Boolean) value).booleanValue())
          .toSource();
    } else if (value instanceof Byte) {
      return jProgram.getLiteralInt(((Byte) value).byteValue()).toSource();
    } else if (value instanceof Character) {
      return jProgram.getLiteralChar(((Character) value).charValue())
          .toSource();
    } else if (value instanceof Class<?>) {
      return ((Class<?>) ((Class<?>) value)).getCanonicalName() + ".class";
    } else if (value instanceof Double) {
      return jProgram.getLiteralDouble(((Double) value).doubleValue())
          .toSource();
    } else if (value instanceof Enum) {
      return value.getClass().getCanonicalName() + "."
          + ((Enum<?>) value).name();
    } else if (value instanceof Float) {
      return jProgram.getLiteralFloat(((Float) value).floatValue()).toSource();
    } else if (value instanceof Integer) {
      return jProgram.getLiteralInt(((Integer) value).intValue()).toSource();
    } else if (value instanceof Long) {
      return jProgram.getLiteralLong(((Long) value).intValue()).toSource();
    } else if (value instanceof String) {
      return '"' + Generator.escape((String) value) + '"';
    } else {
      // TODO(nchalko) handle Annotation types
      throw new IllegalArgumentException(value.getClass()
          + " can not be represented as a Java Literal.");
    }
  }

  public static String capitalizeFirstLetter(String propertyName) {
    if (propertyName == null) {
      return null;
    }
    if (propertyName.length() == 0) {
      return "";
    }
    String cap = propertyName.substring(0, 1).toUpperCase();
    if (propertyName.length() > 1) {
      cap += propertyName.substring(1);
    }
    return cap;
  }

  public static boolean isIterableOrMap(Class<?> elementClass) {
    // TODO(nchalko) handle iterables everywhere this is called.
    return elementClass.isArray()
        || Iterable.class.isAssignableFrom(elementClass)
        || Map.class.isAssignableFrom(elementClass);
  }
  static <T extends Annotation> Class<?> getTypeOfConstraintValidator(
      Class<? extends ConstraintValidator<T, ?>> constraintClass) {

    for (Method method :  constraintClass.getMethods()) {
      if (method.getName().equals("isValid")
          && method.getParameterTypes().length == 2
          && method.getReturnType().isAssignableFrom(Boolean.TYPE)) {
        return method.getParameterTypes()[0];
      }
    }
    throw new IllegalStateException(
        "ConstraintValidators must have a isValid method");
  }
  // Visible for testing
  static <A extends Annotation> ImmutableSet<Class<? extends ConstraintValidator<A, ?>>> getValidatorForType(
      Class<?> type,
      List<Class<? extends ConstraintValidator<A, ?>>> constraintValidatorClasses) {
    type = Primitives.wrap(type);
    Map<Class<?>, Class<? extends ConstraintValidator<A, ?>>> map = Maps.newHashMap();
    for (Class<? extends ConstraintValidator<A, ?>> constraintClass : constraintValidatorClasses) {
      Class<?> aType = Primitives.wrap(getTypeOfConstraintValidator(constraintClass));
      if (aType.isAssignableFrom(type)) {
        map.put(aType, constraintClass);
      }
    }
    // TODO(nchalko) implement per spec
    // Handle Arrays and Generics

    final Set<Class<?>> best = Util.findBestMatches(type, map.keySet());

    Predicate<Class<?>> inBest = new Predicate<Class<?>>() {

      public boolean apply(Class<?> key) {
        return best.contains(key);
      }
    };
    return ImmutableSet.copyOf(Maps.filterKeys(map, inBest).values());
  }

  /**
   * Gets the best {@link ConstraintValidator}.
   *
   * <p>
   * The ConstraintValidator chosen to validate a declared type
   * {@code targetType} is the one where the type supported by the
   * ConstraintValidator is a supertype of {@code targetType} and where there is
   * no other ConstraintValidator whose supported type is a supertype of
   * {@code type} and not a supertype of the chosen ConstraintValidator
   * supported type.
   *
   * @param constraint the constraint to find ConstraintValidators for.
   * @param targetType The type to find a ConstraintValidator for.
   * @return ConstraintValidator
   *
   * @throws UnexpectedTypeException if there is not exactly one maximally
   *           specific constraint validator for targetType.
   */
  private static <A extends Annotation> Class<? extends ConstraintValidator<A, ?>> getValidatorForType(
      ConstraintDescriptor<A> constraint, Class<?> targetType)
      throws UnexpectedTypeException {
    List<Class<? extends ConstraintValidator<A, ?>>> constraintValidatorClasses
        = constraint.getConstraintValidatorClasses();
    if (constraintValidatorClasses.isEmpty()) {
      throw new UnexpectedTypeException("No ConstraintValidator found for  "
          + constraint.getAnnotation());
    }
    ImmutableSet<Class<? extends ConstraintValidator<A, ?>>> best = getValidatorForType(
        targetType, constraintValidatorClasses);
    if (best.isEmpty()) {
      throw new UnexpectedTypeException("No " + constraint.getAnnotation()
          + " ConstraintValidator for type " + targetType);
    }
    if (best.size() > 1) {
      throw new UnexpectedTypeException("More than one maximally specific "
          + constraint.getAnnotation() + " ConstraintValidator for type "
          + targetType + ", found " + Ordering.usingToString().sortedCopy(best));
    }
    return Iterables.get(best, 0);
  }

  private final BeanHelper beanHelper;

  private final Set<BeanHelper> beansToValidate = Sets.newHashSet();

  private final JClassType beanType;

  private final Set<JField> fieldsToWrap = Sets.newHashSet();

  private Set<JMethod> gettersToWrap = Sets.newHashSet();

  private final TypeOracle oracle;

  public GwtSpecificValidatorCreator(JClassType validatorType,
      JClassType beanType, BeanHelper beanHelper, TreeLogger logger,
      GeneratorContext context) {
    super(context, logger, validatorType);
    this.oracle = context.getTypeOracle();
    this.beanType = beanType;
    this.beanHelper = beanHelper;
  }

  @Override
  protected void compose(ClassSourceFileComposerFactory composerFactory) {
    addImports(composerFactory, Annotation.class, ConstraintViolation.class,
        GWT.class, GwtBeanDescriptor.class, GwtValidationContext.class,
        HashSet.class, IllegalArgumentException.class, Set.class,
        ValidationException.class);
    composerFactory.setSuperclass(AbstractGwtSpecificValidator.class.getCanonicalName()
        + "<" + beanType.getQualifiedSourceName() + ">");
    composerFactory.addImplementedInterface(validatorType.getName());
  }

  @Override
  protected void writeClassBody(SourceWriter sw)
      throws UnableToCompleteException {
    writeFields(sw);
    sw.println();
    writeValidate(sw);
    sw.println();
    writeValidateProperty(sw);
    sw.println();
    writeValidateValue(sw);
    sw.println();
    writeGetDescriptor(sw);
    sw.println();
    writePropertyValidators(sw);
    sw.println();

    // Write the wrappers after we know which are needed
    writeWrappers(sw);
    sw.println();
  }

  protected void writeUnsafeNativeLongIfNeeded(SourceWriter sw, JType jType) {
    if (JPrimitiveType.LONG.equals(jType)) {
      // @com.google.gwt.core.client.UnsafeNativeLong
      sw.print("@");
      sw.println(UnsafeNativeLong.class.getCanonicalName());
    }
  }

  private <T> T[] asArray(Collection<?> collection, T[] array) {
    if (collection == null) {
      return null;
    }
    return collection.toArray(array);
  }

  private String constraintDescriptorVar(String name, int count) {
    String s = name + "_c" + count;
    return s;
  }

  private Annotation getAnnotation(PropertyDescriptor p, boolean useField,
      Class<? extends Annotation> expectedAnnotationClass) {
    Annotation annotation = null;
    if (useField) {
      JField field = beanType.findField(p.getPropertyName());
      if (field.getEnclosingType().equals(beanType)) {
        annotation = field.getAnnotation(expectedAnnotationClass);
      }
    } else {
      JMethod method = beanType.findMethod(asGetter(p), NO_ARGS);
      if (method.getEnclosingType().equals(beanType)) {
        annotation = method.getAnnotation(expectedAnnotationClass);
      }
    }
    return annotation;
  }

  private Annotation[] getAnnotations(PropertyDescriptor p,
      boolean useField) {
    Class<?> clazz = beanHelper.getClazz();
    if (useField) {
      try {
        Field field = clazz.getDeclaredField(p.getPropertyName());
        return field.getAnnotations();
      } catch (NoSuchFieldException ignore) {
        // Expected Case
      }
    } else {
      try {
        Method method = clazz.getMethod(asGetter(p));
        return method.getAnnotations();
      } catch (NoSuchMethodException ignore) {
        // Expected Case
      }
    }
    return NO_ANNOTATIONS;
  }

  private String getQualifiedSourceNonPrimitiveType(JType elementType) {
    JPrimitiveType primitive = elementType.isPrimitive();
    return primitive == null ? elementType.getQualifiedSourceName()
        : primitive.getQualifiedBoxedSourceName();
  }

  private boolean hasMatchingAnnotation(Annotation expectedAnnotation,
      Annotation[] annotations) throws UnableToCompleteException {
    // See spec section 2.2. Applying multiple constraints of the same type
    for (Annotation annotation : annotations) {
      // annotations not annotated by @Constraint
      if (annotation.annotationType().getAnnotation(Constraint.class) == null) {
        try {
          // value element has a return type of an array of constraint
          // annotations
          Method valueMethod = annotation.annotationType().getMethod("value");
          Class<?> valueType = valueMethod.getReturnType();
          if (valueType.isArray()
              && Annotation.class.isAssignableFrom(valueType.getComponentType())) {
            Annotation[] valueAnnotions = (Annotation[]) valueMethod.invoke(annotation);
            for (Annotation annotation2 : valueAnnotions) {
              if (expectedAnnotation.equals(annotation2)) {
                return true;
              }
            }
          }
        } catch (NoSuchMethodException ignore) {
          // Expected Case.
        } catch (Exception e) {
          throw error(logger, e);
        }
      }
    }
    return false;
  }

  private boolean hasMatchingAnnotation(ConstraintDescriptor<?> constraint)
      throws UnableToCompleteException {
    Annotation expectedAnnotation = constraint.getAnnotation();
    Class<? extends Annotation> expectedAnnotationClass = expectedAnnotation.annotationType();
    if (expectedAnnotation.equals(beanHelper.getClazz().getAnnotation(
        expectedAnnotationClass))) {
      return true;
    }

    // See spec section 2.2. Applying multiple constraints of the same type
    Annotation[] annotations = beanHelper.getClazz().getAnnotations();
    return hasMatchingAnnotation(expectedAnnotation, annotations);
  }

  private boolean hasMatchingAnnotation(PropertyDescriptor p, boolean useField,
      ConstraintDescriptor<?> constraint) throws UnableToCompleteException {
    Annotation expectedAnnotation = constraint.getAnnotation();
    Class<? extends Annotation> expectedAnnotationClass =
        expectedAnnotation.annotationType();
    if (expectedAnnotation.equals(getAnnotation(p, useField,
        expectedAnnotationClass))) {
      return true;
    }
    return hasMatchingAnnotation(expectedAnnotation,
        getAnnotations(p, useField));
  }

  private boolean hasValid(PropertyDescriptor p, boolean useField) {
    return getAnnotation(p, useField, Valid.class) != null;
  }

  private boolean isPropertyConstrained(BeanHelper helper, PropertyDescriptor p) {
    Set<PropertyDescriptor> propertyDescriptors =
        helper.getBeanDescriptor().getConstrainedProperties();
    Predicate<PropertyDescriptor> nameMatches = newPropertyNameMatches(p);
    return Iterables.any(propertyDescriptors, nameMatches);
  }

  private Predicate<PropertyDescriptor> newPropertyNameMatches(
      final PropertyDescriptor p) {
    return new Predicate<PropertyDescriptor>() {
      public boolean apply(PropertyDescriptor input) {
        return input.getPropertyName().equals(p.getPropertyName());
      }
    };
  }

  private String toWrapperName(JField field) {
    return "_" + field.getName();
  }

  /**
   * @param method
   * @return
   */
  private String toWrapperName(JMethod method) {
    return "_" + method.getName();
  }

  private String validateMethodFieldName(PropertyDescriptor p) {
    return "validateProperty_" + p.getPropertyName();
  }

  private String validateMethodGetterName(PropertyDescriptor p) {
    return "validateProperty_get" + p.getPropertyName();
  }

  private void writeBeanDescriptor(SourceWriter sw) {
    BeanDescriptor beanDescriptor = beanHelper.getBeanDescriptor();

    // private final GwtBeanDescriptor <MyBean> beanDescriptor =
    sw.print("private final ");
    sw.print(GwtBeanDescriptor.class.getCanonicalName());
    sw.print("<" + beanHelper.getTypeCanonicalName() + ">");
    sw.println(" beanDescriptor = ");
    sw.indent();
    sw.indent();

    // GwtBeanDescriptorImpl.builder(Order.class)
    sw.print(GwtBeanDescriptorImpl.class.getCanonicalName());
    sw.println(".builder(" + beanHelper.getTypeCanonicalName() + ".class)");
    sw.indent();
    sw.indent();

    // .setConstrained(true)
    sw.println(".setConstrained(" + beanDescriptor.isBeanConstrained() + ")");

    for (int count = 0; count < beanDescriptor.getConstraintDescriptors().size(); count++) {
      // .add(c0)
      sw.println(".add(" + constraintDescriptorVar("this", count) + ")");
    }

    // .put("myProperty", myProperty_pd)
    for (PropertyDescriptor p : beanDescriptor.getConstrainedProperties()) {
      sw.print(".put(\"");
      sw.print(p.getPropertyName());
      sw.print("\", ");
      sw.print(p.getPropertyName());
      sw.println("_pd)");
    }

    // .build();
    sw.println(".build();");
    sw.outdent();
    sw.outdent();
    sw.outdent();
    sw.outdent();
  }

  private void writeCatchUnexpectedException(SourceWriter sw, String message) {
    // } catch (IllegalArgumentException e) {
    sw.outdent();
    sw.println("} catch (IllegalArgumentException e) {");
    sw.indent();

    // throw e;
    sw.println("throw e;");

    // } catch (ValidationException e) {
    sw.outdent();
    sw.println("} catch (ValidationException e) {");
    sw.indent();

    // throw e;
    sw.println("throw e;");

    // } catch (Exception e) {
    sw.outdent();
    sw.println("} catch (Exception e) {");
    sw.indent();

    // throw new ValidationException("my message", e);
    sw.println("throw new ValidationException(" + message + ", e);");

    // }
    sw.outdent();
    sw.println("}");
  }

  private void writeConstraintDescriptor(SourceWriter sw,
      ConstraintDescriptor<? extends Annotation> constraint,
      String constraintDescripotorVar) throws UnableToCompleteException {
    Class<? extends Annotation> annotationType =
        constraint.getAnnotation().annotationType();

    // First list all composing constraints
    int count = 0;
    for (ConstraintDescriptor<?> composingConstraint :
        constraint.getComposingConstraints()) {
      writeConstraintDescriptor(sw, composingConstraint,
          constraintDescripotorVar + "_" + count++);
    }

    // private final ConstraintDescriptorImpl<MyAnnotation> constraintDescriptor = ;
    sw.print("private final ");
    sw.print(ConstraintDescriptorImpl.class.getCanonicalName());
    sw.print("<");

    sw.print(annotationType.getCanonicalName());
    sw.print(">");

    sw.println(" " + constraintDescripotorVar + "  = ");
    sw.indent();
    sw.indent();

    // ConstraintDescriptorImpl.<MyConstraint> builder()
    sw.print(ConstraintDescriptorImpl.class.getCanonicalName());
    sw.print(".<");

    sw.print(annotationType.getCanonicalName());
    sw.println("> builder()");
    sw.indent();
    sw.indent();

    // .setAnnotation(new MyAnnotation )
    sw.println(".setAnnotation( ");
    sw.indent();
    sw.indent();
    writeNewAnnotation(sw, constraint);
    sw.println(")");
    sw.outdent();
    sw.outdent();

    // .setAttributes(builder()
    sw.println(".setAttributes(attributeBuilder()");
    sw.indent();

    for (Map.Entry<String, Object> entry :
        constraint.getAttributes().entrySet()) {
      // .put(key, value)
      sw.print(".put(");
      sw.print(asLiteral(entry.getKey()));
      sw.print(", ");
      sw.print(asLiteral(entry.getValue()));
      sw.println(")");
    }

    // .build())
    sw.println(".build())");
    sw.outdent();

    // .setConstraintValidatorClasses(classes )
    sw.print(".setConstraintValidatorClasses(");
    sw.print(asLiteral(asArray(constraint.getConstraintValidatorClasses(),
        new Class[0])));
    sw.println(")");

    int ccCount = constraint.getComposingConstraints().size();
    for (int i = 0; i < ccCount; i++) {
      // .addComposingConstraint(cX_X)
      sw.print(".addComposingConstraint(");
      sw.print(constraintDescripotorVar + "_" + i);
      sw.println(")");
    }

    // .getGroups(groups)
    sw.print(".setGroups(");
    Set<Class<?>> groups = constraint.getGroups();
    sw.print(asLiteral(asArray(groups, new Class<?>[0])));
    sw.println(")");

    // .setPayload(payload)
    sw.print(".setPayload(");
    Set<Class<? extends Payload>> payload = constraint.getPayload();
    sw.print(asLiteral(asArray(payload, new Class[0])));
    sw.println(")");

    // .setReportAsSingleViolation(boolean )
    sw.print(".setReportAsSingleViolation(");
    sw.print(Boolean.valueOf(constraint.isReportAsSingleViolation())
        .toString());
    sw.println(")");

    // .build();
    sw.println(".build();");
    sw.outdent();
    sw.outdent();

    sw.outdent();
    sw.outdent();
    sw.println();
  }

  private void writeFields(SourceWriter sw) throws UnableToCompleteException {

    // Create a static array of all valid property names.
    BeanInfo beanInfo;
    try {
      beanInfo = Introspector.getBeanInfo(beanHelper.getClazz());
    } catch (IntrospectionException e) {
      throw error(logger, e);
    }

    // private static final java.util.List<String> ALL_PROPERTY_NAMES =
    sw.println("private static final java.util.List<String> ALL_PROPERTY_NAMES = ");
    sw.indent();
    sw.indent();

    // Collections.<String>unmodifiableList(
    sw.println("java.util.Collections.<String>unmodifiableList(");
    sw.indent();
    sw.indent();

    // java.util.Arrays.<String>asList(
    sw.print("java.util.Arrays.<String>asList(");

    // "foo","bar" );
    sw.print(Joiner.on(",").join(
        Iterables.transform(
            ImmutableList.copyOf(beanInfo.getPropertyDescriptors()),
            Functions.compose(TO_LITERAL, PROPERTY_DESCRIPTOR_TO_NAME))));
    sw.println("));");
    sw.outdent();
    sw.outdent();
    sw.outdent();
    sw.outdent();

    // Create a variable for each constraint of each property
    for (PropertyDescriptor p :
         beanHelper.getBeanDescriptor().getConstrainedProperties()) {
      int count = 0;
      for (ConstraintDescriptor<?> constraint : p.getConstraintDescriptors()) {
        writeConstraintDescriptor(sw, constraint,
            constraintDescriptorVar(p.getPropertyName(), count++));
      }
      writePropertyDescriptor(sw, p);
      if (p.isCascaded()) {
        beansToValidate.add(isIterableOrMap(p.getElementClass())
            ? createBeanHelper(beanHelper.getAssociationType(p, true))
            : createBeanHelper(p.getElementClass()));
      }
    }

    // Create a variable for each constraint of this class.
    int count = 0;
    for (ConstraintDescriptor<?> constraint :
        beanHelper.getBeanDescriptor().getConstraintDescriptors()) {
      writeConstraintDescriptor(sw, constraint,
          constraintDescriptorVar("this", count++));
    }

    // Now write the BeanDescriptor after we already have the
    // PropertyDescriptors and class constraints
    writeBeanDescriptor(sw);
    sw.println();
  }

  private void writeFieldWrapperMethod(SourceWriter sw, JField field) {
    writeUnsafeNativeLongIfNeeded(sw, field.getType());

    // private native fieldType _fieldName(Bean object) /*-{
    sw.print("private native ");

    sw.print(field.getType().getQualifiedSourceName());
    sw.print(" ");
    sw.print(toWrapperName(field));
    sw.print("(");
    sw.print(beanType.getName());
    sw.println(" object) /*-{");
    sw.indent();

    // return object.@com.examples.Bean::myMethod();
    sw.print("return object.@");
    sw.print(field.getEnclosingType().getQualifiedSourceName());
    sw.print("::" + field.getName());
    sw.println(";");

    // }-*/;
    sw.outdent();
    sw.println("}-*/;");
  }

  private void writeGetDescriptor(SourceWriter sw) {
    // public GwtBeanDescriptor<beanType> getConstraints() {
    sw.print("public ");
    sw.print("GwtBeanDescriptor<" + beanHelper.getTypeCanonicalName() + "> ");
    sw.println("getConstraints() {");
    sw.indent();

    // return beanDescriptor;
    sw.println("return beanDescriptor;");

    sw.outdent();
    sw.println("}");
  }

  private void writeGetterWrapperMethod(SourceWriter sw, JMethod method) {
    writeUnsafeNativeLongIfNeeded(sw, method.getReturnType());

    // private native fieldType _getter(Bean object) /*={
    sw.print("private native ");
    sw.print(method.getReturnType().getQualifiedSourceName());
    sw.print(" ");
    sw.print(toWrapperName(method));
    sw.print("(");
    sw.print(beanType.getName());
    sw.println(" object) /*-{");
    sw.indent();

    // return object.@com.examples.Bean::myMethod()();
    sw.print("return object.");
    sw.print(method.getJsniSignature());
    sw.println("();");

    // }-*/;
    sw.outdent();
    sw.println("}-*/;");
  }

  private void writeIfPropertyNameNotFound(SourceWriter sw) {
    // if (!ALL_PROPERTY_NAMES.contains(propertyName)) {
    sw.println(" if (!ALL_PROPERTY_NAMES.contains(propertyName)) {");
    sw.indent();

    // throw new IllegalArgumentException(propertyName
    // +"is not a valid property of myClass");
    sw.print("throw new ");
    sw.print(IllegalArgumentException.class.getCanonicalName());
    sw.print("( propertyName +\" is not a valid property of ");
    sw.print(beanType.getQualifiedSourceName());
    sw.println("\");");

    // }
    sw.outdent();
    sw.println("}");
  }

  private void writeNewAnnotation(SourceWriter sw,
      ConstraintDescriptor<? extends Annotation> constraint)
      throws UnableToCompleteException {
    Annotation annotation = constraint.getAnnotation();
    Class<? extends Annotation> annotationType = annotation.annotationType();

    // new MyAnnotation () {
    sw.print("new ");
    sw.print(annotationType.getCanonicalName());
    sw.println("(){");
    sw.indent();
    sw.indent();

    // public Class<? extends Annotation> annotationType() { return
    // MyAnnotation.class; }
    sw.print("public Class<? extends Annotation> annotationType() {  return ");
    sw.print(annotationType.getCanonicalName());
    sw.println(".class; }");

    for (Method method : annotationType.getMethods()) {
      // method.isAbstract would be better
      if (method.getDeclaringClass().equals(annotation.annotationType())) {
        // public returnType method() { return value ;}
        sw.print("public ");
        sw.print(method.getReturnType().getCanonicalName()); // TODO handle
                                                             // generics
        sw.print(" ");
        sw.print(method.getName());
        sw.print("() { return ");

        try {
          Object value = method.invoke(annotation);
          sw.print(asLiteral(value));
        } catch (IllegalArgumentException e) {
          throw error(logger, e);
        } catch (IllegalAccessException e) {
          throw error(logger, e);
        } catch (InvocationTargetException e) {
          throw error(logger, e);
        }
        sw.println(";}");
      }
    }

    sw.outdent();
    sw.outdent();
    sw.println("}");
  }

  private void writeNewViolations(SourceWriter sw) {
    writeNewViolations(sw, DEFAULT_VIOLATION_VAR);
  }

  private void writeNewViolations(SourceWriter sw, String violationName) {
    // Set<ConstraintViolation<T>> violations =
    sw.print("Set<ConstraintViolation<T>> ");
    sw.print(violationName);
    sw.println(" = ");
    sw.indent();
    sw.indent();

    // new HashSet<ConstraintViolation<T>>();
    sw.println("new HashSet<ConstraintViolation<T>>();");
    sw.outdent();
    sw.outdent();
  }

  /**
   * @param sw
   * @param p
   */
  private void writePropertyDescriptor(SourceWriter sw, PropertyDescriptor p) {
    // private final PropertyDescriptor myProperty_pd =
    sw.print("private final ");
    sw.print(PropertyDescriptor.class.getCanonicalName());
    sw.print(" ");
    sw.print(p.getPropertyName());
    sw.println("_pd =");
    sw.indent();
    sw.indent();

    // new PropertyDescriptorImpl(
    sw.println("new " + PropertyDescriptorImpl.class.getCanonicalName() + "(");
    sw.indent();
    sw.indent();

    // "myProperty",
    sw.println("\"" + p.getPropertyName() + "\",");

    // MyType.class,
    sw.println(p.getElementClass().getCanonicalName() + ".class,");

    // isCascaded,
    sw.print(Boolean.toString(p.isCascaded()));

    // myProperty_c0,
    // myProperty_c1 );
    int size = p.getConstraintDescriptors().size();
    for (int i = 0; i < size; i++) {
      sw.println(","); // Print the , for the previous line
      sw.print(constraintDescriptorVar(p.getPropertyName(), i));
    }
    sw.println(");");

    sw.outdent();
    sw.outdent();
    sw.outdent();
    sw.outdent();
  }

  private void writePropertyValidators(SourceWriter sw)
      throws UnableToCompleteException {
    for (PropertyDescriptor p :
        beanHelper.getBeanDescriptor().getConstrainedProperties()) {
      if (beanHelper.hasField(p)) {
        writeValidatePropertyMethod(sw, p, true);
        sw.println();
      }
      if (beanHelper.hasGetter(p)) {
        writeValidatePropertyMethod(sw, p, false);
        sw.println();
      }
    }
  }

  private void writeValidate(SourceWriter sw) throws UnableToCompleteException {
    // public <T> Set<ConstraintViolation<T>> validate(
    sw.println("public <T> Set<ConstraintViolation<T>> validate(");

    // GwtValidationContext<T> context, BeanType object, Class<?>... groups) {
    sw.indent();
    sw.indent();
    sw.println("GwtValidationContext<T> context,");
    sw.println(beanHelper.getTypeCanonicalName() + " object,");
    sw.println("Class<?>... groups) {");
    sw.outdent();

    // try {
    sw.println("try {");
    sw.indent();

    writeNewViolations(sw);

    // context.addValidatedObject(object);
    sw.println("context.addValidatedObject(object);");

    // /// For each group

    // TODO(nchalko) handle the sequence in the AbstractValidator

    // See JSR 303 section 3.5
    // all reachable fields
    // all reachable getters (both) at once
    // including all reachable and cascadable associations

    Set<PropertyDescriptor> properties = beanHelper.getBeanDescriptor().getConstrainedProperties();

    for (PropertyDescriptor p : properties) {
      writeValidatePropertyCall(sw, p, false);
    }

    // all class level constraints
    int count = 0;
    Class<?> clazz = beanHelper.getClazz();
    for (ConstraintDescriptor<?> constraint : beanHelper.getBeanDescriptor().getConstraintDescriptors()) {
      if (hasMatchingAnnotation(constraint)) {

        if (!constraint.getConstraintValidatorClasses().isEmpty()) {
          Class<? extends ConstraintValidator<? extends Annotation, ?>> validatorClass = getValidatorForType(
              constraint, clazz);

          // validate(context, violations, null, object,
          sw.print("validate(context, violations, null, object, ");

          // new MyValidtor();
          sw.print("new ");
          sw.print(validatorClass.getCanonicalName());
          sw.print("(), "); // TODO(nchalko) use ConstraintValidatorFactory

          // this.aConstraintDescriptor, groups);;
          sw.print(constraintDescriptorVar("this", count));
          sw.println(", groups);");
        } else if (constraint.getComposingConstraints().isEmpty()) {
          // TODO(nchalko) What does the spec say to do here.
          logger.log(TreeLogger.WARN, "No ConstraintValidator of " + constraint
              + " for type " + clazz);
        }
        // TODO(nchalko) handle constraint.isReportAsSingleViolation() and
        // hasComposingConstraints
      }
      count++;
    }

    // validate super classes and super interfaces
    writeValidateInheritance(sw, clazz, Stage.OBJECT, null);

    // return violations;
    sw.println("return violations;");

    writeCatchUnexpectedException(sw,
        "\"Error validating " + beanHelper.getTypeCanonicalName() + "\"");

    // }
    sw.outdent();
    sw.println("}");
  }

  private void writeValidateConstraint(SourceWriter sw, PropertyDescriptor p,
      Class<?> elementClass, ConstraintDescriptor<?> constraint,
      String constraintDescriptorVar) throws UnableToCompleteException {
    writeValidateConstraint(sw, p, elementClass, constraint,
        constraintDescriptorVar, DEFAULT_VIOLATION_VAR);
  }

  /**
   * Writes the call to actually validate a constraint, including its composite
   * constraints.
   * <p>
   * If the constraint is annotated as
   * {@link javax.validation.ReportAsSingleViolation ReportAsSingleViolation},
   * then is called recursively and the {@code violationsVar} is changed to
   * match the the {@code constraintDescriptorVar}.
   *
   * @param sw the Source Writer
   * @param p the property
   * @param elementClass The class of the Element
   * @param constraint the constraint to validate.
   * @param constraintDescriptorVar the name of the constraintDescriptor
   *          variable.
   * @param violationsVar the name of the variable to hold violations
   * @throws UnableToCompleteException
   */
  private void writeValidateConstraint(SourceWriter sw, PropertyDescriptor p,
      Class<?> elementClass, ConstraintDescriptor<?> constraint,
      String constraintDescriptorVar, String violationsVar)
      throws UnableToCompleteException {
    boolean isComposite = !constraint.getComposingConstraints().isEmpty();
    boolean firstReportAsSingleViolation =
        constraint.isReportAsSingleViolation()
        && violationsVar.equals(DEFAULT_VIOLATION_VAR) && isComposite;
    boolean reportAsSingleViolation = firstReportAsSingleViolation
        || !violationsVar.equals(DEFAULT_VIOLATION_VAR);
    boolean hasValidator = !constraint.getConstraintValidatorClasses()
        .isEmpty();
    String compositeViolationsVar = constraintDescriptorVar + "_violations";

    // Only do this the first time in a constraint composition.
    if (firstReportAsSingleViolation) {
      // Report myConstraint as Single Violation
      sw.print("// Report ");
      sw.print(constraint.getAnnotation().annotationType().getCanonicalName());
      sw.println(" as Single Violation");
      writeNewViolations(sw, compositeViolationsVar);
    }

    if (hasValidator) {
      Class<? extends ConstraintValidator<? extends Annotation, ?>> validatorClass;
      try {
        validatorClass = getValidatorForType(constraint, elementClass);
      } catch (UnexpectedTypeException e) {
        throw error(logger, e);
      }

      if (firstReportAsSingleViolation) {
        // if (!
        sw.println("if (!");
        sw.indent();
        sw.indent();
      }

      // validate(myContext, violations object, value, new MyValidator(),
      // constraintDescriptor, groups));
      sw.print("validate(myContext, ");
      sw.print(violationsVar);
      sw.print(", object, value, ");
      sw.print("new "); // TODO(nchalko) use ConstraintValidatorFactory
      sw.print(validatorClass.getCanonicalName());
      sw.print("(), ");
      sw.print(constraintDescriptorVar);
      sw.print(", groups)");
      if (firstReportAsSingleViolation) {
        // ) {
        sw.println(") {");
        sw.outdent();

      } else if (!reportAsSingleViolation) {
        // ;
        sw.println(";");
      } else if (isComposite) {
        // ||
        sw.println(" ||");
      }
    } else if (!isComposite) {
      // TODO(nchalko) What does the spec say to do here.
      logger.log(TreeLogger.WARN, "No ConstraintValidator of " + constraint + " for "
          + p.getPropertyName() + " of type " + elementClass);
    }

    if (firstReportAsSingleViolation) {
      // if (
      sw.print("if (");
      sw.indent();
      sw.indent();
    }
    int count = 0;

    for (ConstraintDescriptor<?> compositeConstraint : constraint
        .getComposingConstraints()) {
      String compositeVar = constraintDescriptorVar + "_" + count++;
      writeValidateConstraint(sw, p, elementClass, compositeConstraint,
          compositeVar, firstReportAsSingleViolation ? compositeViolationsVar
              : violationsVar);
      if (!reportAsSingleViolation) {
        // ;
        sw.println(";");
      } else {
        // ||
        sw.println(" ||");
      }
    }
    if (isComposite && reportAsSingleViolation) {
      // false
      sw.print("false");
    }
    if (firstReportAsSingleViolation) {
      // ) {
      sw.println(" ) {");
      sw.outdent();

      // addSingleViolation(myContext, violations, object, value,
      // constraintDescriptor);
      sw.print("addSingleViolation(myContext, violations, object, value, ");
      sw.print(constraintDescriptorVar);
      sw.println(");");

      // }
      sw.outdent();
      sw.println("}");

      if (hasValidator) {
        // }
        sw.outdent();
        sw.println("}");
      }
    }
  }

  private void writeValidateFieldCall(SourceWriter sw, PropertyDescriptor p,
      boolean useValue) {
    String propertyName = p.getPropertyName();

    // validateProperty_<<field>>(context,
    sw.print(validateMethodFieldName(p));
    sw.print("(context, ");
    sw.print("violations, ");

    // null, (MyType) value,
    // or
    // object, object.getLastName(),
    if (useValue) {
      sw.print("null, ");
      sw.print("(");
      sw.print(getQualifiedSourceNonPrimitiveType(beanHelper.getElementType(p,
          true)));
      sw.print(") value");
    } else {
      sw.print("object, ");
      JField field = beanType.getField(propertyName);
      if (field.isPublic()) {
        sw.print("object.");
        sw.print(propertyName);
      } else {
        fieldsToWrap.add(field);
        sw.print(toWrapperName(field) + "(object)");
      }
    }
    sw.print(", ");

    // groups));
    sw.println("groups);");
  }

  private void writeValidateGetterCall(SourceWriter sw, PropertyDescriptor p,
      boolean useValue) {
    // validateProperty_get<<field>>(context, violations,
    sw.print(validateMethodGetterName(p));
    sw.print("(context, ");
    sw.print("violations, ");

    // object, object.getMyProp(),
    // or
    // null, (MyType) value,
    if (useValue) {
      sw.print("null, ");
      sw.print("(");
      sw.print(getQualifiedSourceNonPrimitiveType(beanHelper.getElementType(p,
          false)));
      sw.print(") value");
    } else {
      sw.print("object, ");
      JMethod method = beanType.findMethod(asGetter(p), NO_ARGS);
      if (method.isPublic()) {
        sw.print("object.");
        sw.print(asGetter(p));
        sw.print("()");
      } else {
        gettersToWrap.add(method);
        sw.print(toWrapperName(method) + "(object)");
      }
    }
    sw.print(", ");

    // groups);
    sw.println("groups);");
  }

  private void writeValidateInheritance(SourceWriter sw, Class<?> clazz,
      Stage stage, PropertyDescriptor property)
      throws UnableToCompleteException {
    writeValidateInterfaces(sw, clazz, stage, property);
    Class<?> superClass = clazz.getSuperclass();
    if (superClass != null) {
      writeValidatorCall(sw, superClass, stage, property);
    }
  }

  private void writeValidateInterfaces(SourceWriter sw, Class<?> clazz,
      Stage stage, PropertyDescriptor p) throws UnableToCompleteException {
    for (Class<?> type : clazz.getInterfaces()) {
      writeValidatorCall(sw, type, stage, p);
      writeValidateInterfaces(sw, type, stage, p);
    }
  }

  private void writeValidateIterable(SourceWriter sw, PropertyDescriptor p) {
    // int i = 0;
    sw.println("int i = 0;");

    // for (Object instance : value) {
    sw.println("for(Object instance : value) {");
    sw.indent();

    // if(instance != null && !context.alreadyValidated(instance)) {
    sw.println(" if(instance != null  && !context.alreadyValidated(instance)) {");
    sw.indent();

    // violations.addAll(
    sw.println("violations.addAll(");
    sw.indent();
    sw.indent();

    // context.getValidator().validate(
    sw.println("context.getValidator().validate(");
    sw.indent();
    sw.indent();

    Class<?> elementClass = p.getElementClass();
    if (elementClass.isArray() || List.class.isAssignableFrom(elementClass)) {
      // context.appendIndex("myProperty",i++),
      sw.print("context.appendIndex(\"");
      sw.print(p.getPropertyName());
      sw.println("\",i),");
    } else {
      // context.appendIterable("myProperty"),
      sw.print("context.appendIterable(\"");
      sw.print(p.getPropertyName());
      sw.println("\"),");
    }

    // instance, groups));
    sw.println("instance, groups));");
    sw.outdent();
    sw.outdent();
    sw.outdent();
    sw.outdent();

    // }
    sw.outdent();
    sw.println("}");

    // i++;
    sw.println("i++;");

    // }
    sw.outdent();
    sw.println("}");
  }

  private void writeValidateMap(SourceWriter sw, PropertyDescriptor p) {
    // for (Entry<?, Type> entry : value.entrySet()) {
    sw.print("for(");
    sw.print(Entry.class.getCanonicalName());
    sw.println("<?, ?> entry : value.entrySet()) {");
    sw.indent();

    // if(entry.getValue() != null &&
    // !context.alreadyValidated(entry.getValue())) {
    sw.println(" if(entry.getValue() != null && !context.alreadyValidated(entry.getValue())) {");
    sw.indent();

    // violations.addAll(
    sw.println("violations.addAll(");
    sw.indent();
    sw.indent();

    // context.getValidator().validate(
    sw.println("context.getValidator().validate(");
    sw.indent();
    sw.indent();

    // context.appendKey("myProperty",entry.getKey()),
    sw.print("context.appendKey(\"");
    sw.print(p.getPropertyName());
    sw.println("\",entry.getKey()),");

    // entry.getValue(), groups));
    sw.println("entry.getValue(), groups));");
    sw.outdent();
    sw.outdent();
    sw.outdent();
    sw.outdent();

    // }
    sw.outdent();
    sw.println("}");

    // }
    sw.outdent();
    sw.println("}");
  }

  private void writeValidateProperty(SourceWriter sw)
      throws UnableToCompleteException {
    // public <T> Set<ConstraintViolation<T>> validate(
    sw.println("public <T> Set<ConstraintViolation<T>> validateProperty(");

    // GwtValidationContext<T> context, BeanType object, String propertyName,
    // Class<?>... groups) throws ValidationException {
    sw.indent();
    sw.indent();
    sw.println("GwtValidationContext<T> context,");
    sw.println(beanHelper.getTypeCanonicalName() + " object,");
    sw.println("String propertyName,");
    sw.println("Class<?>... groups) throws ValidationException {");
    sw.outdent();

    // try {
    sw.println("try {");
    sw.indent();

    writeNewViolations(sw);

    for (PropertyDescriptor property : beanHelper.getBeanDescriptor().getConstrainedProperties()) {
      // if (propertyName.equals(myPropety)) {
      sw.print("if (propertyName.equals(\"");
      sw.print(property.getPropertyName());
      sw.println("\")) {");
      sw.indent();

      writeValidatePropertyCall(sw, property, false);

      // validate all super classes and interfaces
      writeValidateInheritance(sw, beanHelper.getClazz(), Stage.PROPERTY,
          property);

      // }
      sw.outdent();
      sw.print("} else ");
    }

    writeIfPropertyNameNotFound(sw);

    // return violations;
    sw.println("return violations;");

    writeCatchUnexpectedException(
        sw,
        "\"Error validating \" + propertyName + \" of "
        + beanHelper.getTypeCanonicalName() + "\"");

    // }
    sw.outdent();
    sw.println("}");
  }

  private void writeValidatePropertyCall(SourceWriter sw,
      PropertyDescriptor property, boolean useValue) {
    if (useValue) {
      // boolean valueTypeMatches = false;
      sw.println("boolean valueTypeMatches = false;");
    }
    if (beanHelper.hasGetter(property)) {
      if (useValue) {
        // if ( value == null || value instanceof propertyType) {
        sw.print("if ( value == null || value instanceof ");
        sw.print(getQualifiedSourceNonPrimitiveType(beanHelper.getElementType(
            property, false)));
        sw.println(") {");
        sw.indent();

        // valueTypeMatches = true;
        sw.println("valueTypeMatches = true;");
      }
      // validate_getMyProperty
      writeValidateGetterCall(sw, property, useValue);
      if (useValue) {
        // }
        sw.outdent();
        sw.println("}");
      }
    }

    if (beanHelper.hasField(property)) {
      if (useValue) {
        // if ( value == null || value instanceof propertyType) {
        sw.print("if ( value == null || value instanceof ");
        sw.print(getQualifiedSourceNonPrimitiveType(beanHelper.getElementType(
            property, true)));
        sw.println(") {");
        sw.indent();

        // valueTypeMatches = true;
        sw.println("valueTypeMatches = true;");
      }
      // validate_myProperty
      writeValidateFieldCall(sw, property, useValue);
      if (useValue) {
        // } else
        sw.outdent();
        sw.println("}");
      }
    }

    if (useValue & (beanHelper.hasGetter(property) || beanHelper.hasField(property))) {
      // if(!valueTypeMatches) {
      sw.println("if(!valueTypeMatches)  {");
      sw.indent();

      // throw new ValidationException(value.getClass +
      // " is not a valid type for " + propertyName);
      sw.print("throw new ValidationException");
      sw.println("(value.getClass() +\" is not a valid type for \"+ propertyName);");

      // }
      sw.outdent();
      sw.println("}");
    }
  }

  private void writeValidatePropertyMethod(SourceWriter sw,
      PropertyDescriptor p, boolean useField) throws UnableToCompleteException {
    Class<?> elementClass = p.getElementClass();
    JType elementType = beanHelper.getElementType(p, useField);

    // private final <T> void validateProperty_{get}<p>(
    sw.print("private final <T> void ");
    if (useField) {
      sw.print(validateMethodFieldName(p));
    } else {
      sw.print(validateMethodGetterName(p));
    }
    sw.println("(");
    sw.indent();
    sw.indent();

    // final GwtValidationContext<T> context,
    sw.println("final GwtValidationContext<T> context,");

    // final Set<ConstraintViolation<T>> violations,
    sw.println("final Set<ConstraintViolation<T>> violations,");

    // final BeanType object, <Type> value,
    sw.println(beanHelper.getTypeCanonicalName() + " object,");

    // Class<?>... groups) {
    sw.print("final ");
    sw.print(elementType.getParameterizedQualifiedSourceName());
    sw.println(" value,");
    sw.println("Class<?>... groups) {");
    sw.outdent();

    // context = context.append("myProperty");
    sw.print("final GwtValidationContext<T> myContext = context.append(\"");
    sw.print(p.getPropertyName());
    sw.println("\");");

    // TODO(nchalko) move this out of here to the Validate method
    if (p.isCascaded() && hasValid(p, useField)) {

      // if(value != null) {
      sw.println("if(value != null) {");
      sw.indent();

      if (isIterableOrMap(elementClass)) {
        if (hasValid(p, useField)) {
          JClassType associationType = beanHelper.getAssociationType(p,
              useField);
          createBeanHelper(associationType);
          if (Map.class.isAssignableFrom(elementClass)) {
            writeValidateMap(sw, p);
          } else {
            writeValidateIterable(sw, p);
          }
        }
      } else {
        createBeanHelper(elementClass);

        // if (!context.alreadyValidated(value)) {
        sw.println(" if (!context.alreadyValidated(value)) {");
        sw.indent();

        // violations.addAll(myContext.getValidator().validate(context, value,
        // groups));
        sw.print("violations.addAll(");
        sw.println("myContext.getValidator().validate(myContext, value, groups));");

        // }
        sw.outdent();
        sw.println("}");
      }

      // }
      sw.outdent();
      sw.println("}");
    }

    // It is possible for an annotation with the exact same values to be set on
    // both the field and the getter.
    // Keep track of the ones we have used to make sure we don't duplicate.
    // It doesn't matter which one we use because they have exactly the same
    // values.
    Set<Object> includedAnnotations = Sets.newHashSet();
    int count = 0;
    for (ConstraintDescriptor<?> constraint : p.getConstraintDescriptors()) {
      Object annotation = constraint.getAnnotation();
      if (!includedAnnotations.contains(annotation)
          && hasMatchingAnnotation(p, useField, constraint)) {
        includedAnnotations.add(annotation);
        String constraintDescriptorVar = constraintDescriptorVar(
            p.getPropertyName(), count);

        writeValidateConstraint(sw, p, elementClass, constraint,
            constraintDescriptorVar);
      }
      count++;
    }
    sw.outdent();
    sw.println("}");
  }

  private void writeValidateValue(SourceWriter sw)
      throws UnableToCompleteException {
    // public <T> Set<ConstraintViolation<T>> validate(
    sw.println("public <T> Set<ConstraintViolation<T>> validateValue(");

    // GwtValidationContext<T> context, Class<Author> beanType,
    // String propertyName, Object value, Class<?>... groups) {
    sw.indent();
    sw.indent();
    sw.println("GwtValidationContext<T> context,");
    sw.println("Class<" + beanHelper.getTypeCanonicalName() + "> beanType,");
    sw.println("String propertyName,");
    sw.println("Object value,");
    sw.println("Class<?>... groups) {");
    sw.outdent();

    // try {
    sw.println("try {");
    sw.indent();

    writeNewViolations(sw);

    for (PropertyDescriptor property :
        beanHelper.getBeanDescriptor().getConstrainedProperties()) {
      // if (propertyName.equals(myPropety)) {
      sw.print("if (propertyName.equals(\"");
      sw.print(property.getPropertyName());
      sw.println("\")) {");
      sw.indent();

      if (!isIterableOrMap(property.getElementClass())) {
        writeValidatePropertyCall(sw, property, true);
      }

      // validate all super classes and interfaces
      writeValidateInheritance(sw, beanHelper.getClazz(),
          Stage.VALUE, property);

      // }
      sw.outdent();
      sw.print("} else ");
    }

    writeIfPropertyNameNotFound(sw);

    // return violations;
    sw.println("return violations;");

    writeCatchUnexpectedException(
        sw,
        "\"Error validating \" + propertyName + \" of "
            + beanHelper.getTypeCanonicalName() + "\"");

    sw.outdent();
    sw.println("}");
  }

  private void writeValidatorCall(SourceWriter sw, Class<?> type, Stage stage,
      PropertyDescriptor p) throws UnableToCompleteException {
    if (BeanHelper.isClassConstrained(type) && !isIterableOrMap(type)) {
      BeanHelper helper = createBeanHelper(type);
      beansToValidate.add(helper);
      switch (stage) {
        case OBJECT:
          // voilations.addAll(myValidator.validate(context,object,groups));
          sw.print("violations.addAll(");
          sw.print(helper.getValidatorInstanceName());
          sw.println(".validate(context, object, groups));");
          break;
        case PROPERTY:
          if (isPropertyConstrained(helper, p)) {
            // voilations.addAll(myValidator.validateProperty(context,object
            // ,propertyName, groups));
            sw.print("violations.addAll(");
            sw.print(helper.getValidatorInstanceName());
            sw.print(".validateProperty(context, object,");
            sw.println(" propertyName, groups));");
          }
          break;
        case VALUE:
          if (isPropertyConstrained(helper, p)) {
            // voilations.addAll(myValidator.validateProperty(context,beanType
            // ,propertyName, value, groups));
            sw.print("violations.addAll(");
            sw.print(helper.getValidatorInstanceName());
            sw.print(".validateValue(context, ");
            // TODO(nchalko) this seems like an unneeded param
            sw.print(helper.getTypeCanonicalName());
            sw.println(".class, propertyName, value, groups));");
          }
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  private void writeWrappers(SourceWriter sw) {
    sw.println("// Write the wrappers after we know which are needed");
    for (JField field : fieldsToWrap) {
      writeFieldWrapperMethod(sw, field);
      sw.println();
    }

    for (JMethod method : gettersToWrap) {
      writeGetterWrapperMethod(sw, method);
      sw.println();
    }
  }
}
