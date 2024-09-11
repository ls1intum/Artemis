package de.tum.cit.aet.artemis.domain.competency;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * The type of competency according to Bloom's revised taxonomy.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Bloom%27s_taxonomy">Wikipedia</a>
 */
public enum CompetencyTaxonomy {

    REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE;

    @Converter
    public static class TaxonomyConverter implements AttributeConverter<CompetencyTaxonomy, String> {

        @Override
        public String convertToDatabaseColumn(CompetencyTaxonomy taxonomy) {
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
        public CompetencyTaxonomy convertToEntityAttribute(String value) {
            if (value == null) {
                return null;
            }

            return switch (value) {
                case "R" -> CompetencyTaxonomy.REMEMBER;
                case "U" -> CompetencyTaxonomy.UNDERSTAND;
                case "Y" -> CompetencyTaxonomy.APPLY;
                case "A" -> CompetencyTaxonomy.ANALYZE;
                case "E" -> CompetencyTaxonomy.EVALUATE;
                case "C" -> CompetencyTaxonomy.CREATE;
                default -> throw new IllegalArgumentException("Unknown Taxonomy: " + value);
            };
        }
    }

}
