import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs/esm';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { objectToJsonBlob } from 'app/utils/blob-util';
import { LectureUnitInformationDTO } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-units/attachment-units.component';

describe('AttachmentUnitService', () => {
    let service: AttachmentUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: AttachmentUnit;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(LectureUnitService, {
                    convertLectureUnitResponseDatesFromServer<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
                        return res;
                    },
                }),
            ],
        });
        expectedResult = {} as HttpResponse<AttachmentUnit>;
        service = TestBed.inject(AttachmentUnitService);
        httpMock = TestBed.inject(HttpTestingController);

        const attachment = new Attachment();
        attachment.id = 0;
        attachment.version = 1;
        attachment.attachmentType = AttachmentType.FILE;
        attachment.releaseDate = dayjs().year(2010).month(3).date(5);
        attachment.uploadDate = dayjs().year(2010).month(3).date(5);
        attachment.name = 'test';
        attachment.link = '/path/to/file/test.pdf';

        elemDefault = new AttachmentUnit();
        elemDefault.id = 0;
        elemDefault.description = 'lorem ipsum';
        elemDefault.attachment = attachment;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find a AttachmentUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    }));

    it('should create an AttachmentUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        const formData = new FormData();
        formData.append('file', new Blob(), 'filename.pdf');
        formData.append('attachment', objectToJsonBlob(elemDefault.attachment!));
        formData.append('attachmentUnit', objectToJsonBlob(elemDefault));
        service
            .create(formData, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should update a AttachmentUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, name: 'Test' };
        const expected = { ...returnedFromService };
        elemDefault.id = 42;
        const formData = new FormData();
        formData.append('attachment', objectToJsonBlob(elemDefault.attachment!));
        formData.append('attachmentUnit', objectToJsonBlob(elemDefault));
        service
            .update(1, 42, formData)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should create AttachmentUnits', fakeAsync(() => {
        const returnedAttachmentUnits = [
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
                    link: '/api/files/attachments/attachment-unit/235/Unit_1_.pdf',
                    version: 1,
                    attachmentType: 'FILE',
                },
            },
        ];
        let response: any;
        const returnedFromService = { ...returnedAttachmentUnits };

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
});
