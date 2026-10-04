package de.tum.cit.aet.artemis.lecture.service;

public record AttachmentFileUpdateResult(boolean fileBytesChanged, boolean attachmentAdded, boolean attachmentRemoved, Integer oldVersion, Integer newVersion) {

    public static AttachmentFileUpdateResult unchanged(Integer version) {
        return new AttachmentFileUpdateResult(false, false, false, version, version);
    }

    public static AttachmentFileUpdateResult changed(Integer oldVersion, Integer newVersion) {
        return new AttachmentFileUpdateResult(true, false, false, oldVersion, newVersion);
    }

    public static AttachmentFileUpdateResult attachmentAdded(Integer newVersion) {
        return new AttachmentFileUpdateResult(false, true, false, null, newVersion);
    }
}
