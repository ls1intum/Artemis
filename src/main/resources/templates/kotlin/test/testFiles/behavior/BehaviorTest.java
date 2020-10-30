package ${packageName};

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.StringJoiner;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 3.2 (27.10.2020)
 * <br><br>
 *
 * This class serves as an API to Java Reflection to facilitate various operations that are performed
 * regularly in the functional tests. Facilitation mainly means automatically handling all the various
 * errors Reflection is able to intercept through exceptions and delivering appropriate feedback
 * to these errors. The operations include:
 * - Retrieving a class given its qualified name,
 * - Instantiating an object of a given class and with given constructor arguments,
 * - Retrieving the value of an attribute from an object given the attribute's name,
 * - Retrieving a method from a class given the method's name and parameter types,
 * - Invoking a method with certain parameter instances and retrieving its return type.
 */
public abstract class BehaviorTest {

    /**
     * Retrieve the actual class by its qualified name.
     * @param qualifiedClassName: The qualified name of the class that needs to get retrieved (package.classname)
     * @return The wanted class object.
     */
    protected Class<?> getClass(String qualifiedClassName) {
        try {
            return Class.forName(qualifiedClassName);
        } catch (ClassNotFoundException e) {
            // The simple class name is the last part of the qualified class name.
            String className = qualifiedClassName.split("\\.")[qualifiedClassName.split("\\.").length - 1];
            fail("The class '" + className + "' was not found within the submission. Make sure to implement it properly.");
        }

        return null;
    }

    /**
     * Instantiate an object of a given class with the constructor arguments, if applicable.
     * <p>
     * This method does not support passing null, passing subclasses of the parameter types or invoking constructors with primitive parameters.
     * Use {@link #newInstance(Constructor, Object...)} for that.
     * @param qualifiedClassName: The qualified name of the class that needs to get retrieved (package.classname)
     * @param constructorArgs: Parameter instances of the constructor of the class, that it has to get instantiated with. Do not include, if the constructor has no arguments.
     * @return The instance of this class.
     * @see #newInstance(Class, Object...)
     */
    protected Object newInstance(String qualifiedClassName, Object... constructorArgs) {
        return newInstance(getClass(qualifiedClassName), constructorArgs);
    }

    /**
     * Instantiate an object of a given class by its qualified name and the constructor arguments, if applicable.
     * <p>
     * This method does not support passing null, passing subclasses of the parameter types or invoking constructors with primitive parameters.
     * Use {@link #newInstance(Constructor, Object...)} for that.
     * @param clazz: new instance of which is required
     * @param constructorArgs: Parameter instances of the constructor of the class, that it has to get instantiated with. Do not include, if the constructor has no arguments.
     * @return The instance of this class.
     */
    protected Object newInstance(Class<?> clazz, Object... constructorArgs) {
        String failMessage = "Could not instantiate the class " + clazz.getSimpleName() + " because";
        Class<?>[] constructorArgTypes = getParameterTypes(failMessage + " a fitting constructor could not be found because", constructorArgs);

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(constructorArgTypes);
            return constructor.newInstance(constructorArgs);
        } catch (IllegalAccessException iae) {
            fail(failMessage += " access to its constructor with the parameters: " + getParameterTypesAsString(constructorArgTypes) + " was denied."
                + " Make sure to check the modifiers of the constructor.");
        } catch (IllegalArgumentException iae) {
            fail(failMessage += " the actual constructor or none of the actual constructors of this class match the expected one."
                + " We expect, amongst others, one with " + getParameterTypesAsString(constructorArgTypes) + " parameters, which is not exist."
                + " Make sure to implement this constructor correctly.");
        } catch (InstantiationException ie) {
            fail(failMessage += " the class is abstract and should not have a constructor."
                + " Make sure to remove the constructor of the class.");
        } catch (InvocationTargetException ite) {
            fail(failMessage += " the constructor with " + constructorArgs.length + " parameters threw an exception and could not be initialized."
                + " Make sure to check the constructor implementation.");
        } catch (ExceptionInInitializerError eiie) {
            fail(failMessage += " the constructor with " + constructorArgs.length + " parameters could not be initialized.");
        } catch (NoSuchMethodException nsme) {
            fail(failMessage += " the class does not have a constructor with the arguments: "
                + getParameterTypesAsString(constructorArgTypes) + ". Make sure to implement this constructor properly.");
        } catch (SecurityException se) {
            fail(failMessage += " access to the package of the class was denied.");
        }

        return null;
    }

    /**
     * Instantiate an object of a given class by its qualified name and the constructor arguments, if applicable.
     * @param constructorArgs: Parameter instances of the constructor of the class, that it has to get instantiated with. Do not include, if the constructor has no arguments.
     * @return The instance of this class.
     */
    protected Object newInstance(Constructor<?> constructor, Object... constructorArgs) {
        String failMessage = "Could not instantiate the class "
                + constructor.getDeclaringClass().getSimpleName() + " because";

        try {
            return constructor.newInstance(constructorArgs);
        } catch (IllegalAccessException iae) {
            fail(failMessage + " access to its constructor with the parameters: "
                    + getParameterTypesAsString(constructor.getParameterTypes())
                    + " was denied."
                    + " Make sure to check the modifiers of the constructor.");
        } catch (IllegalArgumentException iae) {
            fail(failMessage + " the actual constructor or none of the actual constructors of this class match the expected one."
                    + " We expect, amongst others, one with "
                    + getParameterTypesAsString(constructor.getParameterTypes())
                    + " parameters, which is not exist."
                    + " Make sure to implement this constructor correctly.");
        } catch (InstantiationException ie) {
            fail(failMessage + " the class is abstract and should not have a constructor."
                    + " Make sure to remove the constructor of the class.");
        } catch (InvocationTargetException ite) {
            fail(failMessage + " the constructor with " + constructorArgs.length + " parameters threw an exception and could not be initialized."
                    + " Make sure to check the constructor implementation.");
        } catch (ExceptionInInitializerError eiie) {
            fail(failMessage + " the constructor with " + constructorArgs.length + " parameters could not be initialized.");
        } catch (SecurityException se) {
            fail(failMessage + " access to the package of the class was denied.");
        }

        return null;
    }

    /**
     * Retrieve an attribute value of a given instance of a class by the attribute name.
     * @param object: The instance of the class that contains the attribute. Must not be null, even for static fields.
     * @param attributeName: The name of the attribute whose value needs to get retrieved.
     * @return The instance of the attribute with the wanted value.
     */
    protected Object valueForAttribute(Object object, String attributeName) {
        requireNonNull(object, "Could not retrieve the value of attribute '" + attributeName + "' because the object was null.");
        String failMessage = "Could not retrieve the attribute '" + attributeName + "' from the class "
            + object.getClass().getSimpleName() + " because";

        try {
            return object.getClass().getDeclaredField(attributeName).get(object);
        } catch (NoSuchFieldException nsfe) {
            fail(failMessage += " the attribute does not exist. Make sure to implement the attribute correctly.");
        } catch (SecurityException se) {
            fail(failMessage += " access to the package of the class was denied.");
        } catch (IllegalAccessException iae) {
            fail(failMessage += " access to the attribute was denied. Make sure to check the modifiers of the attribute.");
        }

        return null;
    }

    /**
     * Helper method that retrieves a method with arguments of a given object by its name.
     *
     * @param object: instance of the class that defines the method.
     * @param methodName: the name of the method.
     * @param parameterTypes: The parameter types of this method. Do not include if the method has no parameters.
     * @return The wanted method.
     */
    protected Method getMethod(Object object, String methodName, Class<?>... parameterTypes) {
    	requireNonNull(object, "Could not find the method named '" + methodName + "' because the object was null.");
        return getMethod(object.getClass(), methodName, parameterTypes);
    }

    /**
     * Retrieve a method with arguments of a given class by its name.
     * @param declaringClass: The class that declares this method.
     * @param methodName: The name of this method.
     * @param parameterTypes: The parameter types of this method. Do not include if the method has no parameters.
     * @return The wanted method.
     */
    protected Method getMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        String failMessage = "Could not find the method '" + methodName + "' with the parameters: "
            + getParameterTypesAsString(parameterTypes) + " in the class " + declaringClass.getSimpleName() + " because";

        if (parameterTypes == null || parameterTypes.length == 0) {
            failMessage = "Could not find the method '" + methodName + "' from the class " + declaringClass.getSimpleName() + " because";
        }

        try {
            return declaringClass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException nsme) {
            fail(failMessage += " the method does not exist. Make sure to implement this method properly.");
        } catch (NullPointerException npe) {
            fail(failMessage += " the name of the method is null. Make sure to check the name of the method.");
        } catch (SecurityException se) {
            fail(failMessage += " access to the package class was denied.");
        }

        return null;
    }

    /**
     * Invoke a given method of a given object with instances of the parameters.
     * @param object: The instance of the class that should invoke the method. Can be null if the method is static.
     * @param method: The method that has to get invoked.
     * @param params: Parameter instances of the method. Do not include if the method has no parameters.
     * @return The return value of the method.
     */
    protected Object invokeMethod(Object object, Method method, Object... params) {
        // NOTE: object can be null, if method is static
        String failMessage = "Could not invoke the method '" + method.getName()
                + "' in the class " + method.getDeclaringClass().getSimpleName() + " because";
        try {
            return method.invoke(object, params);
        } catch (IllegalAccessException iae) {
            fail(failMessage += " access to the method was denied. Make sure to check the modifiers of the method.");
        } catch (IllegalArgumentException iae) {
            fail(failMessage += " the parameters are not implemented right. Make sure to check the parameters of the method");
        } catch (InvocationTargetException e) {
            fail(failMessage += " of an exception within the method: " + e.getCause().toString());
        }

        return null;
    }

    /**
     * Invoke a given method of a given object with instances of the parameters,
     * and rethrow an exception if one occurs during the method execution.
     * @param object: The instance of the class that should invoke the method.
     * @param method: The method that has to get invoked.
     * @param params: Parameter instances of the method. Do not include if the method has no parameters.
     * @return The return value of the method.
     */
    protected Object invokeMethodRethrowing(Object object, Method method, Object... params) throws Throwable {
        // NOTE: object can be null, if method is static
        String failMessage = "Could not invoke the method '" + method.getName()
                + "' in the class " + method.getDeclaringClass().getSimpleName() + " because";
        try {
            return method.invoke(object, params);
        } catch (IllegalAccessException iae) {
            fail(failMessage += " access to the method was denied. Make sure to check the modifiers of the method.");
        } catch (IllegalArgumentException iae) {
            fail(failMessage += " the parameters are not implemented right. Make sure to check the parameters of the method");
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }

        return null;
    }

    /**
     * Invoke a given method name of a given object with instances of the parameters.
     * <p>
     * This method does not support invoking static methods and passing null, passing subclasses of the parameter types or invoking
     * methods with primitive parameters. Use {@link #invokeMethod(Object, Method, Object...)} for that.
     * @param object: The instance of the class that should invoke the method. Must not be null, even for static methods.
     * @param methodName: The method name that has to get invoked.
     * @param params: Parameter instances of the method. Do not include if the method has no parameters.
     * @return The return value of the method.
     */
    protected Object invokeMethod(Object object, String methodName, Object... params) {
        String failMessage = "Could not find the method '" + methodName + "' because";
        Class<?>[] parameterTypes = getParameterTypes(failMessage, params);
        Method method = getMethod(object, methodName, parameterTypes);
        return invokeMethod(object, method, params);
    }

    /**
     * Retrieve a constructor with arguments of a given class.
     * @param declaringClass: The class that declares this constructor.
     * @param parameterTypes: The parameter types of this method. Do not include if the method has no parameters.
     * @return The wanted method.
     */
    protected Constructor<?> getConstructor(Class<?> declaringClass, Class<?>... parameterTypes) {
        String failMessage = "Could not find the constructor with the parameters: "
                + getParameterTypesAsString(parameterTypes)
                + " in the class " + declaringClass.getSimpleName() + " because";

        if (parameterTypes == null || parameterTypes.length == 0) {
            failMessage = "Could not find the constructor from the " + declaringClass.getSimpleName() + " because";
        }

        try {
            return declaringClass.getConstructor(parameterTypes);
        } catch (NoSuchMethodException nsme) {
            fail(failMessage + " the method does not exist. Make sure to implement this method properly.");
        } catch (NullPointerException npe) {
            fail(failMessage + " the name of the method is null. Make sure to check the name of the method.");
        } catch (SecurityException se) {
            fail(failMessage + " access to the package class was denied.");
        }

        return null;
    }

    /**
     * Retrieves the parameters types of a given collection of parameter instances.
     * @param failMessage: The beginning of message of the failure message if one of params is null
     * @param params: The instances of the parameters.
     * @return The parameter types of the instances as an array.
     */
    private Class<?>[] getParameterTypes(String failMessage, Object... params) {
        return Arrays.stream(params)
                .map(it -> requireNonNull(it, failMessage + " one of the supplied arguments was null."))
                .map(Object::getClass)
                .toArray(Class<?>[]::new);
    }

    /**
     * Generates a string representation of a given collection of parameter types.
     * @param parameterTypes: The parameter types we want a string representation of.
     * @return The string representation of the parameter types.
     */
    private String getParameterTypesAsString(Class<?>... parameterTypes) {
        StringJoiner joiner = new StringJoiner(", ", "[ ", " ]");
        joiner.setEmptyValue("none");
        Arrays.stream(parameterTypes).map(Class::getSimpleName).forEach(joiner::add);
        return joiner.toString();
    }
}
