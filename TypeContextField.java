package smallville7123.reflectui;

import static smallville7123.reflectui.TypeContext.indent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;

public class TypeContextField {
    TypeContext parent;
    Field field;

    TypeContextField(TypeContext parent, Field field) {
        this.parent = parent;
        this.field = field;
    }

    TypeContext getReturnType() {
        return new TypeContext(parent.context, parent.context.resolveFieldType(field));
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
        printStream.println(indent(indent) + "TypeContextField {");
        indent++;
        TypeContext returnType = getReturnType();
        printStream.println(indent(indent) + "field: " + returnType.context.toStringCurrentClass() + " " + field.getName());
        printStream.println(indent(indent) + "field type: " + returnType.toDetailedString(indent + 1));
        indent--;
        printStream.print(indent(indent) + "}");
        printStream.flush();
        return byteArrayOutputStream.toString();
    }
}
