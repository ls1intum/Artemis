package de.tum.in.www1.artemis.domain.iris.settings;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class IrisModelListConverter implements AttributeConverter<Set<String>, String> {

    @Override
    public String convertToDatabaseColumn(Set<String> type) {
        if (type == null || type.isEmpty()) {
            return null;
        }

        return String.join(",", type);
    }

    @Override
    public Set<String> convertToEntityAttribute(String value) {
        var treeSet = new TreeSet<String>(Comparator.naturalOrder());
        if (value != null) {
            treeSet.addAll(Set.of(value.split(",")));
        }
        return treeSet;
    }
}
