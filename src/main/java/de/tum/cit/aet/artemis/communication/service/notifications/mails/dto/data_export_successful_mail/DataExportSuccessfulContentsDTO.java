package de.tum.cit.aet.artemis.communication.service.notifications.mails.dto.data_export_successful_mail;

import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.core.domain.DataExport;

public record DataExportSuccessfulContentsDTO(Set<DataExportSuccessfulContentDTO> contents) {

    public static DataExportSuccessfulContentsDTO of(Set<DataExport> exportSet) {
        Set<DataExportSuccessfulContentDTO> contents = exportSet.stream().map(DataExportSuccessfulContentDTO::of).collect(Collectors.toSet());
        return new DataExportSuccessfulContentsDTO(contents);
    }
}
