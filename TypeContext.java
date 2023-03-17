package smallville7123.reflectui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import ru.vyarus.java.generics.resolver.GenericsResolver;
import ru.vyarus.java.generics.resolver.context.GenericsContext;
import ru.vyarus.java.generics.resolver.context.GenericsInfo;
import smallville7123.reflectui.utils.Pair;

public class TypeContext {
    GenericsContext context;
    Class<?> current_class;
    int rank = 0;
    boolean selfBound = false;

    List<TypeContext> genericParameters = new ArrayList<>();

    List<TypeContext> implementsContexts = new ArrayList<>();
    List<TypeContext> interfaces = new ArrayList<>();
    private final List<List<TypeContext>> lists;
    TypeContext superclass;
    private TypeContext parent;
    private boolean is_super = false;

    /**
     * use {@link TypeContext#isSuper()}, along with {@link TypeContext#getImplementsContexts()}, to determine what additional classes we can accept
     * <br>
     * <br>
     * used in wildcards
     * <p>
     * ? super EXTENDS_CLASS & IMPLEMENTS CLASS & IMPLEMENTS CLASS & ...
     * <p>
     * ? extends EXTENDS_CLASS & IMPLEMENTS CLASS & IMPLEMENTS CLASS & ...
     * <br>
     * <br>
     * see {@link TypeContext#getImplementsContexts()}
     * <br>
     * <br>
     * assuming Number, super == true means we accept Number and Object, but we DO NOT accept anything that extends from Number, such as Integer, and we DO NOT accept anything that extends from Object except fom Number
     * <br>
     * <br>
     * assuming Number, super == false means we accept Integer, Number, or anything that extends from Number, but we DO NOT accept Object
     * <br>
     * <br>
     * if Integer and super == true then we accept Integer, Number, and Object (Integer extends Number extends Object), but we DO NOT accept anything that extends from Integer (and since Integer is final, nothing can extend Integer), and we DO NOT accept anything that extends from Number except for Integer
     * <br>
     * <br>
     * if Integer and super == false then we accept Integer, but we DO NOT accept Number, and Object (and since Integer is final, nothing can extend Integer)
     */
    public boolean isSuper() {
        return is_super;
    }

    public int getRank() {
        return rank;
    }

    public boolean isSelfBound() {
        return selfBound;
    }

    public List<TypeContext> getGenericParameters() {
        return genericParameters;
    }

    public TypeContext getGenericParameters(int index) {
        return genericParameters.get(index);
    }

    public Class<?> currentClass() {
        return current_class;
    }

    class FOO <T extends ArrayList<String> & List<String>> {}

    FOO<ArrayList<String>> a;

    /**
     * use {@link TypeContext#getImplementsContexts()}, along with {@link TypeContext#isSuper()}, to determine what additional classes we can accept
     * <p>
     * used in wildcards
     * <p>
     * ? super EXTENDS_CLASS & IMPLEMENTS CLASS & IMPLEMENTS CLASS & ...
     * <p>
     * ? extends EXTENDS_CLASS & IMPLEMENTS CLASS & IMPLEMENTS CLASS & ...
     * <br>
     * <br>
     * see {@link TypeContext#isSuper()}
     * <br>
     * <br>
     * T super ArrayList<String> & List<String>
     * <p>
     * T accepts a class ArrayList or any class that ArrayList itself extends from
     * <p>
     * ArrayList already implements List
     * <br>
     * <br>
     * <br>
     * <br>
     * T super Number & List<String>
     * <p>
     * T DOES NOT accept Number, since Number itself does not implement List
     * <p>
     * T accepts any class that Number itself extends from, but these MUST be extended from as they themselves do not implement List
     * <br>
     * <br>
     * <br>
     * <br>
     * T extends ArrayList<String> & List<String>
     * <p>
     * T accepts a class ArrayList or any class that extends ArrayList
     * <p>
     * ArrayList already implements List
     * <br>
     * <br>
     * <br>
     * <br>
     * T extends Number & List<String>
     * <p>
     * T accepts any class that extends Number, since Number itself does not implement List
     * <p>
     * any class extending Number, given to T, must implement List
     * either directly, or from extending a class that does implement List
     */
    public List<TypeContext> getImplementsContexts() {
        return implementsContexts;
    }

    public TypeContext getSuperclass() {
        return superclass;
    }

    public List<TypeContext> getInterfaces() {
        return interfaces;
    }

    public TypeContext(Type type) {
        this(null, type);
    }

    TypeContext(GenericsContext context, Type type) {
        this(context, type, false);
    }

    TypeContext(GenericsContext context, Type type, boolean selfBound) {
        this(null, context, type, selfBound, false);
    }

    TypeContext(TypeContext parent, GenericsContext context, Type type, boolean selfBound, boolean is_super) {
        this.lists = Arrays.asList(genericParameters, implementsContexts, interfaces);
        this.parent = parent;
        while (parent != null) {
            if (parent.current_class == type) {
                this.selfBound = true;
                this.current_class = parent.current_class;
                this.context = parent.context;
                this.rank = parent.rank;
                this.implementsContexts = parent.implementsContexts;
                this.genericParameters = parent.genericParameters;
                this.interfaces = parent.interfaces;
                this.superclass = parent.superclass;
                return;
            }
            parent = parent.parent;
        }
        resolveContext(context, type, selfBound, is_super);
        if (!selfBound) {
            addGenerics();
        }
    }

    private void resolveContext(GenericsContext context, Type type, boolean selfBound, boolean is_super) {
        if (type == null) {
            throw new RuntimeException("given type is null");
        }
        Type resolve = resolve(type);
        if (resolve == null) {
            throw new RuntimeException("resulting type is null");
        }
        this.selfBound = selfBound;
        boolean valid = false;
        if (context != null) {
            if (resolve instanceof Class<?> || resolve instanceof ParameterizedType) {
                this.context = is_super ? context.type(context.resolveClass(resolve)) : context.inlyingType(resolve);
                this.current_class = resolve instanceof Class<?> && ((Class<?>) resolve).isPrimitive() ? (Class<?>) resolve : this.context.currentClass();
                valid = true;
            } else if (resolve instanceof WildcardType) {
                final WildcardType wildcard = (WildcardType) resolve;
                Type[] lowerBounds = wildcard.getLowerBounds();
                if (lowerBounds.length > 0) {
                    this.is_super = true;
                    // ? super
                    resolveContext(context, lowerBounds[0], selfBound, false);
                    valid = true;
                    if (lowerBounds.length > 1) {
                        for (int i = 1, lowerBoundsLength = lowerBounds.length; i < lowerBoundsLength; i++) {
                            implementsContexts.add(new TypeContext(context, context.inlyingType(lowerBounds[i]).currentClass(), false));
                        }
                    }
                } else {
                    // ? extends
                    // in java only one bound could be defined, but here could actually be repackaged TypeVariable
                    Type[] upperBounds = wildcard.getUpperBounds();
                    resolveContext(context, upperBounds[0], selfBound, false);
                    valid = true;
                    if (upperBounds.length > 1) {
                        for (int i = 1, upperBoundsLength = upperBounds.length; i < upperBoundsLength; i++) {
                            implementsContexts.add(new TypeContext(context, context.inlyingType(upperBounds[i]).currentClass(), false));
                        }
                    }
                }
            }
        } else if (resolve instanceof Class<?>) {
            Class<?> resolve1 = (Class<?>) resolve;
            this.context = GenericsResolver.resolve(resolve1);
            current_class = resolve1.isPrimitive() ? resolve1 : this.context.currentClass();
            valid = true;
        }
        if (!valid) {
            throw new RuntimeException("resulting type is not an instance of class: " + resolve + ", CLASS: " + resolve.getClass());
        }
        if (current_class == null) {
            throw new RuntimeException("resulting type has attempting to be resolved but resolved class is null: " + resolve + ", CLASS: " + resolve.getClass());
        }
        if (this.context == null) {
            throw new RuntimeException("resulting type has attempting to be resolved but context is null: " + resolve + ", CLASS: " + resolve.getClass());
        }
        Type genericSuperclass = current_class.getGenericSuperclass();
        if (genericSuperclass != null) {
            if (genericSuperclass instanceof Class<?> || genericSuperclass instanceof ParameterizedType) {
                if (genericSuperclass == Object.class) {
                    superclass = new TypeContext(null, genericSuperclass, false);
                } else {
                    Class<?> c;
                    if (genericSuperclass instanceof Class<?>) {
                        c = (Class<?>) genericSuperclass;
                    } else {
                        c = (Class<?>) ((ParameterizedType) genericSuperclass).getRawType();
                    }
                    c = this.context.resolveClass(c);
                    superclass = new TypeContext(this, this.context.type(c), c, false, true);
                }
            } else {
                throw new RuntimeException("generic superclass is not an instance of class: " + genericSuperclass + ", CLASS: " + genericSuperclass.getClass());
            }
        }
        for (Class<?> anInterface : current_class.getInterfaces()) {
            interfaces.add(new TypeContext(this, this.context, anInterface, false, true));
        }
    }

    void addGenerics() {
        List<Type> types = context.genericTypes();
        for (Type type_ : types) {
            if (type_ == context.currentClass()) {
                genericParameters.add(new TypeContext(this, null, context.currentClass(), true, false));
            } else {
                genericParameters.add(new TypeContext(this, context, type_, false, false));
            }
        }
    }

    public static String contextToString(GenericsContext context, int indent) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        printStream.println();
        printStream.println(indent(indent) + "GenericsContext {");
        indent++;
        printStream.println(indent(indent) + "current class: " + context.currentClass().toGenericString());
        printStream.println(indent(indent) + "is inlying: " + context.isInlying());
        printStream.println(indent(indent) + "owner class: " + context.ownerClass());
        printStream.println(indent(indent) + "owner generics map: " + context.ownerGenericsMap());
        printStream.println(indent(indent) + "visible generics map: " + context.visibleGenericsMap());
        printStream.println(indent(indent) + "generics: " + context.generics());
        printStream.println(indent(indent) + "generic types: " + context.genericTypes());
        printStream.println(indent(indent) + "generics map: " + context.genericsMap());
        printStream.println(indent(indent) + "generics scope: " + context.getGenericsScope());
        printStream.println(indent(indent) + "generics source: " + context.getGenericsSource());
        printStream.println(indent(indent) + "generics of: " + context.resolveGenericsOf(context.currentClass()));
        printStream.println(indent(indent) + "type generics: " + context.resolveTypeGenerics(context.currentClass()));
        printStream.println(indent(indent) + "generics info: " + genericsInfoToString(context.getGenericsInfo(), indent + 1));
        indent--;
        printStream.print(indent(indent) + "}");
        printStream.flush();
        return byteArrayOutputStream.toString();
    }

    private static String genericsInfoToString(GenericsInfo genericsInfo, int indent) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        printStream.println();
        printStream.println(indent(indent) + "GenericsInfo {");
        indent++;
        printStream.println(indent(indent) + "root class: " + genericsInfo.getRootClass());
        printStream.println(indent(indent) + "composing types: " + genericsInfo.getComposingTypes());
        printStream.println(indent(indent) + "types map: " + genericsInfo.getTypesMap());
        indent--;
        printStream.print(indent(indent) + "}");
        printStream.flush();
        return byteArrayOutputStream.toString();
    }

    private Type resolve(Type type) {
        while (type instanceof GenericArrayType) {
            rank++;
            type = ((GenericArrayType) type).getGenericComponentType();
        }
        if (type instanceof Class<?>) {
            Class<?> aClass = (Class<?>) type;
            while (aClass.isArray()) {
                rank++;
                aClass = aClass.getComponentType();
            }
            return aClass;
        } else {
            return type;
        }
    }

    public TypeContextField findField(String fieldName) throws NoSuchFieldException {
        return new TypeContextField(this, getFieldRecursive(fieldName));
    }

    public TypeContextField[] findFields(Predicate<Field> predicate) throws NoSuchFieldException {
        List<TypeContextField> list = new ArrayList<>();
        for (Field field : getFieldsRecursive(predicate)) {
            TypeContextField typeContextField = new TypeContextField(this, field);
            list.add(typeContextField);
        }
        return list.toArray(new TypeContextField[0]);
    }

    public TypeContextMethod[] findMethods(String methodName) throws NoSuchMethodException {
        List<TypeContextMethod> list = new ArrayList<>();
        for (Method method : getMethodsRecursive(methodName)) {
            TypeContextMethod typeContextField = new TypeContextMethod(this, context.inlyingType(method.getDeclaringClass()).method(method));
            list.add(typeContextField);
        }
        return list.toArray(new TypeContextMethod[0]);
    }

    public TypeContextMethod[] findMethods(Predicate<Method> predicate) throws NoSuchMethodException {
        List<TypeContextMethod> list = new ArrayList<>();
        for (Method method : getMethodsRecursive(predicate)) {
            TypeContextMethod typeContextMethod = new TypeContextMethod(this, context.inlyingType(method.getDeclaringClass()).method(method));
            list.add(typeContextMethod);
        }
        return list.toArray(new TypeContextMethod[0]);
    }

    private static <R extends Throwable> R replaceMessage(R throwable, String newMessage) {
        R r = null;
        try {
            //noinspection unchecked
            r = (R) throwable.getClass().getConstructor(String.class).newInstance(newMessage);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        r.setStackTrace(throwable.getStackTrace());
        Throwable cause = throwable.getCause();
        if (cause != null) {
            r.initCause(cause);
        }
        Throwable[] suppressed = throwable.getSuppressed();
        if (suppressed.length > 0) {
            for (Throwable value : suppressed) {
                r.addSuppressed(value);
            }
        }
        return r;
    }

    private Field getFieldRecursive(String fieldName) throws NoSuchFieldException {
        return getFieldRecursive(context.currentClass(), fieldName);
    }

    private Field[] getFieldsRecursive(Predicate<Field> predicate) throws NoSuchFieldException {
        return getFieldsRecursive(context.currentClass(), predicate);
    }

    private static Field getFieldRecursive(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return getFieldsRecursive(clazz, f -> f.getName().contentEquals(fieldName), new ArrayList<>()).get(0);
        } catch (NoSuchFieldException e) {
            throw replaceMessage(e, fieldName);
        }
    }

    private static Field[] getFieldsRecursive(Class<?> clazz, Predicate<Field> predicate) throws NoSuchFieldException {
        return getFieldsRecursive(clazz, predicate, new ArrayList<>()).toArray(new Field[0]);
    }

    private static ArrayList<Field> getFieldsRecursive(Class<?> clazz, Predicate<Field> predicate, ArrayList<Field> seenFields) throws NoSuchFieldException {
        // we must search top-down for the field name
        ArrayList<Field> info = new ArrayList<>();
        Class<?> sc = clazz;
        while (sc != null) {
            Field[] m = sc.getDeclaredFields();
            List<Field> m2 = new ArrayList<>();
            for (Field field2 : m) {
                if (!field2.isSynthetic() && predicate.test(field2)) {
                    m2.add(field2);
                }
            }

            for (Field field : m2) {
                boolean s = false;
                for (Field seenField : seenFields) {
                    if (field.getName().contentEquals(seenField.getName())) {
                        s = true;
                    }
                }
                if (!s) {
                    seenFields.add(field);
                    info.add(field);
                }
            }

            // interfaces cannot have fields
            // finally search superclass
            sc = sc.getSuperclass();
        }

        if (info.isEmpty()) {
            throw new NoSuchFieldException();
        }

        return info;
    }

    private Method[] getMethodsRecursive(String methodName) throws NoSuchMethodException {
        return getMethodsRecursive(context.currentClass(), methodName);
    }

    private Method[] getMethodsRecursive(Predicate<Method> predicate) throws NoSuchMethodException {
        return getMethodsRecursive(context.currentClass(), predicate);
    }

    private static Method[] getMethodsRecursive(Class<?> clazz, String methodName) throws NoSuchMethodException {
        try {
            return getMethodsRecursive(clazz, f -> f.getName().contentEquals(methodName), 0, new ArrayList<>()).toArray(new Method[0]);
        } catch (NoSuchMethodException e) {
            throw replaceMessage(e, methodName);
        }
    }

    private static Method[] getMethodsRecursive(Class<?> clazz, Predicate<Method> predicate) throws NoSuchMethodException {
        return getMethodsRecursive(clazz, predicate, 0, new ArrayList<>()).toArray(new Method[0]);
    }

    private static ArrayList<Method> getMethodsRecursive(Class<?> clazz, Predicate<Method> predicate, int depth, ArrayList<Method> seenMethods) throws NoSuchMethodException {
        // we must search top-down for the method name
        ArrayList<Method> info = new ArrayList<>();
        Class<?> sc = clazz;
        while (sc != null) {
            Method[] m = sc.getDeclaredMethods();
            List<Method> m2 = new ArrayList<>();
            for (Method method2 : m) {
                if (!method2.isBridge() && !method2.isSynthetic() && predicate.test(method2)) {
                    m2.add(method2);
                }
            }

            for (Method method : m2) {
                boolean seen = false;
                for (Method seenMethod : seenMethods) {
                    if (methodsEqual(method, seenMethod)) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    seenMethods.add(method);
                    info.add(method);
                }
            }
            // search superinterfaces
            for (Class<?> scInterface : sc.getInterfaces()) {
                try {
                    info.addAll(getMethodsRecursive(scInterface, predicate, depth + 1, seenMethods));
                } catch (NoSuchMethodException ignored) {
                }
            }
            // finally search superclass
            sc = sc.getSuperclass();
        }
        if (info.isEmpty()) {
            throw new NoSuchMethodException();
        }
        return info;
    }

    private static boolean methodsEqual(Method a, Method b) {
        if (!a.getName().contentEquals(b.getName())) {
            return false;
        }
        return Arrays.equals(a.getParameterTypes(), b.getParameterTypes());
    }

    public void printDetailed() {
        System.out.println(toDetailedString());
    }

    public void printDetailed(int indent) {
        System.out.println(toDetailedString(indent));
    }

    public String toDetailedString() {
        return toDetailedString(this, 0);
    }

    public String toDetailedString(int indent) {
        return toDetailedString(this, indent);
    }

    public static String indent(int indent) {
        String INDENT_STRING = "  ";
        switch (indent) {
            case 0:
                return "";
            case 1:
                return INDENT_STRING;
            default:
                StringBuilder acc = new StringBuilder();
                for (int i = 0; i < indent; i++) {
                    acc.append(INDENT_STRING);
                }
                return acc.toString();
        }
    }

    static String javaString(TypeContextField context) {
        StringBuilder ret = new StringBuilder();
        ret.append(javaString(context.getReturnType()));
        ret.append(" ");
        ret.append(context.field.getName());
        return ret.toString();
    }

    static String javaString(TypeContextMethod context) {
        StringBuilder ret = new StringBuilder();
        ret.append(javaString(context.getReturnType()));
        ret.append(" ");
        ret.append(context.getMethod().getName());
        ret.append("(");
        Iterator<Pair<String, TypeContext>> iterator = context.parameters.iterator();
        while (iterator.hasNext()) {
            Pair<String, TypeContext> next = iterator.next();
            ret.append(javaString(next.second));
            ret.append(" ");
            ret.append(next.first);
            if (iterator.hasNext()) {
                ret.append(", ");
            }
        }
        ret.append(")");
        return ret.toString();
    }

    static String javaString(TypeContext context) {
        StringBuilder ret = new StringBuilder();
        ret.append(context.current_class.toString());
        if (context.genericParameters.size() > 0) {
            ret.append("<");
            Iterator<TypeContext> iterator = context.genericParameters.iterator();
            while (iterator.hasNext()) {
                ret.append(javaString(iterator.next()));
                if (iterator.hasNext()) {
                    ret.append(", ");
                }
            }
            ret.append(">");
        }

        for (int i = 0; i < context.rank; i++) {
            ret.append("[]");
        }

        return ret.toString();
    }

    public String javaString() {
        return TypeContext.javaString(this);
    }

    public String currentClassAsString() {
        StringBuilder ret = new StringBuilder();

        ret.append(current_class.isPrimitive() ? current_class.toString() : context.toStringCurrentClass());

        for (int i = 0; i < rank; i++) {
            ret.append("[]");
        }

        return ret.toString();
    }

    public static String toDetailedString(TypeContext typeContext, int indent) {
        if (indent > 60) {
            return "<<<<OVERFLOW>>>>";
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        printStream.println();
        printStream.println(indent(indent) + "TypeContext {");
        indent++;

        printStream.println(indent(indent) + "type: " + javaString(typeContext));

        if (typeContext.rank > 0) {
            printStream.println(indent(indent) + "rank: " + typeContext.rank);
        }

        if (typeContext.selfBound) {
            printStream.println(indent(indent) + "self bound: " + true);
        } else {
            // if we are self bound, do not recurse anything since we will recurse infinitely
            StringBuilder acc = new StringBuilder();
            int size = typeContext.implementsContexts.size();
            if (size > 0) {
                for (TypeContext implementsContext : typeContext.implementsContexts) {
                    acc.append(implementsContext.toDetailedString(indent + 1));
                }
                printStream.println(indent(indent) + "implements: " + size + acc);
            }

            int size1 = typeContext.genericParameters.size();
            if (size1 > 0) {
                acc = new StringBuilder();
                for (TypeContext genericParameter : typeContext.genericParameters) {
                    acc.append(genericParameter.toDetailedString(indent + 1));
                }
                printStream.println(indent(indent) + "genericParameters: " + size1 + acc);
            }

            int size2 = typeContext.interfaces.size();
            if (size2 > 0) {
                acc = new StringBuilder();
                for (TypeContext i : typeContext.interfaces) {
                    acc.append(i.toDetailedString(indent + 1));
                }
                printStream.println(indent(indent) + "interfaces: " + size2 + acc);
            }

            if (typeContext.superclass != null) {
                printStream.println(indent(indent) + "superclass: " + typeContext.superclass.toDetailedString(indent + 1));
            }
        }
        indent--;
        printStream.print(indent(indent) + "}");
        printStream.flush();
        return byteArrayOutputStream.toString();
    }

    /**
     * returns true if EVERY CLASS matches any of the classes contained in c
     */
    public boolean containsClasses(Class<?> ... c) {
        return containsClasses(Arrays.stream(c).collect(Collectors.toList()));
    }

    /**
     * returns true if EVERY CLASS matches any of the classes contained in c and extra
     */
    public boolean containsClassesWithExtra(List<Class<?>> c, Class<?> ... extra) {
        return containsClasses(c, Arrays.stream(extra).collect(Collectors.toList()));
    }

    /**
     * returns true if EVERY CLASS matches any of the classes contained in c
     */
    public boolean containsClasses(List<Class<?>> ... c) {
        TypeContext sc = this;
        while (sc != null) {
            boolean b = false;
            for (List<Class<?>> classes : c) {
                for (Class<?> p : classes) {
                    if (p == sc.current_class) {
                        b = true;
                        break;
                    }
                }
            }
            if (!b) {
                return false;
            }

            if (sc.selfBound) {
                // if we are self bound, do not recurse anything since we will recurse infinitely
                return true;
            }

            for (List<TypeContext> list : sc.lists) {
                for (TypeContext typeContext : list) {
                    if (!typeContext.containsClasses(c)) {
                        return false;
                    }
                }
            }

            sc = sc.superclass;
            if (sc == null) {
                return true;
            }
        }
        return true;
    }

    /**
     * returns TypeContext if TARGET_CLASS matches this class, or any of its interfaces, superclasses, or implementing contexts (if originating from wildcard, see {@link TypeContext#getImplementsContexts()} and {@link TypeContext#isSuper()})
     */
    public TypeContext findSuperclassOrInterface(Class<?> TARGET_CLASS) {
        TypeContext sc = this;
        while (true) {
            if (sc.current_class == TARGET_CLASS) {
                return sc;
            }

            if (sc.selfBound) {
                // if we are self bound, do not recurse anything since we will recurse infinitely
                return null;
            }

            List<List<TypeContext>> listList = sc.lists;
            // generic parameters is at index 0, skip it
            for (int i = 1, listListSize = listList.size(); i < listListSize; i++) {
                List<TypeContext> list = listList.get(i);
                for (TypeContext typeContext : list) {
                    TypeContext superclassOrInterface = typeContext.findSuperclassOrInterface(TARGET_CLASS);
                    if (superclassOrInterface != null) {
                        return superclassOrInterface;
                    }
                }
            }

            sc = sc.superclass;
            if (sc == null) {
                return null;
            }
        }
    }
}
