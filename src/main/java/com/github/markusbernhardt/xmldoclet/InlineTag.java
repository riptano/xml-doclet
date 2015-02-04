/*
 * This file is part of the Weborganic XMLDoclet library.
 *
 * For licensing information please see the file license.txt included in the release.
 * A copy of this licence can also be found at
 *   http://www.opensource.org/licenses/artistic-license-2.0.php
 */
package com.github.markusbernhardt.xmldoclet;

import com.sun.javadoc.Doc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.ExecutableMemberDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.ThrowsTag;
import java.util.StringTokenizer;
import com.github.markusbernhardt.xmldoclet.xjc.Link;
import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import com.sun.tools.doclets.internal.toolkit.util.ImplementedMethods;

/**
 * A collection of taglets to support the standard javadoc inline tags.
 *
 * <p>These will replace text within comments using XHTML for links.
 *
 * @author Christophe Lauret
 * @author Johan Johansson
 * @version 24 May 2013
 */
public enum InlineTag implements Taglet {

  /**
   * Equivalent to "@literal" but wrapping the content in {@literal <code>}.
   *
   * <p>Displays text in code font without interpreting the text as HTML markup or nested javadoc tags.
   *
   * <p>If you want the same functionality without the code font, use the "@literal" tag.
   *
   * <p>Same as: {@code <code><![CDATA["+tag.text()+"]]></code>}
   */
  CODE("code") {

    @Override
    public String getOutput(Parser parser, Tag tag) {
      return "<code><![CDATA["+tag.text()+"]]></code>";
    }

  },

  /**
   * Represents the relative path to the generated document's (destination) root directory from any generated page.
   *
   * <p>It is useful when you want to include a file that you want to reference from all generated pages.
   *
   * <p>This tag is valid in all doc comments.
   *
   * <p>Always "".
   *
   * @see <a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#docRoot">@docRoot tag</a>
   */
  DOCROOT("docRoot") {

    @Override
    public String getOutput(Parser parser, Tag tag) {
      return parser.getDocRoot();
    }

  },

  /**
   * Inherits (copies) documentation from the "nearest" inheritable class or implementable interface into the current
   * doc comment at this tag's location.
   *
   * This allows you to write more general comments higher up the inheritance tree, and to write around the copied
   * text.
   *
   * This tag is valid only in these places in a doc comment:
   * <ul>
   *   <li>In the main description block of a method. In this case, the main description is copied from a class
   *   or interface up the hierarchy.</li>
   *   <li>In the text arguments of the @return, @param and @throws tags of a method. In this case, the tag text is
   *   copied from the corresponding tag up the hierarchy.</li>
   * </ul>
   *
   * @see <a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#inheritDoc">@inheritDoc tag</a>
   */
  INHERITDOC("inheritDoc") {

    @Override
    public String getOutput(Parser parser, Tag tag) {
      if (! (tag.holder() instanceof MethodDoc)) {
          return "";
      }

      MethodDoc overridenMethod = retrieveOverridenMethod((MethodDoc) tag.holder());

      if (overridenMethod == null) {
        return "";
      }

      return parser.parseComment(overridenMethod);
    }

    @Override
    public String getOutput(Parser parser, Tag tag, Tag parent) {
      if (! (tag.holder() instanceof MethodDoc)) {
          return "";
      }

      Tag overridenTag = retrieveOverridenTag((MethodDoc) tag.holder(), parent);

      if (overridenTag == null) {
        return "";
      }

      return parser.parseComment(overridenTag);
    }

    private MethodDoc retrieveOverridenMethod(MethodDoc doc) {
      MethodDoc overridenMethod;

      overridenMethod = doc.overriddenMethod();

      if (overridenMethod != null && overridenMethod.inlineTags().length > 0) {
        return overridenMethod;
      }

      MethodDoc[] implementedMethods = (new ImplementedMethods(doc, null)).build(false);

      for (int i = 0; i < implementedMethods.length; i++) {
        overridenMethod = implementedMethods[i];

        if (overridenMethod.inlineTags().length > 0) {
          return overridenMethod;
        }
      }

      return null;
    }

    private Tag retrieveOverridenTag(MethodDoc doc, Tag tag) {
      Tag[] overridenTags;
      MethodDoc overridenMethod;

      overridenMethod = doc.overriddenMethod();

      if (overridenMethod != null) {
        overridenTags = overridenMethod.tags(tag.kind());

        if (overridenTags.length > 0) {
          if (tag instanceof ThrowsTag) {
            for (int i = 0; i < overridenTags.length; i++) {
              if (((ThrowsTag) tag).exceptionName() == ((ThrowsTag) overridenTags[i]).exceptionName()) {
                return overridenTags[i];
              }
            }
          } else if (tag instanceof ParamTag) {
            for (int i = 0; i < overridenTags.length; i++) {
              if (((ParamTag) tag).parameterName() == ((ParamTag) overridenTags[i]).parameterName()) {
                return overridenTags[i];
              }
            }
          } else {
            return overridenTags[0];
          }
        }
      }

      MethodDoc[] implementedMethods = (new ImplementedMethods(doc, null)).build(false);

      for (int i = 0; i < implementedMethods.length; i++) {
        overridenTags = implementedMethods[i].tags(tag.kind());

        if (overridenTags.length > 0) {
          if (tag instanceof ThrowsTag) {
            for (int j = 0; j < overridenTags.length; j++) {
              if (((ThrowsTag) tag).exceptionName() == ((ThrowsTag) overridenTags[j]).exceptionName()) {
                return overridenTags[j];
              }
            }
          } else if (tag instanceof ParamTag) {
            for (int j = 0; j < overridenTags.length; j++) {
              if (((ParamTag) tag).parameterName() == ((ParamTag) overridenTags[j]).parameterName()) {
                return overridenTags[j];
              }
            }
          } else {
            return overridenTags[0];
          }
        }
      }

      return null;
    }
  },

  /**
   * Inserts an in-line link with visible text label that points to the documentation for the specified package,
   * class or member name of a referenced class.
   *
   * This tag is valid in all doc comments: overview, package, class, interface, constructor, method and field,
   * including the text portion of any tag.
   *
   * This syntax this tag is:
   * <pre>package.class#member label</pre>
   *
   * <p>If you need to use "}" inside the label, use the HTML entity notation <code>&amp;#125;</code>.
   *
   * @see <a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#link">@link tag</a>
   */
  LINK("link"){

    @Override
    public String getOutput(Parser parser, Tag tag) {
      if (! (tag instanceof SeeTag)) {
          return "";
      }

      return serializeLink(parser.parseLink((SeeTag) tag));
    }

    private String serializeLink(Link linkNode) {
      StringWriter writer = new StringWriter();

      try {
        JAXBContext context  = JAXBContext.newInstance(Link.class);
        JAXBElement<Link> ln = new JAXBElement<Link>(new QName("link"), Link.class, linkNode);
        Marshaller m = context.createMarshaller();
        m.setProperty("jaxb.fragment", true);
        m.setProperty("jaxb.formatted.output", false);
        m.marshal(ln, writer);
      } catch (JAXBException e) {
        return "";
      }

      return "<code>" + writer.toString() + "</code>";
    }

  },

  /**
   * Identical to "link", except the link's label is displayed in plain text than code font.
   *
   * Useful when the label is plain text.
   *
   * @see <a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#linkplain">@linkplain tag</a>
   */
  LINKPLAIN("linkplain") {

    @Override
    public String getOutput(Parser parser, Tag tag) {
      if (! (tag instanceof SeeTag)) {
          return "";
      }

      return serializeLink(parser.parseLink((SeeTag) tag));
    }

    private String serializeLink(Link linkNode) {
      StringWriter writer = new StringWriter();

      try {
        JAXBContext context  = JAXBContext.newInstance(Link.class);
        JAXBElement<Link> ln = new JAXBElement<Link>(new QName("link"), Link.class, linkNode);
        Marshaller m = context.createMarshaller();
        m.setProperty("jaxb.fragment", true);
        m.setProperty("jaxb.formatted.output", false);
        m.marshal(ln, writer);
      } catch (JAXBException e) {
        return "";
      }

      return writer.toString();
    }

  },

  /**
   * Displays text without interpreting the text as HTML markup or nested javadoc tags.
   *
   * <p>Literal tags are simply wrapped in CDATA sections.
   *
   * @see <a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#literal">@literal tag</a>
   */
  LITERAL("literal") {

    @Override
    public String getOutput(Parser parser, Tag tag) {
      return "<![CDATA["+tag.text()+"]]>";
    }

  },

  /**
   * When {@value} is used (without any argument) in the doc comment of a static field,
   * it displays the value of that constant.
   *
   * <p>When used with argument package.class#field in any doc comment, it displays the value
   * of the specified constant.
   *
   * @see <a href="http://docs.oracle.com/javase/7/docs/technotes/tools/windows/javadoc.html#value">@value tag</a>
   */
  VALUE("value"){

    @Override
    public String getOutput(Parser parser, Tag tag) {
      return "";
      // return toValueString(tag);
    }

  };

  // enum class methods ===============================================================================================

  /**
   * The name of the tag
   */
  private final String _name;

  /**
   * Creates a new tag.
   *
   * @param name   The name of the tag.
   */
  InlineTag(String name) {
    this._name = "@" + name;
  }

  @Override
  public String getName() {
    return this._name;
  }


  @Override
  public String getOutput(Parser parser, Tag tag, Tag parent) {
    return getOutput(parser, tag);
  }

  // Utility methods for links
  // ----------------------------------------------------------------------------------------------
  //
  // /**
  //  * @param tag The tag to analyse
  //  * @return the package.class#member component of the tag.
  //  */
  // private static String getLinkSpec(Tag tag) {
  //   String text = tag.text();
  //   return text.replaceAll("^([^#]*#)?([^\\s]+\\([^)]+\\)|([^\\s]+)).*", "$1$2");
  // }
  //
  // private static String getLinkLabel(Tag tag) {
  //   return tag.text().replace(getLinkSpec(tag), "").trim();
  // }
  //
  // /**
  //  * @param tag The tag to analyse
  //  * @return the package component of the tag.
  //  */
  // private static String getLinkPackage(Tag tag) {
  //   if (tag instanceof SeeTag) {
  //     SeeTag seeTag = (SeeTag)tag;
  //
  //     if (seeTag.referencedPackage() != null) {
  //       return seeTag.referencedPackage().name();
  //     }
  //
  //     if (seeTag.referencedClass() != null && seeTag.referencedClass().containingPackage() != null) {
  //       return seeTag.referencedClass().containingPackage().name();
  //     }
  //
  //     return "";
  //   }
  //
  //   String spec = getLinkSpec(tag);
  //   int dot = spec.lastIndexOf('.');
  //   if (dot >= 0) {
  //     // Package was included in reference
  //     return spec.substring(0, dot);
  //   } else {
  //     // Get package from doc
  //     Doc doc = tag.holder();
  //
  //     if (doc instanceof ProgramElementDoc) {
  //       PackageDoc parentPackage = ((ProgramElementDoc)doc).containingPackage();
  //
  //       if (parentPackage != null && parentPackage.name().length() > 0) {
  //         return parentPackage.name();
  //       }
  //     }
  //
  //     spec = doc.toString();
  //
  //     if (doc.isClass() || doc.isMethod() || doc.isConstructor() || doc.isAnnotationType() || doc.isEnum()) {
  //       dot = spec.lastIndexOf('.');
  //     }
  //     return dot >= 0? spec.substring(0, dot) : spec;
  //   }
  // }
  //
  // /**
  //  * @param tag The tag to analyse
  //  * @return the class name component of the tag.
  //  */
  // private static String getClassName(Tag tag) {
  //   if (tag instanceof SeeTag) {
  //     SeeTag seeTag = (SeeTag)tag;
  //
  //     if (seeTag.referencedClass() != null) {
  //       return seeTag.referencedClass().name();
  //     }
  //
  //     return "";
  //   }
  //
  //   String name = getLinkSpec(tag);
  //
  //   // remove package
  //   int dot = name.lastIndexOf('.');
  //   if (dot >= 0) { name = name.substring(dot+1); }
  //   // remove member
  //   int hash = name.indexOf('#');
  //   if (hash >= 0) { name = name.substring(0, hash); }
  //   if (name.length() == 0) {
  //     Doc doc = tag.holder();
  //     name = doc.toString();
  //     if (doc.isClass() || doc.isMethod() || doc.isConstructor() || doc.isAnnotationType() || doc.isEnum()) {
  //       dot = name.lastIndexOf('.');
  //       return dot >= 0? name.substring(dot+1) : name;
  //     }
  //   }
  //   return name;
  // }
  //
  // /**
  //  * @param tag The tag to analyse
  //  * @return the member component of the tag.
  //  */
  // private static String getLinkMember(Tag tag) {
  //   if (tag instanceof SeeTag) {
  //     String name = ((SeeTag)tag).referencedMemberName();
  //
  //     if (name != null) {
  //       return name;
  //     }
  //
  //     return "";
  //   }
  //
  //   String spec = getLinkSpec(tag);
  //   int hash = spec.indexOf('#');
  //   return hash >= 0? spec.substring(hash+1) : null;
  // }
  //
  // /**
  //  * @param tag The tag to analyse
  //  * @return the label component of the tag.
  //  */
  // private static String getLabel(Tag tag) {
  //   if (tag instanceof SeeTag) {
  //     String label = ((SeeTag)tag).label();
  //
  //     if (label != null && label.length() > 0) {
  //       return label;
  //     }
  //
  //     return "";
  //   }
  //
  //   String text = tag.text();
  //   int space = text.indexOf(' ');
  //   String label = (space > 0)? text.substring(space+1) : text;
  //
  //   return label;
  // }
  //
  // /**
  //  * Returns the HTML link from the specified tag
  //  *
  //  * @param tag the tag to process.
  //  * @param css the css class.
  //  * @return the corresponding HTML
  //  */
  // public static String toLinkString(Tag tag, String css) {
  //   // extract spec and label
  //   String text = tag.text();
  //
  //   String spec  = getLinkSpec(tag);
  //   String label = getLinkLabel(tag);
  //   String p = getLinkPackage(tag);
  //   String c = getClassName(tag);
  //   String m = getLinkMember(tag);
  //   String qm = m;
  //
  //   // analyse spec
  //   String type = "unknown";
  //
  //   if (tag instanceof SeeTag) {
  //     SeeTag seeTag = (SeeTag)tag;
  //     type = seeTag.referencedMember() != null ? "member" : seeTag.referencedClass() != null ? "class" : seeTag.referencedPackage() != null ? "package" : "unknown";
  //
  //     if (seeTag.referencedMember() != null && seeTag.referencedMember() instanceof ExecutableMemberDoc) {
  //       ExecutableMemberDoc memberDoc = (ExecutableMemberDoc)seeTag.referencedMember();
  //       qm = memberDoc.name() + memberDoc.signature();
  //       spec = spec.replace(m, qm);
  //     }
  //   }
  //
  //   label = getLabel(tag);
  //
  //   if (c.equals(p)) {
  //     c = "";
  //   }
  //
  //   if (m != null && m.length() > 0) {
  //     m = m.replaceAll(",([^\\s])", ", $1");
  //   }
  //
  //   if (label == null || label.length() < 1) {
  //     if (m != null && m.length() > 0) {
  //       label = m;
  //     } else if (c != null && c.length() > 0) {
  //       label = c;
  //     } else if (p != null && p.length() > 0) {
  //       label = p;
  //     } else {
  //       label = spec;
  //     }
  //   }
  //
  //   // generate HTML link
  //   return toLinkString(spec, label, css, type, p, c, qm);
  // }
  //
  // public static String toLinkString(String url, String label, String cssClass, String type, String packageName, String className, String memberName) {
  //   StringBuilder html = new StringBuilder();
  //
  //   html.append("<a href=\"").append(XMLNode.encodeAttribute(url)).append("\" title=\"").append(XMLNode.encodeAttribute(label)).append('"');
  //   html.append(" class=\"").append(XMLNode.encodeAttribute(cssClass)).append('"');
  //   html.append(" data-type=\"").append(XMLNode.encodeAttribute(type)).append('"');
  //   html.append(" data-package=\"").append(XMLNode.encodeAttribute(packageName)).append('"');
  //
  //   if (className != null && className.length() > 0) {
  //     html.append(" data-class=\"").append(XMLNode.encodeAttribute(className)).append('"');
  //   }
  //
  //   if (memberName != null && memberName.length() > 0) {
  //     html.append(" data-member=\"").append(XMLNode.encodeAttribute(memberName)).append('"');
  //   }
  //
  //   html.append('>').append(XMLNode.encodeElement(label)).append("</a>");
  //   return html.toString();
  // }
  //
  // /**
  //  * Returns the constant value string for a value tag.
  //  *
  //  * @param tag the value tag to process.
  //  * @return the corresponding HTML
  //  */
  // public static String toValueString(Tag tag) {
  //   String text = tag.text();
  //
  //   if (text == null || text.length() == 0) {
  //     // extract constant value of this field
  //     Doc holder = tag.holder();
  //
  //     if (holder != null && holder instanceof FieldDoc) {
  //       return ((FieldDoc)holder).constantValueExpression();
  //     }
  //   }
  //
  //   Doc member = getMemberFromString(text, "field", tag);
  //
  //   if (member != null && member instanceof FieldDoc) {
  //     FieldDoc field = (FieldDoc)member;
  //     String value = field.constantValueExpression();
  //     return toLinkString(text, value, "link", "member", field.containingPackage().name(), field.containingClass().name(), field.name());
  //   }
  //
  //   return "";
  // }
  //
  // /**
  //  * Parses a reference to a qualified or partially qualified type name and returns the Doc for it.
  //  * Does not support unqualified nested class types.
  //  * E.g. #myField, SomeClass#myField, some.package.SomeClass#myField, some.package.SomeClass.NestedClass#myField
  //  *
  //  * @param  name type name
  //  * @param memberType field, method, etc
  //  * @param tag the containing tag
  //  * @return      [description]
  //  */
  // public static Doc getMemberFromString(String name, String memberType, Tag tag) {
  //   StringTokenizer st = new StringTokenizer(name, "#");
  //   String memberName = null;
  //   ClassDoc cd = null;
  //   if (st.countTokens() == 1) {
  //       //Case 2:  @value in same class.
  //       Doc holder = tag.holder();
  //       if (holder instanceof MemberDoc) {
  //           cd = ((MemberDoc) holder).containingClass();
  //       } else if (holder instanceof ClassDoc) {
  //           cd = (ClassDoc) holder;
  //       }
  //       memberName = st.nextToken();
  //   } else {
  //       //Case 3: @value in different class.
  //
  //       // might be fully qualified or not (package or no package)
  //       String className = st.nextToken();
  //
  //       // check for fq name else add local package
  //       if (className.indexOf('.') == -1) {
  //         // NOTE: doesn't work with unqualified nested classes (duh)
  //         Doc holder = tag.holder();
  //
  //         if (holder instanceof ProgramElementDoc) {
  //           className = ((ProgramElementDoc)holder).containingPackage().name() + "." + className;
  //         }
  //       }
  //
  //       cd = XMLDoclet.root.classNamed(className);
  //       memberName = st.nextToken();
  //   }
  //
  //   if (memberType.equals("field")) {
  //     FieldDoc[] fields = cd.fields();
  //     for (int i = 0; i < fields.length; i++) {
  //         if (fields[i].name().equals(memberName)) {
  //             return fields[i];
  //         }
  //     }
  //   }
  //
  //   return null;
  // }
}