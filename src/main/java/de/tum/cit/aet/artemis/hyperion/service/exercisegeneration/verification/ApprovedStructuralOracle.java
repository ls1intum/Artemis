package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Projects an implementation-derived Ares oracle onto the exact public API frozen in an approved {@code SPEC.md}.
 * <p>
 * The implementation oracle remains useful for class metadata, but its members are never authoritative: solution edits must not turn an invented overload into a grading
 * requirement. The specification-stage contract requires signatures to be grouped by owner type in {@code ## Public API}; this parser deliberately implements only that small
 * contract instead of trying to understand arbitrary Markdown or Java source.
 */
final class ApprovedStructuralOracle {

    private static final Pattern CODE_SPAN = Pattern.compile("`([^`\\r\\n]+)`");

    private static final Pattern WORD = Pattern.compile("[A-Za-z_$][\\w$]*");

    private static final Set<String> MODIFIERS = Set.of("public", "protected", "private", "static", "final", "abstract", "default", "synchronized", "native", "strictfp",
            "transient", "volatile");

    private ApprovedStructuralOracle() {
    }

    static String project(String generatedOracle, String approvedSpec, Set<String> studentCreatedTypes, ObjectMapper mapper) throws IOException {
        Map<String, List<MemberSignature>> approvedMembers = parseMembers(approvedSpec, studentCreatedTypes);
        ArrayNode projected = mapper.createArrayNode();
        for (JsonNode entry : (ArrayNode) mapper.readTree(generatedOracle)) {
            String className = entry.path("class").path("name").asText("");
            if (!studentCreatedTypes.contains(className)) {
                continue;
            }
            ObjectNode kept = mapper.createObjectNode();
            kept.set("class", entry.path("class").deepCopy());
            List<MemberSignature> members = approvedMembers.getOrDefault(className, List.of());
            addMembers(kept, "methods", members, MemberKind.METHOD, mapper);
            addMembers(kept, "attributes", members, MemberKind.ATTRIBUTE, mapper);
            addMembers(kept, "constructors", members, MemberKind.CONSTRUCTOR, mapper);
            projected.add(kept);
        }
        return projected.isEmpty() ? "[]" : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(projected);
    }

    private static Map<String, List<MemberSignature>> parseMembers(String spec, Set<String> studentCreatedTypes) {
        String publicApi = section(spec, "## Public API");
        Map<String, List<MemberSignature>> byOwner = new LinkedHashMap<>();
        String currentOwner = studentCreatedTypes.size() == 1 ? studentCreatedTypes.iterator().next() : null;
        for (String line : publicApi.lines().toList()) {
            String ownerOnLine = ownerOnLine(line, studentCreatedTypes);
            if (ownerOnLine != null) {
                currentOwner = ownerOnLine;
            }
            Matcher code = CODE_SPAN.matcher(line);
            boolean foundCodeSpan = false;
            while (code.find()) {
                foundCodeSpan = true;
                MemberSignature signature = parseSignature(code.group(1), currentOwner, studentCreatedTypes);
                if (signature != null) {
                    byOwner.computeIfAbsent(signature.owner(), ignored -> new ArrayList<>()).add(signature);
                }
            }
            if (!foundCodeSpan) {
                String unformattedSignature = unformattedSignature(line);
                MemberSignature signature = parseSignature(unformattedSignature, currentOwner, studentCreatedTypes);
                if (signature != null) {
                    byOwner.computeIfAbsent(signature.owner(), ignored -> new ArrayList<>()).add(signature);
                }
            }
        }
        return byOwner;
    }

    private static String unformattedSignature(String line) {
        int publicStart = line.indexOf("public ");
        int protectedStart = line.indexOf("protected ");
        int start = publicStart < 0 ? protectedStart : protectedStart < 0 ? publicStart : Math.min(publicStart, protectedStart);
        if (start < 0) {
            return "";
        }
        int close = line.indexOf(')', start);
        int tableEnd = line.indexOf('|', start);
        int end = close >= 0 ? close + 1 : tableEnd >= 0 ? tableEnd : line.length();
        return line.substring(start, end);
    }

    private static String section(String document, String heading) {
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int next = document.indexOf("\n## ", start + heading.length());
        return next < 0 ? document.substring(start) : document.substring(start, next);
    }

    private static String ownerOnLine(String line, Set<String> owners) {
        for (String owner : owners) {
            if (Pattern.compile("\\b" + Pattern.quote(owner) + "\\b").matcher(line).find()) {
                return owner;
            }
        }
        return null;
    }

    private static MemberSignature parseSignature(String raw, String currentOwner, Set<String> owners) {
        String signature = raw.strip().replaceAll("\\s+", " ");
        int open = signature.indexOf('(');
        int close = signature.lastIndexOf(')');
        if (open >= 0 && close > open) {
            String rawPrefix = signature.substring(0, open).strip();
            List<String> prefix = words(rawPrefix);
            if (prefix.isEmpty()) {
                return null;
            }
            String name = prefix.getLast();
            String owner = owners.contains(name) ? name : currentOwner;
            if (owner == null) {
                return null;
            }
            List<String> parameters = parameterTypes(signature.substring(open + 1, close));
            List<String> modifiers = prefix.stream().filter(MODIFIERS::contains).toList();
            if (owners.contains(name)) {
                return new MemberSignature(owner, MemberKind.CONSTRUCTOR, name, null, parameters, publicModifiers(modifiers));
            }
            String returnType = returnType(rawPrefix, name);
            return returnType == null ? null : new MemberSignature(owner, MemberKind.METHOD, name, returnType, parameters, publicModifiers(modifiers));
        }

        List<String> tokens = words(signature);
        if (currentOwner == null || tokens.size() < 2 || tokens.stream().noneMatch(modifier -> modifier.equals("public") || modifier.equals("protected"))) {
            return null;
        }
        String name = tokens.getLast();
        String type = returnType(signature, name);
        return type == null ? null
                : new MemberSignature(currentOwner, MemberKind.ATTRIBUTE, name, type, List.of(), publicModifiers(tokens.stream().filter(MODIFIERS::contains).toList()));
    }

    private static List<String> words(String value) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(value);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private static String returnType(String prefix, String methodName) {
        String beforeName = prefix.substring(0, prefix.lastIndexOf(methodName));
        for (String modifier : MODIFIERS) {
            beforeName = beforeName.replaceAll("\\b" + modifier + "\\b", "");
        }
        String result = beforeName.replaceAll("\\s+", "");
        return result.isEmpty() ? null : result;
    }

    private static List<String> parameterTypes(String parameters) {
        if (parameters.isBlank()) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        for (String parameter : splitParameters(parameters)) {
            String withoutAnnotations = parameter.replaceAll("@[\\w.]+(?:\\([^)]*\\))?", "").replace("final ", "").strip();
            int lastSpace = withoutAnnotations.lastIndexOf(' ');
            String type = lastSpace < 0 ? withoutAnnotations : withoutAnnotations.substring(0, lastSpace);
            types.add(canonicalParameterType(type));
        }
        return List.copyOf(types);
    }

    private static List<String> splitParameters(String parameters) {
        List<String> parts = new ArrayList<>();
        int genericDepth = 0;
        int start = 0;
        for (int index = 0; index < parameters.length(); index++) {
            char current = parameters.charAt(index);
            if (current == '<') {
                genericDepth++;
            }
            else if (current == '>') {
                genericDepth--;
            }
            else if (current == ',' && genericDepth == 0) {
                parts.add(parameters.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(parameters.substring(start));
        return parts;
    }

    private static String canonicalParameterType(String type) {
        if (type == null) {
            return null;
        }
        String erased = type.strip().replace("...", "[]").replaceAll("<.*>", "").replaceAll("\\s+", "");
        int packageSeparator = erased.lastIndexOf('.');
        return packageSeparator < 0 ? erased : erased.substring(packageSeparator + 1);
    }

    private static List<String> publicModifiers(List<String> modifiers) {
        List<String> result = modifiers.stream()
                .filter(modifier -> modifier.equals("public") || modifier.equals("protected") || modifier.equals("static") || modifier.equals("final")).toList();
        return result.isEmpty() ? List.of("public") : result;
    }

    private static void addMembers(ObjectNode entry, String field, List<MemberSignature> members, MemberKind kind, ObjectMapper mapper) {
        ArrayNode values = mapper.createArrayNode();
        members.stream().filter(member -> member.kind() == kind).forEach(member -> values.add(member.toJson(mapper)));
        if (!values.isEmpty()) {
            entry.set(field, values);
        }
    }

    private enum MemberKind {
        METHOD, ATTRIBUTE, CONSTRUCTOR,
    }

    private record MemberSignature(String owner, MemberKind kind, String name, String type, List<String> parameters, List<String> modifiers) {

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("name", name);
            node.set("modifiers", mapper.valueToTree(modifiers));
            if (kind == MemberKind.METHOD) {
                if (!parameters.isEmpty()) {
                    node.set("parameters", mapper.valueToTree(parameters));
                }
                node.put("returnType", type);
            }
            else if (kind == MemberKind.ATTRIBUTE) {
                node.put("type", type);
            }
            else if (!parameters.isEmpty()) {
                node.set("parameters", mapper.valueToTree(parameters));
            }
            return node;
        }
    }
}
