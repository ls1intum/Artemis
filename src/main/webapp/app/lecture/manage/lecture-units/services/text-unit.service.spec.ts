import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { MockProvider } from 'ng-mocks';
import { TextUnitService } from 'app/lecture/manage/lecture-units/services/text-unit.service';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs/esm';

describe('TextUnitService', () => {
    let service: TextUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: TextUnit;
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
        expectedResult = {} as HttpResponse<TextUnit>;
        service = TestBed.inject(TextUnitService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new TextUnit();
        elemDefault.id = 0;
        elemDefault.content = 'Lorem Ipsum';
        elemDefault.releaseDate = undefined;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find a TextUnit', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    }));

    it('should create a TextUnit', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(new TextUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should update a TextUnit', fakeAsync(() => {
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
});
