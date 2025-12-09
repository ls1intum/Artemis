import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { take } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import dayjs from 'dayjs/esm';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';

describe('Attachment Service', () => {
    let httpMock: HttpTestingController;
    let service: AttachmentService;
    const resourceUrl = 'api/lecture/attachments';
    let expectedResult: any;
    let elemDefault: Attachment;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(AttachmentService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as HttpResponse<Attachment>;
        elemDefault = new Attachment();
        elemDefault.releaseDate = dayjs();
        elemDefault.link = 'attachments/lecture/4/Mein_Test_PDF4.pdf';
        elemDefault.linkUrl = 'api/core/files/attachments/lecture/4/Mein_Test_PDF4.pdf';
        elemDefault.name = 'testss';
        elemDefault.lecture = new Lecture();
        elemDefault.attachmentType = AttachmentType.FILE;
        elemDefault.uploadDate = dayjs();
        elemDefault.id = 1;
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it.each([new File([], 'testName.txt'), undefined])('should create an attachment in the database with file %s', async (file: File | undefined) => {
            const returnedFromService = Object.assign({}, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .update(1, elemDefault, file)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl + '/1',
                method: 'PUT',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should find an attachment in the database', async () => {
            const returnedFromService = Object.assign({}, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            const id = elemDefault.id!;
            service
                .find(id)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${id}`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should get all attachments by lectureId', async () => {
            const returnedFromService = [elemDefault];
            const expected = returnedFromService;
            const lectureId = 1;
            service
                .findAllByLectureId(lectureId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `api/lecture/lectures/${lectureId}/attachments`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).toEqual(expected);
        });

        it('should delete an attachment in the database', async () => {
            const returnedFromService = Object.assign({}, elemDefault);
            const attachmentId = elemDefault.id!;
            service
                .delete(attachmentId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: `${resourceUrl}/${attachmentId}`,
                method: 'DELETE',
            });
            req.flush(returnedFromService);
            expect(req.request.method).toBe('DELETE');
        });

        it('should convert attachment date from server', async () => {
            const results = service.convertAttachmentFromServer(elemDefault);
            expect(results).toEqual(elemDefault);
        });
    });

    describe('getAttachmentFile', () => {
        it('should retrieve a file as Blob for a given course and attachment ID', () => {
            const courseId = 1;
            const attachmentId = 100;
            const expectedBlob = new Blob(['dummy content'], { type: 'application/pdf' });

            service.getAttachmentFile(courseId, attachmentId).subscribe((resp) => {
                expect(resp).toEqual(expectedBlob);
            });

            const req = httpMock.expectOne({
                url: `api/core/files/courses/${courseId}/attachments/${attachmentId}`,
                method: 'GET',
            });
            expect(req.request.responseType).toBe('blob');
            req.flush(expectedBlob);
        });
    });
});
