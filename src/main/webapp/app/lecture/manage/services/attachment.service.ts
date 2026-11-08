import { Service } from '@angular/core';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { convertDateFromServer } from 'app/foundation/util/date.utils';
import { addPublicFilePrefix } from 'app/app.constants';

/**
 * Converts the dates of an attachment received from the server, and derives the URL its file is
 * served from. An attachment is only ever created, updated and deleted through the attachment video unit that owns it,
 * so this service issues no requests of its own; the lecture unit service uses it while mapping a lecture unit.
 */
@Service()
export class AttachmentService {
    convertAttachmentFromServer(attachment?: Attachment) {
        if (attachment) {
            attachment.releaseDate = convertDateFromServer(attachment.releaseDate);
            attachment.uploadDate = convertDateFromServer(attachment.uploadDate);
            attachment.linkUrl = addPublicFilePrefix(attachment.link);
        }
        return attachment;
    }
}
