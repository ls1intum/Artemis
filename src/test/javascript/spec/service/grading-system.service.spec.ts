import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { take } from 'rxjs/operators';
import { RouterTestingModule } from '@angular/router/testing';

describe('Grading System Service', () => {
    let injector: TestBed;
    let service: GradingSystemService;
    let httpMock: HttpTestingController;
    let elemDefault: GradingScale;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, RouterTestingModule],
        });
        injector = getTestBed();
        service = injector.get(GradingSystemService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new GradingScale();
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should find a grading scale for course', async () => {
        service
            .findGradingScaleForCourse(123)
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'GET' });
        request.flush(elemDefault);
    });

    it('should find a grading scale for exam', async () => {
        service
            .findGradingScaleForExam(123, 456)
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'GET' });
        request.flush(elemDefault);
    });

    it('should create a grading scale for course', async () => {
        service
            .createGradingScaleForCourse(123, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'POST' });
        request.flush(elemDefault);
    });

    it('should create a grading scale for exam', async () => {
        service
            .createGradingScaleForExam(123, 456, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'POST' });
        request.flush(elemDefault);
    });

    it('should update a grading scale for course', async () => {
        service
            .updateGradingScaleForCourse(123, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'PUT' });
        request.flush(elemDefault);
    });

    it('should update a grading scale for exam', async () => {
        service
            .updateGradingScaleForExam(123, 456, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'PUT' });
        request.flush(elemDefault);
    });

    it('should delete a grading scale for course', async () => {
        service.deleteGradingScaleForCourse(123).pipe(take(1)).subscribe();

        const request = httpMock.expectOne({ method: 'DELETE' });
        request.flush(elemDefault);
    });

    it('should delete a grading scale for exam', async () => {
        service.deleteGradingScaleForExam(123, 456).pipe(take(1)).subscribe();

        const request = httpMock.expectOne({ method: 'DELETE' });
        request.flush(elemDefault);
    });
});
