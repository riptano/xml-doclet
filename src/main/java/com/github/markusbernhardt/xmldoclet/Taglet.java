package com.github.markusbernhardt.xmldoclet;

import com.sun.javadoc.Tag;

public interface Taglet {
  public String getName();
  public String getOutput(Parser parser, Tag tag);
  public String getOutput(Parser parser, Tag tag, Tag parent);
}
