package de.tum.in.www1.artemis.domain;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * The type of learning goal according to Bloom's revised taxonomy.
 * @see <a href="https://en.wikipedia.org/wiki/Bloom%27s_taxonomy">Wikipedia</a>
 */
public enum LearningGoalTaxonomy {

    REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE;

    @Converter
    public static class TaxonomyConverter implements AttributeConverter<LearningGoalTaxonomy, String> {

        @Override
        public String convertToDatabaseColumn(LearningGoalTaxonomy taxonomy) {
            if (taxonomy == null) {
                return null;
            }

            return switch (taxonomy) {
                case REMEMBER -> "R";
                case UNDERSTAND -> "U";
                case APPLY -> "Y";
                case ANALYZE -> "A";
                case EVALUATE -> "E";
                case CREATE -> "C";
            };
        }

        @Override
        public LearningGoalTaxonomy convertToEntityAttribute(String value) {
            if (value == null) {
                return null;
            }

            return switch (value) {
                case "R" -> LearningGoalTaxonomy.REMEMBER;
                case "U" -> LearningGoalTaxonomy.UNDERSTAND;
                case "Y" -> LearningGoalTaxonomy.APPLY;
                case "A" -> LearningGoalTaxonomy.ANALYZE;
                case "E" -> LearningGoalTaxonomy.EVALUATE;
                case "C" -> LearningGoalTaxonomy.CREATE;
                default -> throw new IllegalArgumentException("Unknown Taxonomy: " + value);
            };
        }
    }

}
