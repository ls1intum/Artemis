import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { OnlineResourceDTO } from 'app/lecture/lecture-unit/lecture-unit-management/online-resource-dto.model';
import { OnlineUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/onlineUnit.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';

describe('OnlineUnitService', () => {
    let service: OnlineUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: OnlineUnit;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(LectureUnitService, {
                    convertLectureUnitResponseDatesFromServer<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
                        if (res.body) {
                            res.body.releaseDate = res.body.releaseDate ? dayjs(res.body.releaseDate) : undefined;
                        }
                        return res;
                    },
                }),
            ],
        });
        expectedResult = {} as HttpResponse<OnlineUnit>;
        service = TestBed.inject(OnlineUnitService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new OnlineUnit();
        elemDefault.id = 0;
        elemDefault.description = 'Lorem Ipsum';
        elemDefault.source = 'test';
        elemDefault.releaseDate = undefined;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find a OnlineUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    }));

    it('should create a OnlineUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(new OnlineUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should update a OnlineUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, name: 'Test' };
        const expected = { ...returnedFromService };
        service
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should send request to get online resource', async () => {
        const dto = new OnlineResourceDTO();
        dto.url = 'https://www.example.com';
        service.getOnlineResource(dto.url).subscribe((resp) => expect(resp.body!.url).toBe(dto.url));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(dto);
    });
});
