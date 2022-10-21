import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync } from '@angular/core/testing';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { TextUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/textUnit.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';

describe('TextUnitService', () => {
    let service: TextUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: TextUnit;
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
        const returnedFromService = { ...elemDefault };
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    }));

    it('should create a TextUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(new TextUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should update a TextUnit', fakeAsync(() => {
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
});
