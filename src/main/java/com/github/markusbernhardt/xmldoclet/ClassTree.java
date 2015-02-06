package com.github.markusbernhardt.xmldoclet;

import com.sun.javadoc.RootDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Type;
import com.sun.javadoc.ParameterizedType;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Comparator;

public class ClassTree {

    /**
     * We want the list of types in alphabetical order.  However, types are not
     * comparable.  We need a comparator for now.
     */
    private static class TypeComparator implements Comparator {
        public int compare(Object type1, Object type2) {
            return ((Type) type1).qualifiedTypeName().toLowerCase().compareTo(
                ((Type) type2).qualifiedTypeName().toLowerCase());
        }
    }

    /**
     * List of baseclasses. Contains only java.lang.Object. Can be used to get
     * the mapped listing of sub-classes.
     */
    private List baseclasses = new ArrayList();

    /**
    * Mapping for each Class with their SubClasses
    */
    private Map subclasses = new HashMap();

    /**
     * List of base-interfaces. Contains list of all the interfaces who do not
     * have super-interfaces. Can be used to get the mapped listing of
     * sub-interfaces.
     */
    private List baseinterfaces = new ArrayList();

    /**
    * Mapping for each Interface with their SubInterfaces
    */
    private Map subinterfaces = new HashMap();

    private List baseEnums = new ArrayList();
    private Map subEnums = new HashMap();

    private List baseAnnotationTypes = new ArrayList();
    private Map subAnnotationTypes = new HashMap();

    /**
    * Mapping for each Interface with classes who implement it.
    */
    private Map implementingclasses = new HashMap();

    /**
     * Constructor. Build the Tree using the Root of this Javadoc run.
     *
     * @param root Root of the Document.
     */
    public ClassTree(RootDoc root) {
        buildTree(root.classes());
    }

    /**
     * Generate mapping for the sub-classes for every class in this run.
     * Return the sub-class list for java.lang.Object which will be having
     * sub-class listing for itself and also for each sub-class itself will
     * have their own sub-class lists.
     *
     * @param classes all the classes in this run.
     */
    private void buildTree(ClassDoc[] classes) {
        for (int i = 0; i < classes.length; i++) {
            if (classes[i].isEnum()) {
                processType(classes[i], baseEnums, subEnums);
            } else if (classes[i].isClass()) {
                processType(classes[i], baseclasses, subclasses);
            } else if (classes[i].isInterface()) {
                processInterface(classes[i]);
                List list  = (List)implementingclasses.get(classes[i]);
                if (list != null) {
                    Collections.sort(list);
                }
            } else if (classes[i].isAnnotationType()) {
                processType(classes[i], baseAnnotationTypes,
                    subAnnotationTypes);
            }
        }

        Collections.sort(baseinterfaces);
        for (Iterator it = subinterfaces.values().iterator(); it.hasNext(); ) {
            Collections.sort((List)it.next());
        }
        for (Iterator it = subclasses.values().iterator(); it.hasNext(); ) {
            Collections.sort((List)it.next());
        }
    }

    /**
     * For the class passed map it to it's own sub-class listing.
     * For the Class passed, get the super class,
     * if superclass is non null, (it is not "java.lang.Object")
     *    get the "value" from the hashmap for this key Class
     *    if entry not found create one and get that.
     *    add this Class as a sub class in the list
     *    Recurse till hits java.lang.Object Null SuperClass.
     *
     * @param cd class for which sub-class mapping to be generated.
     */
    private void processType(ClassDoc cd, List bases, Map subs) {
        ClassDoc superclass = getFirstVisibleSuperClassCD(cd);
        if (superclass != null) {
            if (!add(subs, superclass, cd)) {
                return;
            } else {
                processType(superclass, bases, subs);
            }
        } else {     // cd is java.lang.Object, add it once to the list
            if (!bases.contains(cd)) {
                bases.add(cd);
            }
        }
        List intfacs = getAllInterfaces(cd);
        for (Iterator iter = intfacs.iterator(); iter.hasNext();) {
            add(implementingclasses, ((Type) iter.next()).asClassDoc(), cd);
        }
    }

    private ClassDoc getFirstVisibleSuperClassCD(ClassDoc classDoc) {
        if (classDoc == null) {
            return null;
        }
        ClassDoc supClassDoc = classDoc.superclass();
        while (supClassDoc != null &&
                  (! (supClassDoc.isPublic() ||
                              supClassDoc.isIncluded())) ) {
            supClassDoc = supClassDoc.superclass();
        }
        if (classDoc.equals(supClassDoc)) {
            return null;
        }
        return supClassDoc;
    }

    public List getAllInterfaces(Type type) {
        Map results = new TreeMap();
        Type[] interfaceTypes = null;
        Type superType = null;
        if (type instanceof ParameterizedType) {
            interfaceTypes = ((ParameterizedType) type).interfaceTypes();
            superType = ((ParameterizedType) type).superclassType();
        } else if (type instanceof ClassDoc) {
            interfaceTypes = ((ClassDoc) type).interfaceTypes();
            superType = ((ClassDoc) type).superclassType();
        } else {
            interfaceTypes = type.asClassDoc().interfaceTypes();
            superType = type.asClassDoc().superclassType();
        }

        for (int i = 0; i < interfaceTypes.length; i++) {
            Type interfaceType = interfaceTypes[i];
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (! (interfaceClassDoc.isPublic() ||
                   interfaceClassDoc.isIncluded())) {
                continue;
            }
            results.put(interfaceClassDoc, interfaceType);
            List superInterfaces = getAllInterfaces(interfaceType);
            for (Iterator iter = superInterfaces.iterator(); iter.hasNext(); ) {
                Type t = (Type) iter.next();
                results.put(t.asClassDoc(), t);
            }
        }
        if (superType == null)
            return new ArrayList(results.values());
        //Try walking the tree.
        addAllInterfaceTypes(results,
            superType,
            superType instanceof ClassDoc ?
                ((ClassDoc) superType).interfaceTypes() :
                ((ParameterizedType) superType).interfaceTypes(),
            false);
        List resultsList = new ArrayList(results.values());
        Collections.sort(resultsList, new TypeComparator());
        return resultsList;
    }

    private void addAllInterfaceTypes(Map results, Type type,
            Type[] interfaceTypes, boolean raw) {
        for (int i = 0; i < interfaceTypes.length; i++) {
            Type interfaceType = interfaceTypes[i];
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (! (interfaceClassDoc.isPublic() ||
                interfaceClassDoc.isIncluded())) {
                continue;
            }
            if (raw)
                interfaceType = interfaceType.asClassDoc();
            results.put(interfaceClassDoc, interfaceType);
            List superInterfaces = getAllInterfaces(interfaceType);
            for (Iterator iter = superInterfaces.iterator(); iter.hasNext(); ) {
                Type superInterface = (Type) iter.next();
                results.put(superInterface.asClassDoc(), superInterface);
            }
        }
        if (type instanceof ParameterizedType)
            findAllInterfaceTypes(results, (ParameterizedType) type);
        else if (((ClassDoc) type).typeParameters().length == 0)
            findAllInterfaceTypes(results, (ClassDoc) type, raw);
        else
            findAllInterfaceTypes(results, (ClassDoc) type, true);
    }


    private void findAllInterfaceTypes(Map results, ClassDoc c, boolean raw) {
        Type superType = c.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                superType instanceof ClassDoc ?
                ((ClassDoc) superType).interfaceTypes() :
                ((ParameterizedType) superType).interfaceTypes(),
                raw);
    }

    private void findAllInterfaceTypes(Map results, ParameterizedType p) {
        Type superType = p.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                superType instanceof ClassDoc ?
                ((ClassDoc) superType).interfaceTypes() :
                ((ParameterizedType) superType).interfaceTypes(),
                false);
    }

    /**
     * For the interface passed get the interfaces which it extends, and then
     * put this interface in the sub-interface list of those interfaces. Do it
     * recursively. If a interface doesn't have super-interface just attach
     * that interface in the list of all the baseinterfaces.
     *
     * @param cd Interface under consideration.
     */
    private void processInterface(ClassDoc cd) {
        ClassDoc[] intfacs = cd.interfaces();
        if (intfacs.length > 0) {
            for (int i = 0; i < intfacs.length; i++) {
                if (!add(subinterfaces, intfacs[i], cd)) {
                    return;
                } else {
                    processInterface(intfacs[i]);   // Recurse
                }
            }
        } else {
            // we need to add all the interfaces who do not have
            // super-interfaces to baseinterfaces list to traverse them
            if (!baseinterfaces.contains(cd)) {
                baseinterfaces.add(cd);
            }
        }
    }

    /**
     * Adjust the Class Tree. Add the class interface  in to it's super-class'
     * or super-interface's sub-interface list.
     *
     * @param map the entire map.
     * @param superclass java.lang.Object or the super-interface.
     * @param cd sub-interface to be mapped.
     * @returns boolean true if class added, false if class already processed.
     */
    private boolean add(Map map, ClassDoc superclass, ClassDoc cd) {
        List list = (List)map.get(superclass);
        if (list == null) {
            list = new ArrayList();
            map.put(superclass, list);
        }
        if (list.contains(cd)) {
            return false;
        } else {
            list.add(cd);
        }
        return true;
    }

    /**
     * From the map return the list of sub-classes or sub-interfaces. If list
     * is null create a new one and return it.
     *
     * @param map The entire map.
     * @param cd class for which the sub-class list is requested.
     * @returns List Sub-Class list for the class passed.
     */
    private List get(Map map, ClassDoc cd) {
        List list = (List)map.get(cd);
        if (list == null) {
            return new ArrayList();
        }
        return list;
    }

    /**
     *  Return the sub-class list for the class passed.
     *
     * @param cd class whose sub-class list is required.
     */
    public List subclasses(ClassDoc cd) {
        return get(subclasses, cd);
    }

    /**
     *  Return the sub-interface list for the interface passed.
     *
     * @param cd interface whose sub-interface list is required.
     */
    public List subinterfaces(ClassDoc cd) {
        return get(subinterfaces, cd);
    }

    /**
     *  Return the list of classes which implement the interface passed.
     *
     * @param cd interface whose implementing-classes list is required.
     */
    public List implementingclasses(ClassDoc cd) {
        List result = get(implementingclasses, cd);
        List subinterfaces = allSubs(cd, false);

        //If class x implements a subinterface of cd, then it follows
        //that class x implements cd.
        Iterator implementingClassesIter, subInterfacesIter = subinterfaces.listIterator();
        ClassDoc c;
        while(subInterfacesIter.hasNext()){
            implementingClassesIter = implementingclasses((ClassDoc)
                    subInterfacesIter.next()).listIterator();
            while(implementingClassesIter.hasNext()){
                c = (ClassDoc)implementingClassesIter.next();
                if(! result.contains(c)){
                    result.add(c);
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     *  Return the sub-class/interface list for the class/interface passed.
     *
     * @param cd class/interface whose sub-class/interface list is required.
     * @param isEnum true if the subclasses should be forced to come from the
     * enum tree.
     */
    public List subs(ClassDoc cd, boolean isEnum) {
        if (isEnum) {
            return get(subEnums, cd);
        } else if (cd.isAnnotationType()) {
            return get(subAnnotationTypes, cd);
        } else if (cd.isInterface()) {
            return get(subinterfaces, cd);
        } else if (cd.isClass()) {
            return get(subclasses, cd);
        } else {
            return null;
        }

    }

    /**
     * Return a list of all direct or indirect, sub-classes and subinterfaces
     * of the ClassDoc argument.
     *
     * @param cd ClassDoc whose sub-classes or sub-interfaces are requested.
     * @param isEnum true if the subclasses should be forced to come from the
     * enum tree.
     */
    public List allSubs(ClassDoc cd, boolean isEnum) {
        List list = subs(cd, isEnum);
        for (int i = 0; i < list.size(); i++) {
            cd = (ClassDoc)list.get(i);
            List tlist = subs(cd, isEnum);
            for (int j = 0; j < tlist.size(); j++) {
                ClassDoc tcd = (ClassDoc)tlist.get(j);
                if (!list.contains(tcd)) {
                    list.add(tcd);
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    /**
     *  Return the base-classes list. This will have only one element namely
     *  thw classdoc for java.lang.Object, since this is the base class for all
     *  classes.
     */
    public List baseclasses() {
        return baseclasses;
    }

    /**
     *  Return the list of base interfaces. This is the list of interfaces
     *  which do not have super-interface.
     */
    public List baseinterfaces() {
        return baseinterfaces;
    }

    /**
     *  Return the list of base enums. This is the list of enums
     *  which do not have super-enums.
     */
    public List baseEnums() {
        return baseEnums;
    }

    /**
     *  Return the list of base annotation types. This is the list of
     *  annotation types which do not have super-annotation types.
     */
    public List baseAnnotationTypes() {
        return baseAnnotationTypes;
    }
}