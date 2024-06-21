/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.datastore;

import com.dremio.common.utils.ProtobufUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.io.IOException;

/**
 * a Serializer implementation for protostuff generated classes
 *
 * @param <T> a protostuff generated class
 */
public class ProtobufSerializer<T extends Message> extends Serializer<T, byte[]> {
  private final Class<T> clazz;
  private final Parser<T> parser;

  public ProtobufSerializer(Class<T> clazz, Parser<T> parser) {
    this.clazz = clazz;
    this.parser = parser;
  }

  @Override
  public byte[] convert(T t) {
    return t.toByteArray();
  }

  @Override
  public T revert(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    try {
      return parser.parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public String toJson(T v) {
    try {
      return ProtobufUtils.toJSONString(v);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public T fromJson(String v) throws IOException {
    return ProtobufUtils.fromJSONString(clazz, v);
  }
}
