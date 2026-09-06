import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';

describe('Attachment Service', () => {
    let service: AttachmentService;
    let attachment: Attachment;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(AttachmentService);

        attachment = new Attachment();
        attachment.id = 1;
        attachment.name = 'testss';
        attachment.attachmentType = AttachmentType.FILE;
        attachment.link = 'attachments/attachment-unit/4/Mein_Test_PDF4.pdf';
        attachment.releaseDate = dayjs();
        attachment.uploadDate = dayjs();
    });

    it('should convert the dates to their server representation without touching the original', () => {
        const converted = service.convertAttachmentDatesFromClient(attachment);

        expect(converted.releaseDate).toEqual(attachment.releaseDate!.toJSON());
        expect(converted.uploadDate).toEqual(attachment.uploadDate!.toJSON());
        expect(dayjs.isDayjs(attachment.releaseDate)).toBe(true);
        expect(dayjs.isDayjs(attachment.uploadDate)).toBe(true);
    });

    it('should convert the dates from the server and derive the public file url', () => {
        const converted = service.convertAttachmentFromServer(attachment)!;

        expect(dayjs.isDayjs(converted.releaseDate)).toBe(true);
        expect(dayjs.isDayjs(converted.uploadDate)).toBe(true);
        expect(converted.linkUrl).toBe('api/core/files/attachments/attachment-unit/4/Mein_Test_PDF4.pdf');
    });

    it('should tolerate a missing attachment', () => {
        expect(service.convertAttachmentFromServer(undefined)).toBeUndefined();
    });
});
