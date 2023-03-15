package smallville7123.reflectui;

import static smallville7123.reflectui.TypeContext.indent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.vyarus.java.generics.resolver.context.MethodGenericsContext;
import smallville7123.reflectui.utils.Pair;

public class TypeContextMethod {
    TypeContext parent;
    MethodGenericsContext methodContext;

    List<Pair<String, TypeContext>> parameters;

    TypeContextMethod(TypeContext parent, MethodGenericsContext methodContext) {
        this.parent = parent;
        this.methodContext = methodContext;

        Parameter[] parameters1 = methodContext.currentMethod().getParameters();
        int parameterCount = parameters1.length;
        ArrayList<Pair<String, TypeContext>> params = new ArrayList<>(parameterCount);
        for (int i = 0; i < parameterCount; i++) {
            Pair<String, TypeContext> pair = new Pair<>(parameters1[i].getName(), new TypeContext(parent.context, parent.context.resolveType(methodContext.resolveParameterType(i)), false));
            params.add(pair);
        }
        parameters = Collections.unmodifiableList(params);
    }

    public Method getMethod() {
        return methodContext.currentMethod();
    }

    public TypeContext getReturnType() {
        return new TypeContext(parent.context, parent.context.resolveType(methodContext.resolveReturnType()), false);
    }
    public List<Pair<String, TypeContext>> getParameters() {
        return parameters;
    }

    public String javaString() {
        return TypeContext.javaString(this);
    }

    public void printDetailed() {
        System.out.println(toDetailedString());
    }

    public String toDetailedString() {
        return toDetailedString(0);
    }

    private String toDetailedString(int indent) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(byteArrayOutputStream);
        printStream.println();
        printStream.println(indent(indent) + "TypeContextMethod {");
        indent++;
        TypeContext returnType = getReturnType();
        printStream.println(indent(indent) + "method: " + javaString());
        printStream.println(indent(indent) + "method return type: " + getReturnType().toDetailedString(indent + 1));
        StringBuilder acc = new StringBuilder();
        for (Pair<String, TypeContext> typeContext : parameters) {
            acc.append(typeContext.second.toDetailedString(indent + 1));
        }
        printStream.println(indent(indent) + "parameters: " + parameters.size() + acc);
        indent--;
        printStream.print(indent(indent) + "}");
        printStream.flush();
        return byteArrayOutputStream.toString();
    }
}
