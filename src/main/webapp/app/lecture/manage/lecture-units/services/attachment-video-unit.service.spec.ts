import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnitService, withFileName } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';

async function readJsonPart(formData: FormData, name: string): Promise<any> {
    const part = formData.get(name) as Blob;
    expect(part.type).toBe('application/json');
    return JSON.parse(await part.text());
}

describe('AttachmentVideoUnitService', () => {
    let service: AttachmentVideoUnitService;
    let httpMock: HttpTestingController;
    let unit: AttachmentVideoUnit;
    let attachment: Attachment;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(AttachmentVideoUnitService);
        httpMock = TestBed.inject(HttpTestingController);

        attachment = new Attachment();
        attachment.id = 3;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs('2010-04-05T10:00:00Z');
        attachment.name = 'test';
        attachment.link = '/path/to/file/test.pdf';
        attachment.linkUrl = 'https://client-only';

        unit = new AttachmentVideoUnit();
        unit.id = 42;
        unit.name = 'Unit';
        unit.description = 'lorem ipsum';
        unit.releaseDate = dayjs('2010-04-05T10:00:00Z');
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('finds a unit and converts it to the lecture unit class', () => {
        let result: AttachmentVideoUnit | undefined;
        service.findById(42, 7).subscribe((r) => (result = r));

        httpMock.expectOne({ method: 'GET', url: '/api/lecture/lectures/7/attachment-video-units/42' }).flush({
            id: 42,
            type: 'attachment',
            releaseDate: '2010-04-05T10:00:00Z',
            attachment: { id: 3, link: 'attachments/test.pdf', releaseDate: '2010-04-05T10:00:00Z' },
        });

        expect(result).toBeInstanceOf(AttachmentVideoUnit);
        expect(dayjs.isDayjs(result!.releaseDate)).toBe(true);
        expect(dayjs.isDayjs(result!.attachment!.releaseDate)).toBe(true);
        expect(result!.attachment!.linkUrl).toContain('attachments/test.pdf');
    });

    it('creates a unit with its file, keeps the file name and bypasses the service worker', async () => {
        const file = new File(['pdf'], 'slides.pdf', { type: 'application/pdf' });
        let result: AttachmentVideoUnit | undefined;
        service.create(7, unit, attachment, file).subscribe((r) => (result = r));

        const req = httpMock.expectOne({ method: 'POST', url: '/api/lecture/lectures/7/attachment-video-units?keepFilename=true' });
        expect(req.request.headers.get('ngsw-bypass')).toBe('true');
        const body = req.request.body as FormData;
        expect((body.get('file') as File).name).toBe('slides.pdf');
        expect((await readJsonPart(body, 'attachmentVideoUnit')).name).toBe('Unit');
        const sentAttachment = await readJsonPart(body, 'attachment');
        expect(sentAttachment.name).toBe('test');
        expect(sentAttachment.linkUrl).toBeUndefined();
        expect(sentAttachment.releaseDate).toBe('2010-04-05T10:00:00.000Z');
        req.flush({ id: 1, type: 'attachment' });

        expect(result).toBeInstanceOf(AttachmentVideoUnit);
    });

    it('creates a video-only unit without file and attachment parts', () => {
        service.create(7, unit).subscribe();

        const req = httpMock.expectOne({ method: 'POST', url: '/api/lecture/lectures/7/attachment-video-units?keepFilename=true' });
        const body = req.request.body as FormData;
        expect(body.has('file')).toBe(false);
        expect(body.has('attachment')).toBe(false);
        expect(body.has('attachmentVideoUnit')).toBe(true);
        req.flush({ id: 1, type: 'attachment' });
    });

    it('updates a unit without a notification text', async () => {
        let result: AttachmentVideoUnit | undefined;
        service.update(7, 42, unit).subscribe((r) => (result = r));

        const req = httpMock.expectOne({ method: 'PUT', url: '/api/lecture/lectures/7/attachment-video-units/42?keepFilename=true' });
        expect(req.request.headers.get('ngsw-bypass')).toBe('true');
        const body = req.request.body as FormData;
        expect(body.has('file')).toBe(false);
        expect(body.has('studentVersion')).toBe(false);
        expect(body.has('hiddenPages')).toBe(false);
        expect(body.has('pageOrder')).toBe(false);
        expect((await readJsonPart(body, 'attachmentVideoUnit')).description).toBe('lorem ipsum');
        req.flush({ id: 42, name: 'Test', type: 'attachment' });

        expect(result!.name).toBe('Test');
    });

    it('encodes the notification text and sends an empty one, which also notifies', () => {
        service.update(7, 42, unit, { notificationText: 'Q&A #2' }).subscribe();
        httpMock
            .expectOne({ method: 'PUT', url: '/api/lecture/lectures/7/attachment-video-units/42?keepFilename=true&notificationText=Q%26A+%232' })
            .flush({ id: 42, type: 'attachment' });

        service.update(7, 42, unit, { notificationText: '' }).subscribe();
        httpMock.expectOne({ method: 'PUT', url: '/api/lecture/lectures/7/attachment-video-units/42?keepFilename=true&notificationText=' }).flush({ id: 42, type: 'attachment' });
    });

    it('sends the editor parts of an update under their part names', async () => {
        const file = new File(['pdf'], 'slides.pdf', { type: 'application/pdf' });
        const studentVersion = new File(['student'], 'slides_student.pdf', { type: 'application/pdf' });
        service
            .update(7, 42, unit, {
                attachment,
                file,
                studentVersion,
                hiddenPages: [{ slideId: 's1', date: '2030-01-01T00:00:00.000Z', exerciseId: 5 }],
                pageOrder: [{ slideId: 's1', order: 1 }],
            })
            .subscribe();

        const req = httpMock.expectOne({ method: 'PUT', url: '/api/lecture/lectures/7/attachment-video-units/42?keepFilename=true' });
        const body = req.request.body as FormData;
        expect((body.get('file') as File).name).toBe('slides.pdf');
        expect((body.get('studentVersion') as File).name).toBe('slides_student.pdf');
        expect(await readJsonPart(body, 'hiddenPages')).toEqual([{ slideId: 's1', date: '2030-01-01T00:00:00.000Z', exerciseId: 5 }]);
        expect(await readJsonPart(body, 'pageOrder')).toEqual([{ slideId: 's1', order: 1 }]);
        expect((await readJsonPart(body, 'attachment')).id).toBe(3);
        req.flush({ id: 42, type: 'attachment' });
    });

    describe('getAttachmentFile', () => {
        it('should retrieve a file as Blob for a given course and attachment video unit ID', () => {
            const expectedBlob = new Blob(['example data'], { type: 'application/pdf' });
            let result: Blob | undefined;

            service.getAttachmentFile(5, 10).subscribe((response) => (result = response));

            const req = httpMock.expectOne({ url: 'api/core/files/courses/5/attachment-video-units/10', method: 'GET' });
            expect(req.request.responseType).toBe('blob');
            req.flush(expectedBlob);
            expect(result).toEqual(expectedBlob);
        });
    });

    describe('getPlaylistUrl', () => {
        it('should return playlist URL on success', () => {
            const playlistUrl = 'https://stream.tum-live.de/video.m3u8?jwt=signed';
            let result: string | undefined;

            service.getPlaylistUrl(7).subscribe((url) => (result = url));

            const req = httpMock.expectOne({ method: 'GET', url: '/api/lecture/lecture-units/7/playlist' });
            expect(req.request.responseType).toBe('text');
            req.flush(playlistUrl);

            expect(result).toBe(playlistUrl);
        });

        it('should return undefined on error', () => {
            let result: string | undefined = 'initial';

            service.getPlaylistUrl(7).subscribe((url) => (result = url));

            httpMock.expectOne({ method: 'GET', url: '/api/lecture/lecture-units/7/playlist' }).flush('Not found', { status: 404, statusText: 'Not Found' });

            expect(result).toBeUndefined();
        });
    });

    describe('createAttachmentVideoUnitFromFile', () => {
        function createFrom(fileName: string) {
            const file = new File(['content'], fileName, { type: 'application/pdf' });
            let result: AttachmentVideoUnit | undefined;
            service.createAttachmentVideoUnitFromFile(42, file).subscribe((r) => (result = r));
            const req = httpMock.expectOne({ method: 'POST', url: '/api/lecture/lectures/42/attachment-video-units?keepFilename=true' });
            return { file, req, result: () => result };
        }

        it('should create a unit named after the file with the file attached', async () => {
            const { file, req, result } = createFrom('My_Lecture-Slides.pdf');

            const body = req.request.body as FormData;
            expect(body.get('file')).toBe(file);
            expect((await readJsonPart(body, 'attachmentVideoUnit')).name).toBe('My Lecture Slides');
            const sentAttachment = await readJsonPart(body, 'attachment');
            expect(sentAttachment.name).toBe('My Lecture Slides');
            expect(sentAttachment.attachmentType).toBe('FILE');
            req.flush({ id: 1, name: 'My Lecture Slides', type: 'attachment' });
            expect(result()!.name).toBe('My Lecture Slides');
        });

        it('should handle filename with uppercase PDF extension', async () => {
            const { req } = createFrom('Document.PDF');
            expect((await readJsonPart(req.request.body as FormData, 'attachmentVideoUnit')).name).toBe('Document');
            req.flush({ id: 2, type: 'attachment' });
        });

        it('should trim whitespace from the unit name', async () => {
            const { req } = createFrom('  spaced_name  .pdf');
            expect((await readJsonPart(req.request.body as FormData, 'attachmentVideoUnit')).name).toBe('spaced name');
            req.flush({ id: 4, type: 'attachment' });
        });

        it('should release the unit 15 minutes from now', async () => {
            const before = dayjs();
            const { req } = createFrom('test.pdf');
            const releaseDate = dayjs((await readJsonPart(req.request.body as FormData, 'attachmentVideoUnit')).releaseDate);
            expect(releaseDate.diff(before, 'minute', true)).toBeGreaterThan(14.9);
            expect(releaseDate.diff(before, 'minute', true)).toBeLessThan(15.1);
            req.flush({ id: 5, type: 'attachment' });
        });
    });

    describe('withFileName', () => {
        it('keeps a file that has the wanted name or no wanted name', () => {
            const file = new File(['a'], 'a.pdf', { type: 'application/pdf' });
            expect(withFileName(file, 'a.pdf')).toBe(file);
            expect(withFileName(file)).toBe(file);
        });

        it('renames a file with its content and type', async () => {
            const renamed = withFileName(new File(['a'], 'a.pdf', { type: 'application/pdf' }), 'b.pdf');
            expect(renamed.name).toBe('b.pdf');
            expect(renamed.type).toBe('application/pdf');
            expect(await renamed.text()).toBe('a');
        });
    });
});
