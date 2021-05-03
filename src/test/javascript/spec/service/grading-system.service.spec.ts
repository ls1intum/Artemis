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
        const returnedFromService = Object.assign(
            {
                id: 123,
            },
            elemDefault,
        );
        service.findGradingScaleForCourse(123).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should find a grading scale for exam', async () => {
        const returnedFromService = Object.assign(
            {
                id: 456,
            },
            elemDefault,
        );
        service.findGradingScaleForExam(123, 456).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should create a grading scale for course', async () => {
        const returnedFromService = Object.assign(
            {
                id: 123,
            },
            elemDefault,
        );
        service.createGradingScaleForCourse(123, new GradingScale()).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should create a grading scale for exam', async () => {
        const returnedFromService = Object.assign(
            {
                id: 456,
            },
            elemDefault,
        );
        service.createGradingScaleForExam(123, 456, new GradingScale()).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should update a grading scale for course', async () => {
        const returnedFromService = Object.assign(
            {
                id: 123,
            },
            elemDefault,
        );
        service.updateGradingScaleForCourse(123, new GradingScale()).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should update a grading scale for exam', async () => {
        const returnedFromService = Object.assign(
            {
                id: 456,
            },
            elemDefault,
        );
        service.updateGradingScaleForExam(123, 456, new GradingScale()).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should delete a grading scale for course', async () => {
        const returnedFromService = Object.assign(
            {
                id: 123,
            },
            elemDefault,
        );
        service.deleteGradingScaleForCourse(123).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should delete a grading scale for exam', async () => {
        const returnedFromService = Object.assign(
            {
                id: 456,
            },
            elemDefault,
        );
        service.deleteGradingScaleForExam(123, 456).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush(JSON.stringify(returnedFromService));
    });
});
