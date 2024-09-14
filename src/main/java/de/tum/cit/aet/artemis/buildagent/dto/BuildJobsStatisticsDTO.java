package de.tum.cit.aet.artemis.buildagent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildJobsStatisticsDTO(long totalBuilds, long successfulBuilds, long failedBuilds, long cancelledBuilds) {

    /**
     * Create a BuildJobsStatisticsDTO from a list of BuildJobResultCountDTOs.
     *
     * @param resultCountDTOList the list of BuildJobResultCountDTOs
     * @return the BuildJobsStatisticsDTO
     */
    public static BuildJobsStatisticsDTO of(List<BuildJobResultCountDTO> resultCountDTOList) {
        long totalBuilds;
        long successfulBuilds = 0;
        long failedBuilds = 0;
        long cancelledBuilds = 0;
        // Switch case would cause an error in the testDTOImplementations test
        for (BuildJobResultCountDTO resultCountDTO : resultCountDTOList) {
            if (resultCountDTO.status() == BuildStatus.SUCCESSFUL) {
                successfulBuilds += resultCountDTO.count();
            }
            else if (resultCountDTO.status() == BuildStatus.FAILED || resultCountDTO.status() == BuildStatus.ERROR) {
                failedBuilds += resultCountDTO.count();
            }
            else if (resultCountDTO.status() == BuildStatus.CANCELLED) {
                cancelledBuilds += resultCountDTO.count();
            }
        }
        totalBuilds = successfulBuilds + failedBuilds + cancelledBuilds;
        return new BuildJobsStatisticsDTO(totalBuilds, successfulBuilds, failedBuilds, cancelledBuilds);
    }
}
