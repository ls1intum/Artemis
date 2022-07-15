import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import dayjs from 'dayjs/esm';
import { VideoUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/videoUnit.service';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';

describe('VideoUnitService', () => {
    let service: VideoUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: VideoUnit;
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
        expectedResult = {} as HttpResponse<VideoUnit>;
        service = TestBed.inject(VideoUnitService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new VideoUnit();
        elemDefault.id = 0;
        elemDefault.description = 'Lorem Ipsum';
        elemDefault.source = 'test';
        elemDefault.releaseDate = undefined;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find a VideoUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(elemDefault);
    }));

    it('should create a VideoUnit', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(new VideoUnit(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResult = resp));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResult.body).toEqual(expected);
    }));

    it('should update a VideoUnit', fakeAsync(() => {
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
