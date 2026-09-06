package de.tum.cit.aet.artemis.core.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Normalizes a stored external file URI to the canonical spelling on the way out of the database, and stores the canonical spelling on the way in.
 * <p>
 * Clients append the value of one of these columns to {@code api/core/files}, so the stored string <em>is</em> the request URL. {@code FilePathConverter} emits the canonical
 * spelling and a migration re-spelled the rows written before it, but two situations still leave the legacy spelling at rest: a row the migration skipped, and a row a node
 * still running the previous release wrote after the migration had already run. Running every read through {@link FilePathConverter#canonicalExternalUri} is what allows
 * {@code FileResource} to serve only the canonical path: whichever spelling a row carries, the client is handed the canonical one, so no client release and no completed
 * migration is a precondition for removing the legacy aliases.
 * <p>
 * The converter is deliberately <b>not</b> {@code autoApply}: it must only touch columns that hold an external file URI, and it is declared per field with
 * {@link jakarta.persistence.Convert @Convert}. It applies to entity loads and to JPQL constructor-expression projections that select the annotated attribute, which is why it
 * is preferred over normalizing inside each DTO — several DTOs are built by a projection that never passes through the entity getter.
 * <p>
 * Writing the canonical spelling back means a value can grow by up to ten characters, so every column this is declared on has been widened to fit; see the changelog that
 * introduced the widening. Nothing here parses a path: the file system lookup keeps going through
 * {@link FilePathConverter#fileSystemPathForExternalUri}, which accepts both spellings and must keep doing so, because the directory layout on disk is unchanged.
 */
@Converter
public class CanonicalFileUriConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FilePathConverter.canonicalExternalUri(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FilePathConverter.canonicalExternalUri(dbData);
    }
}
