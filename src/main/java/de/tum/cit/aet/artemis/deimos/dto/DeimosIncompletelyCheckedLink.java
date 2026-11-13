package de.tum.cit.aet.artemis.deimos.dto;

import java.util.List;

/**
 * Row for the Deimos completion email: a participation part of whose evidence was not examined, because of a limit, an
 * unreadable submission or a changed binary file or link, and that the model, if it was asked, found benign.
 *
 * @param url             deep link to the submissions view of the participation
 * @param participationId the participation id
 * @param omissions       the first omissions, in order of occurrence
 * @param omissionCount   the total number of distinct omissions, which may exceed {@code omissions.size()}
 * @param rationale       the model's rationale for its benign classification, empty when the model was not asked
 */
public record DeimosIncompletelyCheckedLink(String url, long participationId, List<DeimosOmission> omissions, long omissionCount, String rationale) {
}
