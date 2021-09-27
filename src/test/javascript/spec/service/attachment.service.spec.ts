import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { Lecture } from 'app/entities/lecture.model';
import dayjs from 'dayjs';
import { AttachmentService } from 'app/lecture/attachment.service';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';

const expect = chai.expect;

describe('Attachment Service', () => {
    let injector: TestBed;
    let httpMock: HttpTestingController;
    let service: AttachmentService;
    const resourceUrl = SERVER_API_URL + 'api/attachments';
    let expectedResult: any;
    let elemDefault: Attachment;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        service = injector.get(AttachmentService);
        httpMock = injector.get(HttpTestingController);

        expectedResult = {} as HttpResponse<Attachment>;
        elemDefault = new Attachment();
        elemDefault.releaseDate = dayjs();
        elemDefault.link = '/api/files/attachments/lecture/4/Mein_Test_PDF4.pdf';
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
        it('should create an attachment in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .create(elemDefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl,
                method: 'POST',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should update an attachment in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .update(elemDefault)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl,
                method: 'PUT',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should find an attachment in the database', async () => {
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
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
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should invoke query', async () => {
            const returnedFromService = [elemDefault];
            const expected = returnedFromService;
            service
                .query({})
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({
                url: resourceUrl,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
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
                url: `api/lectures/${lectureId}/attachments`,
                method: 'GET',
            });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should delete an attachment in the database', async () => {
            const returnedFromService = { ...elemDefault };
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
            expect(req.request.method).to.equal('DELETE');
        });

        it('should convert attachment date from server', async () => {
            const results = service.convertAttachmentDateFromServer(elemDefault);
            expect(results).to.deep.equal(elemDefault);
        });
    });
});
