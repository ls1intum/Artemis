import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { Attachment, AttachmentType } from 'app/entities/attachment.model';
import { AttachmentUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/attachmentUnit.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('AttachmentUnitService', () => {
    let injector: TestBed;
    let service: AttachmentUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: AttachmentUnit;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(LectureUnitService, {
                    convertDateFromServerResponse<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
                        return res;
                    },
                }),
            ],
        });
        expectedResult = {} as HttpResponse<AttachmentUnit>;
        injector = getTestBed();
        service = injector.get(AttachmentUnitService);
        httpMock = injector.get(HttpTestingController);

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
        expect(expectedResult.body).to.deep.equal(elemDefault);
    });

    it('should create an AttachmentUnit', async () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(new AttachmentUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).to.deep.equal(expected);
    });

    it('should update a AttachmentUnit', async () => {
        const returnedFromService = { ...elemDefault, name: 'Test' };
        const expected = { ...returnedFromService };
        service
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(expectedResult.body).to.deep.equal(expected);
    });
});
