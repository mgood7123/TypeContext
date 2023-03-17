# TypeContext
Java Reflection based type analysis powered by Generics-Resolver 3.0.3

#### example
```java
// test for List<String>

// assume Foo exists and it contains a field named "foo" (public or non public)
TypeContext ret = new TypeContext(Foo.class).findField("foo").getReturnType();
boolean m = false;

// findSuperclassOrInterface checks if
// this class, or any of its interfaces, superclasses, or implementing contexts (if originating from wildcard)
// matches the given class
TypeContext r = ret.findSuperclassOrInterface(List.class);

if (r != null) {
    // if we end up here we know we are List<?>.class
    // r must be List but ret can be any class that implements List, such as ArrayList
    // find out what generic parameters we accept
    if (r.getGenericParameters(0).findSuperclassOrInterface(String.class) != null) {
        // String is final so nothing can extend it
        // String is not an interface so nothing can implement it
        //
        // this means if we end up here we know we are List<String>.class (and we accept a generic parameter of exactly String.class and nothing else)
        m = true;
        break;
    }
}
// if r == null we could not find List
// if r != null && m == false, we found List but it was not List<String>
// if r != null && m == true, we found List<String>
```
