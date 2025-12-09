import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs/esm';
import { OnlineUnitService } from 'app/lecture/manage/lecture-units/services/online-unit.service';
import { OnlineUnit } from 'app/lecture/shared/entities/lecture-unit/onlineUnit.model';
import { OnlineResourceDTO } from 'app/lecture/manage/lecture-units/online-resource-dto.model';

describe('OnlineUnitService', () => {
    let service: OnlineUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: OnlineUnit;
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    }));

    it('should create a OnlineUnit', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(new OnlineUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should update a OnlineUnit', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { name: 'Test' });
        const expected = Object.assign({}, returnedFromService);
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
