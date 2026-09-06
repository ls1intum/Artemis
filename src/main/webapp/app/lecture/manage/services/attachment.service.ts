import { Injectable } from '@angular/core';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { convertDateFromClient, convertDateFromServer } from 'app/foundation/util/date.utils';
import { addPublicFilePrefix } from 'app/app.constants';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * Converts the dates of an attachment between the client and the server representation, and derives the URL its file is
 * served from. An attachment is only ever created, updated and deleted through the attachment video unit that owns it,
 * so this service issues no requests of its own; the lecture unit service uses it while mapping a lecture unit.
 */
@Injectable({ providedIn: 'root' })
export class AttachmentService {
    convertAttachmentDatesFromClient(attachment: Attachment): Attachment {
        // cloneWith already deep-clones its source, which preserves all nested properties of the attachment.
        return cloneWith(attachment, {
            releaseDate: convertDateFromClient(attachment.releaseDate),
            uploadDate: convertDateFromClient(attachment.uploadDate),
        });
    }

    convertAttachmentFromServer(attachment?: Attachment) {
        if (attachment) {
            attachment.releaseDate = convertDateFromServer(attachment.releaseDate);
            attachment.uploadDate = convertDateFromServer(attachment.uploadDate);
            attachment.linkUrl = addPublicFilePrefix(attachment.link);
        }
        return attachment;
    }
}
