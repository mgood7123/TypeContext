package smallville7123.reflectui;

import static smallville7123.reflectui.TypeContext.indent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ru.vyarus.java.generics.resolver.context.MethodGenericsContext;

public class TypeContextMethod {
    TypeContext parent;
    MethodGenericsContext methodContext;

    List<TypeContext> parameters;

    TypeContextMethod(TypeContext parent, MethodGenericsContext methodContext) {
        this.parent = parent;
        this.methodContext = methodContext;

        int parameterCount = methodContext.currentMethod().getParameterCount();
        ArrayList<TypeContext> params = new ArrayList<>(parameterCount);
        for (int i = 0; i < parameterCount; i++) {
            params.add(new TypeContext(parent.context, parent.context.resolveType(methodContext.resolveParameterType(i))));
        }
        parameters = Collections.unmodifiableList(params);
    }

    public Method getMethod() {
        return methodContext.currentMethod();
    }

    public TypeContext getReturnType() {
        return new TypeContext(parent.context, parent.context.resolveType(methodContext.resolveReturnType()));
    }
    public List<TypeContext> getParameters() {
        return parameters;
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
        printStream.println(indent(indent) + "method: " + methodContext.toStringMethod());
        printStream.println(indent(indent) + "method return type: " + getReturnType().toDetailedString(indent + 1));
        StringBuilder acc = new StringBuilder();
        for (TypeContext typeContext : parameters) {
            acc.append(typeContext.toDetailedString(indent + 1));
        }
        printStream.println(indent(indent) + "genericParameters: " + parameters.size() + acc);
        indent--;
        printStream.print(indent(indent) + "}");
        printStream.flush();
        return byteArrayOutputStream.toString();
    }
}
