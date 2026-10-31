package de.tum.cit.aet.artemis.deimos.dto;

import java.util.List;

/**
 * Row for the Deimos completion email: a participation the model found benign, but only after part of its evidence had
 * been left out to stay within a limit or because a submission could not be read.
 *
 * @param url             deep link to the submissions view of the participation
 * @param participationId the participation id
 * @param omissions       the first omissions, in order of occurrence
 * @param omissionCount   the total number of distinct omissions, which may exceed {@code omissions.size()}
 * @param rationale       the model's rationale for its benign classification
 */
public record DeimosIncompletelyCheckedLink(String url, long participationId, List<DeimosOmission> omissions, long omissionCount, String rationale) {
}
