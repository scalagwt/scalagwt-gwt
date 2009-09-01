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
package com.google.gwt.junit;

import com.google.gwt.i18n.client.Constants;
import com.google.gwt.i18n.client.Messages;

import junit.framework.TestCase;

/**
 * Tests of FakeMessagesMaker.
 */
public class FakeMessagesMakerTest extends TestCase {
  interface MyMessages extends Messages {
    @DefaultMessage("Isn''t this the fakiest?")
    @Description("A sample message to be tested.")
    String myMessage();

    @DefaultMessage("Isn''t this the fakiest? Pick one: {1} or {2}?")
    @Description("A sample message with parameters.")
    String myArgumentedMessage(@Example("yes") String yes,
        @Example("no") String no);
  }

  interface MyConstants extends Constants {
    @DefaultStringValue("This is a very simple message")
    String myFixedMessage();

    @DefaultStringValue("This message is so complicated, it requires a description")
    @Description("42")
    String messageWithDescription();
  }

  public void testSimpleWithMessages() {
    MyMessages messages = FakeMessagesMaker.create(MyMessages.class);
    assertEquals("myMessage", messages.myMessage());
  }

  public void testArgsWithMessages() {
    MyMessages messages = FakeMessagesMaker.create(MyMessages.class);
    assertEquals("myArgumentedMessage[oui, non]",
        messages.myArgumentedMessage("oui", "non"));
  }

  public void testSimpleWithConstants() {
    MyConstants constants = FakeMessagesMaker.create(MyConstants.class);
    assertEquals("myFixedMessage", constants.myFixedMessage());
  }

  public void testConstantWithDescription() {
    MyConstants constants = FakeMessagesMaker.create(MyConstants.class);
    assertEquals("messageWithDescription", constants.messageWithDescription());
  }

}
