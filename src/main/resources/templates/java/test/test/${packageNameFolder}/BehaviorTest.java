package ${packageName};

import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 2.0 (24.02.2019)
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
            fail("Problem: the class '" + className + "' was not found within the submission. Please implement it properly.");
        }

        return null;
    }

    /**
     * Instantiate an object of a given class by its qualified name and the constructor arguments, if applicable.
     * @param qualifiedClassName: The qualified name of the class that needs to get retrieved (package.classname)
     * @param constructorArgs: Parameter instances of the constructor of the class, that it has to get instantiated with. Do not include, if the constructor has no arguments.
     * @return The instance of this class.
     */
    protected Object newInstance(String qualifiedClassName, Object... constructorArgs) {
        Class<?> clazz = getClass(qualifiedClassName);
        Class<?>[] constructorArgTypes = getParameterTypes(constructorArgs);

        String className = qualifiedClassName.split("\\.")[qualifiedClassName.split("\\.").length - 1];

        String failMessage = "Problem: could not instantiate the class '" + className + "' because";
        try {
            Constructor<?> constructor = clazz.getConstructor(constructorArgTypes);

            try {
                return constructor.newInstance(constructorArgs);
            } catch (IllegalAccessException iae) {
                fail(failMessage += " access to its constructor with the parameters: " + getParameterTypesAsString(constructorArgTypes) + " was denied."
                    + " Please check the modifiers of the constructor.");
            } catch (IllegalArgumentException iae) {
                fail(failMessage += " the actual constructor or none of the actual constructors of this class match the expected one."
                    + " We expect, amongst others, one with " + constructorArgs.length + " arguments."
                    + " Please implement this constructor correctly.");
            } catch (InstantiationException ie) {
                fail(failMessage += " the class is abstract and should not have a constructor."
                    + " Please remove the constructor of the class.");
            } catch (InvocationTargetException ite) {
                fail(failMessage += " the constructor with " + constructorArgs.length + " parameters threw an exception and could not be initialized."
                    + " Please check the constructor implementation.");
            } catch (ExceptionInInitializerError eiie) {
                fail(failMessage += " the constructor with " + constructorArgs.length + " parameters could not be initialized.");
            }
        } catch (NoSuchMethodException nsme) {
            fail(failMessage += " the class does not have a constructor with the arguments: "
                + getParameterTypesAsString(constructorArgTypes) + ". Please implement this constructor properly.");
        } catch (SecurityException se) {
            fail(failMessage += " access to the package of the class was denied.");
        }

        return null;
    }

    /**
     * Retrieve an attribute value of a given instance of a class by the attribute name.
     * @param object: The instance of the class that contains the attribute.
     * @param attributeName: The name of the attribute whose value needs to get retrieved.
     * @return The instance of the attribute with the wanted value.
     */
    protected Object valueForAttribute(Object object, String attributeName) {
        String failMessage = "Problem: could not retrieve the attribute '" + attributeName + "' from the class '"
            + object.getClass().getSimpleName() + "' because";

        try {
            return object.getClass().getField(attributeName).get(object);
        } catch (NoSuchFieldException nsfe) {
            fail(failMessage += " the attribute does not exist. Please implement the attribute correctly.");
        } catch (SecurityException se) {
            fail(failMessage += " access to the package of the class was denied.");
        } catch (IllegalAccessException iae) {
            fail(failMessage += " access to the attribute was denied. Please check the modifiers of the attribute.");
        }

        return null;
    }

    /**
     * Retrieve a method with arguments of a given class instance by its name.
     * @param declaringClass: The class that declares this method.
     * @param methodName: The name of this method.
     * @param parameterTypes: The parameter types of this method. Do not include if the method has no parameters.
     * @return The wanted method.
     */
    protected Method getMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        String failMessage = "Problem: could not retrieve the method '" + methodName + "' with the parameters: "
            + getParameterTypesAsString(parameterTypes) + " from the class " + declaringClass.getSimpleName() + " because";

        try {
            return declaringClass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException nsme) {
            fail(failMessage += " the method does not exist. Please implement this method properly.");
        } catch (NullPointerException npe) {
            fail(failMessage += " the name of the method is null. Please check the name of the method.");
        } catch (SecurityException se) {
            fail(failMessage += " access to the package class was denied.");
        }

        return null;
    }

    /**
     * Invoke a given method of a given class instance with instances of the parameters.
     * @param object: The instance of the class that should invoke the method.
     * @param method: The method that has to get invoked.
     * @param params: Parameter instances of the method. Do not include if the method has no parameters.
     * @return The return instance of the method.
     */
    protected Object invokeMethod(Object object, Method method, Object... params) {
        String failMessage = "Problem: could not invoke the method '" + method.getName() + "' in the class '" + object.getClass().getSimpleName() + "' because";
        try {
            return method.invoke(object, params);
        } catch (IllegalAccessException iae) {
            fail(failMessage += " access to the method was denied. Please check the modifiers of the method.");
        } catch (IllegalArgumentException iae) {
            fail(failMessage += " the parameters are not implemented right. Please check the parameters of the method");
        } catch (InvocationTargetException e) {
            fail(failMessage += " of an exception within the method: " + e.getCause().toString());
        }

        return null;
    }

    /**
     * Retrieves the parameters types of a given collection of parameter instances.
     * @param params: The instances of the parameters.
     * @return The parameter types of the instances as an array.
     */
    private Class<?>[] getParameterTypes(Object... params) {
        Class<?>[] parameterTypes;

        if(params != null && params.length > 0) {
            parameterTypes = new Class<?>[params.length];

            for(Object param : params) {
                Class<?> paramType = param.getClass();
                parameterTypes[Arrays.asList(params).indexOf(param)] = paramType;
            }
        } else {
            parameterTypes = null;
        }

        return parameterTypes;
    }

    /**
     * Generates a string representation of a given collection of parameter types.
     * @param parameterTypes: The parameter types we want a string representation of.
     * @return The string representation of the parameter types.
     */
    private String getParameterTypesAsString(Class<?>... parameterTypes) {
        if(parameterTypes.length == 0) {
            return "[ none ]";
        } else {
            String parameterTypesInformation = "[ ";

            for(int i = 0; i < parameterTypes.length; i++) {
                parameterTypesInformation += parameterTypes[i].getSimpleName() + ((i == parameterTypes.length - 1) ? "" : ", ");
            }

            return parameterTypesInformation += " ]";
        }
    }
}
