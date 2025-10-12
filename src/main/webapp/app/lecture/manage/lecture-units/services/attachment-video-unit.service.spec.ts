import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { Attachment, AttachmentType } from 'app/lecture/shared/entities/attachment.model';
import { AttachmentVideoUnitService } from 'app/lecture/manage/lecture-units/services/attachment-video-unit.service';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { LectureUnitInformationDTO } from 'app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component';
import { AlertService } from 'app/shared/service/alert.service';

describe('AttachmentVideoUnitService', () => {
    let service: AttachmentVideoUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: AttachmentVideoUnit;
    let expectedResult: any;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(LectureUnitService, {
                    convertLectureUnitResponseDatesFromServer<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
                        return res;
                    },
                }),
                MockProvider(AlertService),
            ],
        });
        expectedResult = {} as HttpResponse<AttachmentVideoUnit>;
        service = TestBed.inject(AttachmentVideoUnitService);
        httpMock = TestBed.inject(HttpTestingController);
        alertService = TestBed.inject(AlertService);

        const attachment = new Attachment();
        attachment.id = 0;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.uploadDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file/test.pdf';

        elemDefault = new AttachmentVideoUnit();
        elemDefault.id = 0;
        elemDefault.description = 'lorem ipsum';
        elemDefault.attachment = attachment;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find a AttachmentVideoUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    }));

    it('should create an AttachmentVideoUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        const formData = new FormData();
        formData.append('file', new Blob(), 'filename.pdf');
        formData.append('attachment', objectToJsonBlob(elemDefault.attachment!));
        formData.append('attachmentVideoUnit', objectToJsonBlob(elemDefault));
        service
            .create(formData, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should update a AttachmentVideoUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, name: 'Test' };
        const expected = { ...returnedFromService };
        elemDefault.id = 42;
        const formData = new FormData();
        formData.append('attachment', objectToJsonBlob(elemDefault.attachment!));
        formData.append('attachmentVideoUnit', objectToJsonBlob(elemDefault));
        service
            .update(1, 42, formData)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should create AttachmentVideoUnits', fakeAsync(() => {
        const returnedAttachmentVideoUnits = [
            {
                type: 'attachment',
                id: 1,
                name: 'Unit 1',
                lecture: {
                    id: 1,
                    title: 'test',
                    course: {
                        id: 1,
                        title: 'test',
                    },
                },
                attachment: {
                    id: 1,
                    name: 'Unit1',
                    link: '/api/lecture/files/attachments/attachment-unit/235/Unit_1_.pdf',
                    version: 1,
                    attachmentType: 'FILE',
                },
            },
        ];
        let response: any;
        const returnedFromService = { ...returnedAttachmentVideoUnits };

        const expected = { ...returnedFromService };
        const filename = 'filename-on-server';
        const lectureUnitInformation: LectureUnitInformationDTO = {
            units: [
                {
                    unitName: 'Unit 1',
                    releaseDate: dayjs().year(2022).month(3).date(5),
                    startPage: 1,
                    endPage: 20,
                },
            ],
            numberOfPages: 0,
            removeSlidesCommaSeparatedKeyPhrases: '',
        };

        service
            .createUnits(1, filename, lectureUnitInformation)
            .pipe(take(1))
            .subscribe((resp) => (response = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(response.body).toEqual(expected);
    }));

    it('should get units information', fakeAsync(() => {
        const unit1 = {
            unitName: 'Unit 1',
            releaseDate: dayjs().year(2022).month(3).date(5),
            startPage: 1,
            endPage: 20,
        };

        let response: any;
        const returnedFromService = { lectureUnitDTOS: [unit1], numberOfPages: 20 };

        const expected = { ...returnedFromService };

        const filename = 'filename-on-server';
        service
            .getSplitUnitsData(1, filename)
            .pipe(take(1))
            .subscribe((resp) => (response = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(response.body).toEqual(expected);
    }));

    it('should get removed slides data', fakeAsync(() => {
        let response: any;
        const returnedFromService = [1, 2, 3];
        const expected = [...returnedFromService];

        const keyphrases = 'phrase1, phrase2';
        const filename = 'filename-on-server';
        service
            .getSlidesToRemove(1, filename, keyphrases)
            .pipe(take(1))
            .subscribe((resp) => (response = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(response.body).toEqual(expected);
    }));

    it('should upload slides', fakeAsync(() => {
        let response: any;
        const returnedFromService = 'filename-on-server';
        const expected = 'filename-on-server';
        const file = new File([''], 'testFile.pdf', { type: 'application/pdf' });

        service
            .uploadSlidesForProcessing(1, file)
            .pipe(take(1))
            .subscribe((resp) => (response = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);

        expect(response.body).toEqual(expected);
    }));
    it('create(): sets ngsw-bypass header and keepFilename=true', fakeAsync(() => {
        const formData = new FormData();
        formData.append('file', new Blob(), 'file.pdf');

        let resp: any;
        service
            .create(formData, 99)
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne((r) => r.method === 'POST' && r.url === 'api/lecture/lectures/99/attachment-video-units?keepFilename=true');
        expect(req.request.headers.get('ngsw-bypass')).toBe('true');
        req.flush({ id: 1 });
        expect(resp.body).toEqual({ id: 1 });
    }));

    it('update(): sets ngsw-bypass header and keepFilename=true (no notificationText)', fakeAsync(() => {
        const formData = new FormData();
        let resp: any;

        service
            .update(7, 42, formData)
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne((r) => r.method === 'PUT' && r.url === 'api/lecture/lectures/7/attachment-video-units/42?keepFilename=true');
        expect(req.request.headers.get('ngsw-bypass')).toBe('true');
        req.flush({ id: 42 });
        expect(resp.body).toEqual({ id: 42 });
    }));

    it('update(): appends notificationText when provided', fakeAsync(() => {
        const formData = new FormData();
        let resp: any;

        const notificationText = 'UpdatedVersion';
        service
            .update(7, 42, formData, notificationText)
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne(
            (r) => r.method === 'PUT' && r.url === `api/lecture/lectures/7/attachment-video-units/42?keepFilename=true&notificationText=${notificationText}`,
        );
        expect(req.request.headers.get('ngsw-bypass')).toBe('true');
        req.flush({ id: 42, note: notificationText });
        expect(resp.body).toEqual({ id: 42, note: notificationText });
    }));

    it('updateStudentVersion(): calls correct URL with ngsw-bypass header', fakeAsync(() => {
        const formData = new FormData();
        formData.append('file', new Blob(), 'student.pdf');
        let resp: any;

        service
            .updateStudentVersion(3, 11, formData)
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne((r) => r.method === 'PUT' && r.url === 'api/lecture/lectures/3/attachment-video-units/11/student-version');
        expect(req.request.headers.get('ngsw-bypass')).toBe('true');
        req.flush({ id: 11, ok: true });
        expect(resp.body).toEqual({ id: 11, ok: true });
    }));

    it('uploadSlidesForProcessing(): sends FormData with file field', fakeAsync(() => {
        const file = new File(['hello'], 'slides.pdf', { type: 'application/pdf' });
        let resp: any;

        service
            .uploadSlidesForProcessing(55, file)
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne((r) => r.method === 'POST' && r.url === 'api/lecture/lectures/55/attachment-video-units/upload');
        expect(req.request.body instanceof FormData).toBeTrue();
        const sentFormData = req.request.body as FormData;
        // Verify FormData contains "file" with our name
        const fileEntry = sentFormData.get('file') as File;
        expect(fileEntry).toBeTruthy();
        expect(fileEntry.name).toBe('slides.pdf');

        req.flush('filename-on-server');
        expect(resp.body).toBe('filename-on-server');
    }));

    it('getSlidesToRemove(): sets commaSeparatedKeyPhrases param', fakeAsync(() => {
        const lectureId = 12;
        const filename = 'deck.pdf';
        const keyPhrases = 'foo,bar,baz';

        let resp: any;
        service
            .getSlidesToRemove(lectureId, filename, keyPhrases)
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne(
            (r) =>
                r.method === 'GET' &&
                r.url === `api/lecture/lectures/${lectureId}/attachment-video-units/slides-to-remove/${filename}` &&
                r.params.get('commaSeparatedKeyPhrases') === keyPhrases,
        );
        req.flush([2, 5]);
        expect(resp.body).toEqual([2, 5]);
    }));

    it('createUnits(): posts DTO payload to correct URL', fakeAsync(() => {
        const dto: LectureUnitInformationDTO = {
            units: [{ unitName: 'U1', releaseDate: dayjs('2024-04-05'), startPage: 1, endPage: 10 }],
            numberOfPages: 10,
            removeSlidesCommaSeparatedKeyPhrases: 'x,y',
        };
        let resp: any;

        service
            .createUnits(66, 'server-fn.pdf', dto)
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne((r) => r.method === 'POST' && r.url === 'api/lecture/lectures/66/attachment-video-units/split/server-fn.pdf');
        expect(req.request.body).toEqual(dto);
        req.flush([{ id: 1 }]);
        expect(resp.body).toEqual([{ id: 1 }]);
    }));

    it('getSplitUnitsData(): hits correct URL and observes response', fakeAsync(() => {
        let resp: any;
        service
            .getSplitUnitsData(77, 'file.pdf')
            .pipe(take(1))
            .subscribe((r) => (resp = r));

        const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === 'api/lecture/lectures/77/attachment-video-units/data/file.pdf');
        req.flush({ lectureUnitDTOS: [], numberOfPages: 0 });
        expect(resp.body).toEqual({ lectureUnitDTOS: [], numberOfPages: 0 });
    }));

    describe('getAttachmentFile', () => {
        it('should retrieve a file as Blob for a given course and attachment video unit ID', () => {
            const courseId = 5;
            const attachmentVideoUnitId = 10;
            const expectedBlob = new Blob(['example data'], {
                type: 'application/pdf',
            });

            service.getAttachmentFile(courseId, attachmentVideoUnitId).subscribe((response) => {
                expect(response).toEqual(expectedBlob);
            });

            const req = httpMock.expectOne({
                url: `api/core/files/courses/${courseId}/attachment-units/${attachmentVideoUnitId}`,
                method: 'GET',
            });
            expect(req.request.responseType).toBe('blob');
            req.flush(expectedBlob);
        });
    });
    describe('startTranscription', () => {
        const lectureId = 7;
        const lectureUnitId = 13;
        const videoUrl = 'https://tum-live.de/stream/abc.m3u8';

        it('should POST to the correct URL with expected body and show success alert on 200', fakeAsync(() => {
            const successSpy = jest.spyOn(alertService, 'success');
            let completed = false;

            service
                .startTranscription(lectureId, lectureUnitId, videoUrl)
                .pipe(take(1))
                .subscribe(() => (completed = true));

            const req = httpMock.expectOne({
                method: 'POST',
                url: `/api/lecture/nebula/${lectureId}/lecture-unit/${lectureUnitId}/transcriber`,
            });

            // Verify request shape
            expect(req.request.method).toBe('POST');
            expect(req.request.responseType).toBe('text');
            expect(req.request.body).toEqual({
                videoUrl,
                lectureId,
                lectureUnitId,
            });

            // Simulate successful response
            req.flush('transcription started', { status: 200, statusText: 'OK' });

            expect(completed).toBeTrue();
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.attachmentVideoUnit.transcription.started');
        }));

        it('should show error alert and handle server errors gracefully', fakeAsync(() => {
            const errorSpy = jest.spyOn(alertService, 'error');
            let completed = false;

            service
                .startTranscription(lectureId, lectureUnitId, videoUrl)
                .pipe(take(1))
                .subscribe({
                    next: () => (completed = true),
                    complete: () => (completed = true),
                });

            const req = httpMock.expectOne({
                method: 'POST',
                url: `/api/lecture/nebula/${lectureId}/lecture-unit/${lectureUnitId}/transcriber`,
            });

            // Simulate server error
            req.flush('Internal error', { status: 500, statusText: 'Server Error' });

            expect(completed).toBeTrue();
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.attachmentVideoUnit.transcription.error');
        }));

        it('should send videoUrl verbatim even with special characters', fakeAsync(() => {
            const specialUrl = 'https://live.rbg.tum.de/w/test/26?video_only=1&token=a+b%2F=';
            const successSpy = jest.spyOn(alertService, 'success');

            service.startTranscription(lectureId, lectureUnitId, specialUrl).pipe(take(1)).subscribe();

            const req = httpMock.expectOne({
                method: 'POST',
                url: `/api/lecture/nebula/${lectureId}/lecture-unit/${lectureUnitId}/transcriber`,
            });

            // Body should contain the exact string we passed in
            expect(req.request.body).toEqual({
                videoUrl: specialUrl,
                lectureId,
                lectureUnitId,
            });

            req.flush('ok', { status: 200, statusText: 'OK' });
            expect(successSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.attachmentVideoUnit.transcription.started');
        }));

        it('should handle errors with missing message gracefully', fakeAsync(() => {
            const errorSpy = jest.spyOn(alertService, 'error');

            service.startTranscription(lectureId, lectureUnitId, videoUrl).pipe(take(1)).subscribe();

            const req = httpMock.expectOne({
                method: 'POST',
                url: `/api/lecture/nebula/${lectureId}/lecture-unit/${lectureUnitId}/transcriber`,
            });

            // Simulate error without message
            req.error(new ProgressEvent('Network error'));

            expect(errorSpy).toHaveBeenCalledWith('artemisApp.lectureUnit.attachmentVideoUnit.transcription.error');
        }));
    });
});
