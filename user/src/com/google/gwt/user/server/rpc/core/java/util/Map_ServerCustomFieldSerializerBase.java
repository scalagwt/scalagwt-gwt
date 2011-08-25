/*
 * Copyright 2011 Google Inc.
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
package com.google.gwt.user.server.rpc.core.java.util;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.core.java.util.Map_CustomFieldSerializerBase;
import com.google.gwt.user.server.rpc.impl.DequeMap;
import com.google.gwt.user.server.rpc.impl.SerializabilityUtil;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamReader;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Server-side Custom field serializer for {@link java.util.HashMap}.
 */
public final class Map_ServerCustomFieldSerializerBase {

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void deserialize(ServerSerializationStreamReader streamReader, Map instance,
      Class<?> instanceClass, DequeMap<Type, Type> resolvedTypes)
      throws SerializationException {
    Type[] actualTypes =
        SerializabilityUtil.findInstanceParameters(instanceClass, resolvedTypes);
    if (actualTypes == null || actualTypes.length < 2) {
      Map_CustomFieldSerializerBase.deserialize(streamReader, instance);
      return;
    }

    int size = streamReader.readInt();
    for (int i = 0; i < size; ++i) {
      Object key = streamReader.readObject(actualTypes[0], resolvedTypes);
      Object value = streamReader.readObject(actualTypes[1], resolvedTypes);

      instance.put(key, value);
    }
  }

}