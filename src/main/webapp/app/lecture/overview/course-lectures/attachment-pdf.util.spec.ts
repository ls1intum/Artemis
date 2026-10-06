import { describe, expect, it } from 'vitest';
import { Attachment } from 'app/lecture/shared/entities/attachment.model';
import { isPdfAttachment } from 'app/lecture/overview/course-lectures/attachment-pdf.util';

describe('isPdfAttachment', () => {
    const attachment = (fields: Partial<Attachment>) => Object.assign(new Attachment(), fields);

    it('is false without an attachment', () => {
        expect(isPdfAttachment(undefined)).toBe(false);
    });

    it('recognizes a PDF link regardless of case', () => {
        expect(isPdfAttachment(attachment({ link: '/files/slides.PDF' }))).toBe(true);
    });

    it('lets the student version decide before the stored link', () => {
        expect(isPdfAttachment(attachment({ link: '/files/slides.docx', studentVersion: '/files/student.pdf' }))).toBe(true);
        expect(isPdfAttachment(attachment({ link: '/files/slides.pdf', studentVersion: '/files/student.docx' }))).toBe(false);
    });

    it('falls back to the name when there is neither a student version nor a link', () => {
        expect(isPdfAttachment(attachment({ name: 'slides.pdf' }))).toBe(true);
        expect(isPdfAttachment(attachment({ name: 'slides' }))).toBe(false);
    });
});
