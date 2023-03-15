package smallville7123.reflectui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import ru.vyarus.java.generics.resolver.GenericsResolver;
import ru.vyarus.java.generics.resolver.context.GenericsContext;

public class TypeContext {
    GenericsContext context;
    int rank = 0;
    boolean selfBound = false;

    List<TypeContext> genericParameters = new ArrayList<>();

    public int getRank() {
        return rank;
    }

    public boolean isSelfBound() {
        return selfBound;
    }

    public List<TypeContext> getGenericParameters() {
        return genericParameters;
    }

    public TypeContext(Type type) {
        Type t = resolve(type);
        if (t instanceof Class<?>) {
            context = GenericsResolver.resolve((Class<?>) t);
        } else {
            throw new RuntimeException("resulting type is not an instance of class: " + t);
        }
        addGenerics();
    }

    // if this gets called, then type == currentClass, and we know the type is self bound
    //
    // eg: class S <T extends S<T>> // parameter type == S.class
    TypeContext(Type type, boolean selfBound) {
        Type t = resolve(type);
        if (t instanceof Class<?>) {
            context = GenericsResolver.resolve((Class<?>) t);
            this.selfBound = true;
        } else {
            throw new RuntimeException("resulting type is not an instance of class: " + t);
        }
    }

    TypeContext(GenericsContext context, Type type) {
        this.context = context.inlyingType(resolve(type));
        addGenerics();
    }

    void addGenerics() {
        List<Type> types = context.genericTypes();
        for (Type type_ : types) {
            if (type_ == context.currentClass()) {
                genericParameters.add(new TypeContext(context.currentClass(), true));
            } else {
                genericParameters.add(new TypeContext(context, type_));
            }
        }
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

    public TypeContextField findField(String fieldName) {
        try {
            return new TypeContextField(this, getFieldRecursive(fieldName));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public TypeContextField[] findFields(Predicate<Field> predicate) {
        try {
            List<TypeContextField> list = new ArrayList<>();
            for (Field field : getFieldsRecursive(predicate)) {
                TypeContextField typeContextField = new TypeContextField(this, field);
                list.add(typeContextField);
            }
            return list.toArray(new TypeContextField[0]);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public TypeContextMethod[] findMethods(String methodName) {
        try {
            List<TypeContextMethod> list = new ArrayList<>();
            for (Method method : getMethodsRecursive(methodName)) {
                TypeContextMethod typeContextField = new TypeContextMethod(this, context.method(method));
                list.add(typeContextField);
            }
            return list.toArray(new TypeContextMethod[0]);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public TypeContextMethod[] findMethods(Predicate<Method> predicate) {
        try {
            List<TypeContextMethod> list = new ArrayList<>();
            for (Method method : getMethodsRecursive(predicate)) {
                TypeContextMethod typeContextField = new TypeContextMethod(this, context.method(method));
                list.add(typeContextField);
            }
            return list.toArray(new TypeContextMethod[0]);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
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

    public Field getFieldRecursive(String fieldName) throws NoSuchFieldException {
        return getFieldRecursive(context.currentClass(), fieldName);
    }

    public Field[] getFieldsRecursive(Predicate<Field> predicate) throws NoSuchFieldException {
        return getFieldsRecursive(context.currentClass(), predicate);
    }

    public static Field getFieldRecursive(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return getFieldsRecursive(clazz, f -> f.getName().contentEquals(fieldName), new ArrayList<>()).get(0);
        } catch (NoSuchFieldException e) {
            throw replaceMessage(e, fieldName);
        }
    }

    public static Field[] getFieldsRecursive(Class<?> clazz, Predicate<Field> predicate) throws NoSuchFieldException {
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

    public Method[] getMethodsRecursive(String methodName) throws NoSuchMethodException {
        return getMethodsRecursive(context.currentClass(), methodName);
    }

    public Method[] getMethodsRecursive(Predicate<Method> predicate) throws NoSuchMethodException {
        return getMethodsRecursive(context.currentClass(), predicate);
    }

    public static Method[] getMethodsRecursive(Class<?> clazz, String methodName) throws NoSuchMethodException {
        try {
            return getMethodsRecursive(clazz, f -> f.getName().contentEquals(methodName), 0, new ArrayList<>()).toArray(new Method[0]);
        } catch (NoSuchMethodException e) {
            throw replaceMessage(e, methodName);
        }
    }

    public static Method[] getMethodsRecursive(Class<?> clazz, Predicate<Method> predicate) throws NoSuchMethodException {
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

    public static String toDetailedString(TypeContext typeContext, int indent) {
        if (indent > 30) {
            return "<<<<OVERFLOW>>>>";
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        printStream.println();
        printStream.println(indent(indent) + "TypeContext {");
        indent++;

        printStream.println(indent(indent) + "type: " + typeContext.context.currentClass());

        if (typeContext.rank > 0) {
            printStream.println(indent(indent) + "rank: " + typeContext.rank);
        }

        if (typeContext.selfBound) {
            printStream.println(indent(indent) + "self bound: " + true);
        }

        StringBuilder acc = new StringBuilder();
        for (TypeContext genericParameter : typeContext.genericParameters) {
            acc.append(genericParameter.toDetailedString(indent + 1));
        }
        printStream.println(indent(indent) + "genericParameters: " + typeContext.genericParameters.size() + acc);

        indent--;
        printStream.print(indent(indent) + "}");
        printStream.flush();
        return byteArrayOutputStream.toString();
    }
}
