package de.tum.in.www1.artemis.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        for (BuildJobResultCountDTO resultCountDTO : resultCountDTOList) {
            switch (resultCountDTO.status()) {
                case SUCCESSFUL -> successfulBuilds += resultCountDTO.count();
                case FAILED, ERROR -> failedBuilds += resultCountDTO.count();
                case CANCELLED -> cancelledBuilds += resultCountDTO.count();
            }
        }
        totalBuilds = successfulBuilds + failedBuilds + cancelledBuilds;
        return new BuildJobsStatisticsDTO(totalBuilds, successfulBuilds, failedBuilds, cancelledBuilds);
    }
}
