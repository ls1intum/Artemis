import { Attachment } from 'app/lecture/shared/entities/attachment.model';

/**
 * Whether the attachment is rendered as a PDF. The student version is what a student sees, so it decides before the
 * stored link and, as a last resort, the file name.
 */
export function isPdfAttachment(attachment: Attachment | undefined): boolean {
    const candidate = attachment?.studentVersion ?? attachment?.link ?? attachment?.name;
    return candidate?.toLowerCase().endsWith('.pdf') ?? false;
}
