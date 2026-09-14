package ${packageName};

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;

/** Checks the generic API from the approved specification, independently of the reference implementation. */
@Public
@WhitelistPath("build")
@BlacklistPath("build/classes/java/test")
class GenericTypeTest {

    @TestFactory
    @StrictTimeout(10)
    List<DynamicTest> genericApi() throws Exception {
        JSONArray oracle;
        try (InputStream input = getClass().getResourceAsStream("test.json")) {
            if (input == null) {
                throw new IllegalStateException("The approved structure oracle is missing");
            }
            oracle = new JSONArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (int i = 0; i < oracle.length(); i++) {
            JSONObject entry = oracle.getJSONObject(i);
            if (entry.has("genericApi")) {
                String name = entry.getJSONObject("class").getString("name");
                tests.add(DynamicTest.dynamicTest("testGenericApi[" + name + "]", () -> check(entry)));
            }
        }
        return tests;
    }

    private static void check(JSONObject entry) throws Exception {
        JSONObject contract = entry.getJSONObject("genericApi");
        String packageName = entry.getJSONObject("class").getString("package");
        String name = entry.getJSONObject("class").getString("name");
        Class<?> owner = Class.forName(packageName.isEmpty() ? name : packageName + "." + name);
        TypeVariable<?>[] variables = owner.getTypeParameters();
        assertEquals(contract.getInt("parameterCount"), variables.length, "Declare the required class type parameters");
        for (TypeVariable<?> variable : variables) {
            assertTrue(Arrays.equals(new Type[] { Object.class }, variable.getBounds()), "The class type parameters must be unbounded");
        }
        Set<String> actual = new HashSet<>();
        for (var method : owner.getDeclaredMethods()) {
            if (visible(method.getModifiers()) && !method.isSynthetic()) {
                actual.add("method:" + method.getName() + parameters(method.getGenericParameterTypes(), owner, contract) + ":"
                        + shape(method.getGenericReturnType(), owner, contract));
            }
        }
        for (var constructor : owner.getDeclaredConstructors()) {
            if (visible(constructor.getModifiers())) {
                actual.add("constructor:" + parameters(constructor.getGenericParameterTypes(), owner, contract));
            }
        }
        for (var field : owner.getDeclaredFields()) {
            if (visible(field.getModifiers())) {
                actual.add("field:" + field.getName() + ":" + shape(field.getGenericType(), owner, contract));
            }
        }
        for (Object signature : contract.getJSONArray("signatures")) {
            assertTrue(actual.contains(signature.toString()), "Missing generic API contract: " + signature);
        }
    }

    private static boolean visible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static List<String> parameters(Type[] types, Class<?> owner, JSONObject contract) {
        return Arrays.stream(types).map(type -> shape(type, owner, contract)).toList();
    }

    private static String shape(Type type, Class<?> owner, JSONObject contract) {
        String name = type.getTypeName().replace(", ", ",");
        // Bind type variables before shortening qualified class names, which may shadow those variables.
        TypeVariable<?>[] variables = owner.getTypeParameters();
        for (int i = 0; i < variables.length; i++) {
            name = name.replaceAll("(?<![\\w$.])" + Pattern.quote(variables[i].getName()) + "(?![\\w$.])", java.util.regex.Matcher.quoteReplacement("$" + i));
        }
        String prefix = owner.getPackageName();
        for (Object exerciseType : contract.getJSONArray("exerciseTypes")) {
            if (!prefix.isEmpty()) {
                name = name.replaceAll("(?<![\\w$])" + Pattern.quote(prefix + "." + exerciseType) + "(?![\\w$])", exerciseType.toString());
            }
        }
        for (String namespace : List.of("java.lang.", "java.util.")) {
            var matcher = Pattern.compile("(?<![\\w$])" + Pattern.quote(namespace) + "([A-Z][\\w$]*)(?![\\w$])").matcher(name);
            StringBuilder normalized = new StringBuilder();
            while (matcher.find()) {
                boolean owned = contract.getJSONArray("exerciseTypes").toList().contains(matcher.group(1));
                matcher.appendReplacement(normalized, java.util.regex.Matcher.quoteReplacement(owned ? matcher.group() : matcher.group(1)));
            }
            matcher.appendTail(normalized);
            name = normalized.toString();
        }
        return name;
    }
}
