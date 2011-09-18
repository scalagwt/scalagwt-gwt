package com.google.gwt.dev.javac;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.jjs.impl.jribble.JribbleProtos.DeclaredType;

public class JribbleParser {

  public static DeclaredType parse(TreeLogger logger, String typeName, String source) {
    DeclaredType.Builder b = DeclaredType.newBuilder();
    try {
      com.google.gwt.dev.protobuf.TextFormat.merge(source, b);
      return b.build();
    } catch (Exception e) {
      throw new InternalCompilerException(String.format(
          "Failed to parse %1s, parsing failed with a message:\n%2s",
          typeName,
          e.getMessage()), e);
    }
  }

}
