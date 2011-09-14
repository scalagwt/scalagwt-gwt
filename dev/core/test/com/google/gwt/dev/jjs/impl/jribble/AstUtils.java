package com.google.gwt.dev.jjs.impl.jribble;

import com.google.jribble.ast.Ref;
import com.google.jribble.ast.Package;

import scala.Option;
import scala.Some;

public class AstUtils {

  static Ref toRef(String name) {
    int i = name.lastIndexOf(".");
    if (i == -1) {
      Option<Package> none = Option.apply(null);
      return new Ref(none, name);
    } else {
      return new Ref(new Some<Package>(new Package(name.substring(0, i))), name.substring(i + 1));
    }
  }

}
