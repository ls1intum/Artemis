import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { objectToJsonBlob } from 'app/utils/blob-util';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';

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

    it('should find a AttachmentUnit', async () => {
        const returnedFromService = { ...elemDefault };
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    });

    it('should create an AttachmentUnit', async () => {
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
    });

    it('should update a AttachmentUnit', async () => {
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
    });
});
