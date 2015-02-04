package com.github.markusbernhardt.xmldoclet;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.markusbernhardt.xmldoclet.xjc.Annotation;
import com.github.markusbernhardt.xmldoclet.xjc.AnnotationArgument;
import com.github.markusbernhardt.xmldoclet.xjc.AnnotationElement;
import com.github.markusbernhardt.xmldoclet.xjc.AnnotationInstance;
import com.github.markusbernhardt.xmldoclet.xjc.Class;
import com.github.markusbernhardt.xmldoclet.xjc.Constructor;
import com.github.markusbernhardt.xmldoclet.xjc.Enum;
import com.github.markusbernhardt.xmldoclet.xjc.EnumValue;
import com.github.markusbernhardt.xmldoclet.xjc.Field;
import com.github.markusbernhardt.xmldoclet.xjc.Interface;
import com.github.markusbernhardt.xmldoclet.xjc.Method;
import com.github.markusbernhardt.xmldoclet.xjc.Param;
import com.github.markusbernhardt.xmldoclet.xjc.ObjectFactory;
import com.github.markusbernhardt.xmldoclet.xjc.Package;
import com.github.markusbernhardt.xmldoclet.xjc.Root;
import com.github.markusbernhardt.xmldoclet.xjc.TagInfo;
import com.github.markusbernhardt.xmldoclet.xjc.TypeInfo;
import com.github.markusbernhardt.xmldoclet.xjc.Generic;
import com.github.markusbernhardt.xmldoclet.xjc.Wildcard;
import com.github.markusbernhardt.xmldoclet.xjc.Link;
import com.github.markusbernhardt.xmldoclet.xjc.Return;
import com.github.markusbernhardt.xmldoclet.xjc.Throws;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.AnnotationTypeElementDoc;
import com.sun.javadoc.AnnotationValue;
import com.sun.javadoc.Doc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.ThrowsTag;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import com.sun.javadoc.WildcardType;

public class Parser {

  private final static Logger log = LoggerFactory.getLogger(Parser.class);

  protected Map<String, Package> packages = new TreeMap<String, Package>();

  private ObjectFactory objectFactory = new ObjectFactory();

  private String docRoot;

  /**
   * The taglets loaded by this doclet.
   */
  private Map<String, Taglet> taglets = new HashMap<String, Taglet>();

  /**
   * Creates new options.
   */
  public Parser(String docRoot) {
    this.docRoot = docRoot;
    // Load the standard taglets
    for (InlineTag t : InlineTag.values()) {
      taglets.put(t.getName(), t);
    }
  }

  public String getDocRoot() {
    return docRoot;
  }

  /**
   * The entry point into parsing the javadoc.
   * 
   * @param rootDoc
   *            The RootDoc intstance obtained via the doclet API
   * @return The root node, containing everything parsed from javadoc doclet
   */
  public Root parseRootDoc(RootDoc rootDoc) {
    Root rootNode = objectFactory.createRoot();

    for (ClassDoc classDoc : rootDoc.classes()) {
      PackageDoc packageDoc = classDoc.containingPackage();

      Package packageNode = packages.get(packageDoc.name());
      if (packageNode == null) {
        packageNode = parsePackage(packageDoc);
        packages.put(packageDoc.name(), packageNode);
        rootNode.getPackage().add(packageNode);
      }

      if (classDoc.isAnnotationType()) {
        packageNode.getAnnotation().add(parseAnnotationTypeDoc(classDoc));
      } else if (classDoc.isEnum()) {
        packageNode.getEnum().add(parseEnum(classDoc));
      } else if (classDoc.isInterface()) {
        packageNode.getInterface().add(parseInterface(classDoc));
      } else {
        packageNode.getClazz().add(parseClass(classDoc));
      }
    }

    return rootNode;
  }

  /**
   * Transforms comments on the Doc object into XML.
   *
   * @param doc The Doc object.
   */
  public String parseComment(Doc holder) {
    StringBuilder comment = new StringBuilder();

    // Analyse each token and produce comment node
    for (Tag t : holder.inlineTags()) {
      Taglet taglet = taglets.get(t.name());
      if (taglet != null) comment.append(taglet.getOutput(this, t));
      else comment.append(t.text());
    }

    return comment.toString();
  }

  /**
   * Transforms comments on the Doc object into XML.
   *
   * @param tag The Doc object.
   */
  public String parseComment(Tag tag) {
    StringBuilder comment = new StringBuilder();

    // Analyse each token and produce comment node
    for (Tag t : tag.inlineTags()) {
      Taglet taglet = taglets.get(t.name());
      if (taglet != null) comment.append(taglet.getOutput(this, t));
      else comment.append(t.text());
    }

    return comment.toString();
  }

  public Link parseLink(SeeTag tag) {
    Link seeNode = objectFactory.createLink();

    if (tag.referencedMember() != null) {
      MemberDoc member = tag.referencedMember();
      seeNode.setText(member.containingClass().qualifiedName() + "#" + tag.referencedMemberName());
      seeNode.setHref(member.containingClass().qualifiedName() + "#" + tag.referencedMemberName());
    } else if (tag.referencedClass() != null) {
      seeNode.setText(tag.referencedClass().qualifiedName());
      seeNode.setHref(tag.referencedClass().qualifiedName());
    } else if (tag.referencedPackage() != null) {
      seeNode.setText(tag.referencedPackage().name());
      seeNode.setHref(tag.referencedPackage().name());
    } else {
      seeNode.setText(tag.text());
      seeNode.setHref(tag.text());
    }

    if (tag.label() != null && !tag.label().isEmpty()) {
      seeNode.setText(tag.label());
    }

    return seeNode;
  }

  protected Package parsePackage(PackageDoc packageDoc) {
    Package packageNode = objectFactory.createPackage();
    packageNode.setName(packageDoc.name());
    packageNode.setComment(parseComment(packageDoc));

    Tag[] tags;
    SeeTag[] seeTags;

    tags = packageDoc.tags("@deprecated");
    if (tags.length > 0) {
      packageNode.setDeprecated(parseComment(tags[0]));
    }

    tags = packageDoc.tags("@since");
    if (tags.length > 0) {
      packageNode.setSince(tags[0].text());
    }

    tags = packageDoc.tags("@version");
    if (tags.length > 0) {
      packageNode.setVersion(tags[0].text());
    }

    seeTags = packageDoc.seeTags();

    for (int i = 0; i < seeTags.length; i++) {
      packageNode.getLink().add(parseLink(seeTags[i]));
    }

    return packageNode;
  }

  /**
   * Parse an annotation.
   * 
   * @param annotationTypeDoc
   *            A AnnotationTypeDoc instance
   * @return the annotation node
   */
  protected Annotation parseAnnotationTypeDoc(ClassDoc classDoc) {
    Annotation annotationNode = objectFactory.createAnnotation();
    annotationNode.setName(classDoc.name());
    annotationNode.setFull(classDoc.qualifiedName());
    annotationNode.setComment(parseComment(classDoc));
    annotationNode.setScope(parseScope(classDoc));

    Tag[] tags;
    SeeTag[] seeTags;

    tags = classDoc.tags("@deprecated");
    if (tags.length > 0) {
      annotationNode.setDeprecated(parseComment(tags[0]));
    }

    tags = classDoc.tags("@since");
    if (tags.length > 0) {
      annotationNode.setSince(tags[0].text());
    }

    tags = classDoc.tags("@version");
    if (tags.length > 0) {
      annotationNode.setVersion(tags[0].text());
    }

    tags = classDoc.tags("@author");
    for (int i = 0; i < tags.length; i++) {
      annotationNode.getAuthor().add(tags[i].text());
    }

    seeTags = classDoc.seeTags();

    for (int i = 0; i < seeTags.length; i++) {
      annotationNode.getLink().add(parseLink(seeTags[i]));
    }

    for (AnnotationTypeElementDoc annotationTypeElementDoc : ((AnnotationTypeDoc) classDoc).elements()) {
      annotationNode.getElement().add(parseAnnotationTypeElementDoc(annotationTypeElementDoc));
    }

    return annotationNode;
  }

  /**
   * Parse the elements of an annotation
   * 
   * @param element
   *            A AnnotationTypeElementDoc instance
   * @return the annotation element node
   */
  protected AnnotationElement parseAnnotationTypeElementDoc(AnnotationTypeElementDoc annotationTypeElementDoc) {
    AnnotationElement annotationElementNode = objectFactory.createAnnotationElement();
    annotationElementNode.setName(annotationTypeElementDoc.name());
    annotationElementNode.setFull(annotationTypeElementDoc.qualifiedName());
    annotationElementNode.setComment(parseComment(annotationTypeElementDoc));

    AnnotationValue value = annotationTypeElementDoc.defaultValue();
    if (value != null) {
      annotationElementNode.setDefault(value.toString());
    }

    Tag[] tags;
    SeeTag[] seeTags;

    tags = annotationTypeElementDoc.tags("@deprecated");
    if (tags.length > 0) {
      annotationElementNode.setDeprecated(parseComment(tags[0]));
    }

    tags = annotationTypeElementDoc.tags("@since");
    if (tags.length > 0) {
      annotationElementNode.setSince(tags[0].text());
    }

    tags = annotationTypeElementDoc.tags("@version");
    if (tags.length > 0) {
      annotationElementNode.setVersion(tags[0].text());
    }

    Return returnNode = objectFactory.createReturn();

    tags = annotationTypeElementDoc.tags("@return");
    if (tags.length > 0) {
      returnNode.setComment(parseComment(tags[0]));
    }

    returnNode.setType(parseTypeInfo(annotationTypeElementDoc.returnType()));

    annotationElementNode.setReturn(returnNode);

    seeTags = annotationTypeElementDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      annotationElementNode.getLink().add(parseLink(seeTags[i]));
    }

    return annotationElementNode;
  }

  protected Enum parseEnum(ClassDoc classDoc) {
    Enum enumNode = objectFactory.createEnum();
    enumNode.setName(classDoc.name());
    enumNode.setFull(classDoc.qualifiedName());
    enumNode.setComment(parseComment(classDoc));
    enumNode.setScope(parseScope(classDoc));

    Tag[] tags;
    SeeTag[] seeTags;

    tags = classDoc.tags("@deprecated");
    if (tags.length > 0) {
      enumNode.setDeprecated(parseComment(tags[0]));
    }

    tags = classDoc.tags("@since");
    if (tags.length > 0) {
      enumNode.setSince(tags[0].text());
    }

    tags = classDoc.tags("@version");
    if (tags.length > 0) {
      enumNode.setVersion(tags[0].text());
    }

    tags = classDoc.tags("@author");
    for (int i = 0; i < tags.length; i++) {
      enumNode.getAuthor().add(tags[i].text());
    }

    Type superClassType = classDoc.superclassType();
    if (superClassType != null) {
      enumNode.setClazz(parseTypeInfo(superClassType));
    }

    for (Type interfaceType : classDoc.interfaceTypes()) {
      enumNode.getInterface().add(parseTypeInfo(interfaceType));
    }

    seeTags = classDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      enumNode.getLink().add(parseLink(seeTags[i]));
    }

    for (FieldDoc field : classDoc.enumConstants()) {
      enumNode.getValue().add(parseEnumValue(field));
    }

    return enumNode;
  }

  /**
   * Parses an enum type definition
   * 
   * @param fieldDoc
   * @return
   */
  protected EnumValue parseEnumValue(FieldDoc fieldDoc) {
    EnumValue enumValue = objectFactory.createEnumValue();
    enumValue.setName(fieldDoc.name());
    enumValue.setComment(parseComment(fieldDoc));

    Tag[] tags;
    SeeTag[] seeTags;

    tags = fieldDoc.tags("@deprecated");
    if (tags.length > 0) {
      enumValue.setDeprecated(parseComment(tags[0]));
    }

    tags = fieldDoc.tags("@since");
    if (tags.length > 0) {
      enumValue.setSince(tags[0].text());
    }

    tags = fieldDoc.tags("@version");
    if (tags.length > 0) {
      enumValue.setVersion(tags[0].text());
    }

    seeTags = fieldDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      enumValue.getLink().add(parseLink(seeTags[i]));
    }

    return enumValue;
  }

  protected Interface parseInterface(ClassDoc classDoc) {

    Interface interfaceNode = objectFactory.createInterface();
    interfaceNode.setName(classDoc.name());
    interfaceNode.setFull(classDoc.qualifiedName());
    interfaceNode.setComment(parseComment(classDoc));
    interfaceNode.setScope(parseScope(classDoc));

    for (TypeVariable typeVariable : classDoc.typeParameters()) {
      interfaceNode.getGeneric().add(parseGeneric(typeVariable));
    }

    for (Type interfaceType : classDoc.interfaceTypes()) {
      interfaceNode.getInterface().add(parseTypeInfo(interfaceType));
    }

    for (MethodDoc method : classDoc.methods()) {
      interfaceNode.getMethod().add(parseMethod(method));
    }

    Tag[] tags;
    SeeTag[] seeTags;

    tags = classDoc.tags("@deprecated");
    if (tags.length > 0) {
      interfaceNode.setDeprecated(parseComment(tags[0]));
    }

    tags = classDoc.tags("@since");
    if (tags.length > 0) {
      interfaceNode.setSince(tags[0].text());
    }

    tags = classDoc.tags("@version");
    if (tags.length > 0) {
      interfaceNode.setVersion(tags[0].text());
    }

    seeTags = classDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      interfaceNode.getLink().add(parseLink(seeTags[i]));
    }

    return interfaceNode;
  }

  protected Class parseClass(ClassDoc classDoc) {

    Class classNode = objectFactory.createClass();
    classNode.setName(classDoc.name());
    classNode.setFull(classDoc.qualifiedName());
    classNode.setComment(parseComment(classDoc));
    classNode.setAbstract(classDoc.isAbstract());
    classNode.setError(classDoc.isError());
    classNode.setException(classDoc.isException());
    classNode.setExternalizable(classDoc.isExternalizable());
    classNode.setSerializable(classDoc.isSerializable());
    classNode.setScope(parseScope(classDoc));

    for (TypeVariable typeVariable : classDoc.typeParameters()) {
      classNode.getGeneric().add(parseGeneric(typeVariable));
    }

    Type superClassType = classDoc.superclassType();
    if (superClassType != null) {
      classNode.setClazz(parseTypeInfo(superClassType));
    }

    for (Type interfaceType : classDoc.interfaceTypes()) {
      classNode.getInterface().add(parseTypeInfo(interfaceType));
    }

    for (MethodDoc method : classDoc.methods()) {
      classNode.getMethod().add(parseMethod(method));
    }

    for (ConstructorDoc constructor : classDoc.constructors()) {
      classNode.getConstructor().add(parseConstructor(constructor));
    }

    for (FieldDoc field : classDoc.fields()) {
      classNode.getField().add(parseField(field));
    }

    Tag[] tags;
    SeeTag[] seeTags;

    tags = classDoc.tags("@deprecated");
    if (tags.length > 0) {
      classNode.setDeprecated(parseComment(tags[0]));
    }

    tags = classDoc.tags("@since");
    if (tags.length > 0) {
      classNode.setSince(tags[0].text());
    }

    tags = classDoc.tags("@version");
    if (tags.length > 0) {
      classNode.setVersion(tags[0].text());
    }

    tags = classDoc.tags("@author");
    for (int i = 0; i < tags.length; i++) {
      classNode.getAuthor().add(tags[i].text());
    }

    seeTags = classDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      classNode.getLink().add(parseLink(seeTags[i]));
    }

    return classNode;
  }

  protected Constructor parseConstructor(ConstructorDoc constructorDoc) {
    Constructor constructorNode = objectFactory.createConstructor();

    constructorNode.setName(constructorDoc.name());
    constructorNode.setFull(constructorDoc.qualifiedName());
    constructorNode.setComment(parseComment(constructorDoc));
    constructorNode.setScope(parseScope(constructorDoc));
    constructorNode.setFinal(constructorDoc.isFinal());
    constructorNode.setNative(constructorDoc.isNative());
    constructorNode.setStatic(constructorDoc.isStatic());
    constructorNode.setSynchronized(constructorDoc.isSynchronized());
    constructorNode.setVarArgs(constructorDoc.isVarArgs());

    Map<String, String> paramDescriptions = new HashMap<String, String>();
    ParamTag[] paramTags = constructorDoc.paramTags();

    for (int i = 0; i < paramTags.length; i++) {
      paramDescriptions.put(paramTags[i].parameterName(), parseComment((Tag) paramTags[i]));
    }

    for (Parameter parameter : constructorDoc.parameters()) {
      Param paramNode = parseParam(parameter);
      paramNode.setComment(paramDescriptions.get(parameter.name()));
      constructorNode.getParam().add(paramNode);
    }

    List<ThrowsTag> throwsTags = new ArrayList<ThrowsTag>(Arrays.asList(constructorDoc.throwsTags()));

    for (Type exceptionType : constructorDoc.thrownExceptionTypes()) {
      Throws throwsNode = objectFactory.createThrows();
      throwsNode.setType(parseTypeInfo(exceptionType));

      for (int i = 0; i < throwsTags.size(); i++) {
        ThrowsTag throwsTag = throwsTags.get(i);

        if (throwsTag.exceptionType() == exceptionType) {
          throwsNode.setComment(parseComment((Tag) throwsTag));
          throwsTags.remove(i);
          break;
        }
      }

      constructorNode.getThrows().add(throwsNode);
    }

    for (int i = 0; i < throwsTags.size(); i++) {
      ThrowsTag throwsTag = throwsTags.get(i);
      Throws throwsNode = objectFactory.createThrows();

      throwsNode.setType(parseTypeInfo(throwsTag.exceptionType()));
      throwsNode.setComment(parseComment((Tag) throwsTag));

      constructorNode.getThrows().add(throwsNode);
    }

    Tag[] tags;
    SeeTag[] seeTags;

    tags = constructorDoc.tags("@deprecated");
    if (tags.length > 0) {
      constructorNode.setDeprecated(parseComment(tags[0]));
    }

    tags = constructorDoc.tags("@since");
    if (tags.length > 0) {
      constructorNode.setSince(tags[0].text());
    }

    tags = constructorDoc.tags("@version");
    if (tags.length > 0) {
      constructorNode.setVersion(tags[0].text());
    }

    seeTags = constructorDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      constructorNode.getLink().add(parseLink(seeTags[i]));
    }

    return constructorNode;
  }

  protected Method parseMethod(MethodDoc methodDoc) {
    Method methodNode = objectFactory.createMethod();

    methodNode.setName(methodDoc.name());
    methodNode.setFull(methodDoc.qualifiedName());
    methodNode.setComment(parseComment(methodDoc));
    methodNode.setScope(parseScope(methodDoc));
    methodNode.setAbstract(methodDoc.isAbstract());
    methodNode.setFinal(methodDoc.isFinal());
    methodNode.setNative(methodDoc.isNative());
    methodNode.setStatic(methodDoc.isStatic());
    methodNode.setSynchronized(methodDoc.isSynchronized());
    methodNode.setVarArgs(methodDoc.isVarArgs());

    Map<String, String> paramDescriptions = new HashMap<String, String>();
    ParamTag[] paramTags = methodDoc.paramTags();

    for (int i = 0; i < paramTags.length; i++) {
      paramDescriptions.put(paramTags[i].parameterName(), parseComment((Tag) paramTags[i]));
    }

    for (Parameter parameter : methodDoc.parameters()) {
      Param paramNode = parseParam(parameter);
      paramNode.setComment(paramDescriptions.get(parameter.name()));
      methodNode.getParam().add(paramNode);
    }

    List<ThrowsTag> throwsTags = new ArrayList<ThrowsTag>(Arrays.asList(methodDoc.throwsTags()));

    for (Type exceptionType : methodDoc.thrownExceptionTypes()) {
      Throws throwsNode = objectFactory.createThrows();
      throwsNode.setType(parseTypeInfo(exceptionType));

      for (int i = 0; i < throwsTags.size(); i++) {
        ThrowsTag throwsTag = throwsTags.get(i);

        if (throwsTag.exceptionType() == exceptionType) {
          throwsNode.setComment(parseComment((Tag) throwsTag));
          throwsTags.remove(i);
          break;
        }
      }

      methodNode.getThrows().add(throwsNode);
    }

    for (int i = 0; i < throwsTags.size(); i++) {
      ThrowsTag throwsTag = throwsTags.get(i);
      Throws throwsNode = objectFactory.createThrows();

      throwsNode.setType(parseTypeInfo(throwsTag.exceptionType()));
      throwsNode.setComment(parseComment((Tag) throwsTag));

      methodNode.getThrows().add(throwsNode);
    }

    Tag[] tags;
    SeeTag[] seeTags;

    Return returnNode = objectFactory.createReturn();

    tags = methodDoc.tags("@return");
    if (tags.length > 0) {
      returnNode.setComment(parseComment(tags[0]));
    }

    returnNode.setType(parseTypeInfo(methodDoc.returnType()));

    methodNode.setReturn(returnNode);

    tags = methodDoc.tags("@deprecated");
    if (tags.length > 0) {
      methodNode.setDeprecated(parseComment(tags[0]));
    }

    tags = methodDoc.tags("@since");
    if (tags.length > 0) {
      methodNode.setSince(tags[0].text());
    }

    tags = methodDoc.tags("@version");
    if (tags.length > 0) {
      methodNode.setVersion(tags[0].text());
    }

    seeTags = methodDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      methodNode.getLink().add(parseLink(seeTags[i]));
    }

    return methodNode;
  }

  protected Param parseParam(Parameter parameter) {
    Param paramNode = objectFactory.createParam();
    paramNode.setName(parameter.name());
    paramNode.setType(parseTypeInfo(parameter.type()));

    return paramNode;
  }

  protected Field parseField(FieldDoc fieldDoc) {
    Field fieldNode = objectFactory.createField();
    fieldNode.setName(fieldDoc.name());
    fieldNode.setQualified(fieldDoc.qualifiedName());
    fieldNode.setComment(parseComment(fieldDoc));
    fieldNode.setScope(parseScope(fieldDoc));
    fieldNode.setFinal(fieldDoc.isFinal());
    fieldNode.setStatic(fieldDoc.isStatic());
    fieldNode.setVolatile(fieldDoc.isVolatile());
    fieldNode.setTransient(fieldDoc.isTransient());
    fieldNode.setDefault(fieldDoc.constantValueExpression());

    Tag[] tags;
    SeeTag[] seeTags;

    Return returnNode = objectFactory.createReturn();

    tags = fieldDoc.tags("@return");
    if (tags.length > 0) {
      returnNode.setComment(parseComment(tags[0]));
    }

    returnNode.setType(parseTypeInfo(fieldDoc.type()));

    fieldNode.setReturn(returnNode);

    tags = fieldDoc.tags("@deprecated");
    if (tags.length > 0) {
      fieldNode.setDeprecated(parseComment(tags[0]));
    }

    tags = fieldDoc.tags("@since");
    if (tags.length > 0) {
      fieldNode.setSince(tags[0].text());
    }

    tags = fieldDoc.tags("@version");
    if (tags.length > 0) {
      fieldNode.setVersion(tags[0].text());
    }

    seeTags = fieldDoc.seeTags();
    for (int i = 0; i < seeTags.length; i++) {
      fieldNode.getLink().add(parseLink(seeTags[i]));
    }

    return fieldNode;
  }

  protected TypeInfo parseTypeInfo(Type type) {
    TypeInfo typeInfoNode = objectFactory.createTypeInfo();
    typeInfoNode.setFull(type.qualifiedTypeName());
    String dimension = type.dimension();
    if (dimension.length() > 0) {
      typeInfoNode.setDimension(dimension);
    }

    WildcardType wildcard = type.asWildcardType();
    if (wildcard != null) {
      typeInfoNode.setWildcard(parseWildcard(wildcard));
    }

    ParameterizedType parameterized = type.asParameterizedType();
    if (parameterized != null) {
      for (Type typeArgument : parameterized.typeArguments()) {
        typeInfoNode.getGeneric().add(parseTypeInfo(typeArgument));
      }
    }

    return typeInfoNode;
  }

  protected Wildcard parseWildcard(WildcardType wildcard) {
    Wildcard wildcardNode = objectFactory.createWildcard();

    for (Type extendType : wildcard.extendsBounds()) {
      wildcardNode.getExtendsBound().add(parseTypeInfo(extendType));
    }

    for (Type superType : wildcard.superBounds()) {
      wildcardNode.getSuperBound().add(parseTypeInfo(superType));
    }

    return wildcardNode;
  }

  /**
   * Parse type variables for generics
   * 
   * @param typeVariable
   * @return
   */
  protected Generic parseGeneric(TypeVariable typeVariable) {
    Generic genericNode = objectFactory.createGeneric();
    genericNode.setName(typeVariable.typeName());

    for (Type bound : typeVariable.bounds()) {
      genericNode.getBound().add(bound.qualifiedTypeName());
    }

    return genericNode;
  }

  /**
   * Returns string representation of scope
   * 
   * @param doc
   * @return
   */
  protected String parseScope(ProgramElementDoc doc) {
    if (doc.isPrivate()) {
      return "private";
    } else if (doc.isProtected()) {
      return "protected";
    } else if (doc.isPublic()) {
      return "public";
    }
    return "";
  }
}
