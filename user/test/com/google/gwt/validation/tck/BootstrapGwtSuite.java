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
package com.google.gwt.validation.tck;

import junit.framework.Test;

import org.hibernate.jsr303.tck.tests.bootstrap.ConfigurationGwtTest;
import org.hibernate.jsr303.tck.tests.bootstrap.CustomMessageInterpolatorGwtTest;
import org.hibernate.jsr303.tck.tests.bootstrap.ValidationProviderGwtTest;
import org.hibernate.jsr303.tck.tests.bootstrap.ValidationProviderResolverGwtTest;
import org.hibernate.jsr303.tck.tests.bootstrap.customprovider.BootstrapCustomProviderDefinedInServiceFileGwtTest;
import org.hibernate.jsr303.tck.tests.bootstrap.customprovider.BootstrapCustomProviderDefinedInValidationXmlGwtTest;
import org.hibernate.jsr303.tck.tests.bootstrap.customprovider.BootstrapUnknownCustomProviderGwtTest;
import org.hibernate.jsr303.tck.util.TckTestSuiteWrapper;

/**
 * Tck Tests for the {@code bootstrap} package.
 */
public class BootstrapGwtSuite {
  public static Test suite() {
    TckTestSuiteWrapper suite = new TckTestSuiteWrapper(
        "TCK for GWT Validation, constraints bootstrap package");
    suite.addTestSuite(ConfigurationGwtTest.class);
    suite.addTestSuite(CustomMessageInterpolatorGwtTest.class);
    suite.addTestSuite(ValidationProviderGwtTest.class);
    suite.addTestSuite(ValidationProviderResolverGwtTest.class);
    suite
        .addTestSuite(BootstrapCustomProviderDefinedInServiceFileGwtTest.class);
    suite
        .addTestSuite(BootstrapCustomProviderDefinedInValidationXmlGwtTest.class);
    suite.addTestSuite(BootstrapUnknownCustomProviderGwtTest.class);
    return suite;
  }
}
