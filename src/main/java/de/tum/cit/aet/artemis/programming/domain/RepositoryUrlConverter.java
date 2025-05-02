package de.tum.cit.aet.artemis.programming.domain;

import java.net.URI;
import java.util.regex.Pattern;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.springframework.beans.factory.annotation.Value;

/**
 * This class is responsible for converting the repository URL to a database value and vice versa.
 * A repository URL generally has the following form:
 * <p>
 * {vc.url}/git/{project_key}/{user_part}.git
 * <p>
 * We want to store only the {project_key}/{user_part} part in the database as this identifies the repository uniquely.
 */
@Converter
public class RepositoryUrlConverter implements AttributeConverter<String, String> {

    private final String prefix;

    private final String postfix = ".git";

    private final Pattern pattern;

    /**
     * @param vcUrlString The base URL of the version control system
     */
    public RepositoryUrlConverter(@Value("${artemis.version-control.url}") String vcUrlString) {
        URI vcUrl;
        try {
            vcUrl = URI.create(vcUrlString);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid version control URL: " + vcUrlString, e);
        }

        this.prefix = vcUrl.resolve("git").toString();
        this.pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(.*?)" + Pattern.quote(postfix) + "$");
    }

    /**
     * Converts the original repository URL to a database value.
     * Removes {vc.url}/git/ and .git from the repository URL.
     *
     * @param attribute The original repository URL in the form {vc.url}/git/{project_key}/{user_part}.git
     * @return The database value in the form {project_key}/{user_part}
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        // Remove prefix and postfix by regex
        var matcher = pattern.matcher(attribute);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid repository URL: " + attribute);
        }

        // Extract the {project_key}/{user_part} part
        return matcher.group(1);
    }

    /**
     * Converts the database value back to the original repository URL.
     * Adds {vc.url}/git/ and .git back to the {project_key}/{user_part} part.
     *
     * @param dbData The database value in the form {project_key}/{user_part}
     * @return The original repository URL in the form {vc.url}/git/{project_key}/{user_part}.git
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        // Add prefix and postfix back
        return prefix + dbData + postfix;
    }
}
