package com.google.gwt.dev.javac;

import com.google.gwt.core.ext.TreeLogger;
import com.google.jribble.DefParser;
import com.google.jribble.DefParserForJava;
import com.google.jribble.ast.DeclaredType;

import java.io.StringReader;

import scala.Either;

public class JribbleParser {

  private static final DefParser parser = new DefParserForJava();

  public static DeclaredType parse(TreeLogger logger, String typeName, String source) {
    Either<DeclaredType, String> result = parser.parse(new StringReader(source), "not used");
    if (result.isRight()) {
      throw new RuntimeException(String.format(
        "Failed to parse %1s, parsing failed with a message:\n%2s",
        typeName,
        result.right().get()));
    } else {
      return result.left().get();
    }
  }

}
