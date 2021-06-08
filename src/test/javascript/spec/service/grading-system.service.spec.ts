import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { take } from 'rxjs/operators';
import { RouterTestingModule } from '@angular/router/testing';
import { GradeStep, GradeStepsDTO } from 'app/entities/grade-step.model';

describe('Grading System Service', () => {
    let injector: TestBed;
    let service: GradingSystemService;
    let httpMock: HttpTestingController;
    let elemDefault: GradingScale;

    const gradeStep2: GradeStep = {
        gradeName: 'Pass',
        lowerBoundPercentage: 40,
        upperBoundPercentage: 80,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: true,
    };
    const gradeStep1: GradeStep = {
        gradeName: 'Fail',
        lowerBoundPercentage: 0,
        upperBoundPercentage: 40,
        lowerBoundInclusive: true,
        upperBoundInclusive: false,
        isPassingGrade: false,
    };
    const gradeStep3: GradeStep = {
        gradeName: 'Excellent',
        lowerBoundPercentage: 80,
        upperBoundPercentage: 100,
        lowerBoundInclusive: true,
        upperBoundInclusive: true,
        isPassingGrade: true,
    };
    const gradeSteps = [gradeStep2, gradeStep3, gradeStep1];

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

    it('should find a grading scale for course', fakeAsync(() => {
        service
            .findGradingScaleForCourse(123)
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'GET' });
        request.flush(elemDefault);
        tick();
    }));

    it('should find a grading scale for exam', fakeAsync(() => {
        service
            .findGradingScaleForExam(123, 456)
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        httpMock.expectOne({ method: 'GET' }).flush(elemDefault);
        tick();
    }));

    it('should create a grading scale for course', fakeAsync(() => {
        service
            .createGradingScaleForCourse(123, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'POST' });
        request.flush(elemDefault);
        tick();
    }));

    it('should create a grading scale for exam', fakeAsync(() => {
        service
            .createGradingScaleForExam(123, 456, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        httpMock.expectOne({ method: 'POST' }).flush(elemDefault);
        tick();
    }));

    it('should update a grading scale for course', fakeAsync(() => {
        service
            .updateGradingScaleForCourse(123, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        const request = httpMock.expectOne({ method: 'PUT' });
        request.flush(elemDefault);
    }));

    it('should update a grading scale for exam', fakeAsync(() => {
        service
            .updateGradingScaleForExam(123, 456, new GradingScale())
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(elemDefault));

        httpMock.expectOne({ method: 'PUT' }).flush(elemDefault);
        tick();
    }));

    it('should delete a grading scale for course', fakeAsync(() => {
        service.deleteGradingScaleForCourse(123).pipe(take(1)).subscribe();

        const request = httpMock.expectOne({ method: 'DELETE' });
        request.flush(elemDefault);
        tick();
    }));

    it('should delete a grading scale for exam', fakeAsync(() => {
        service.deleteGradingScaleForExam(123, 456).pipe(take(1)).subscribe();

        httpMock.expectOne({ method: 'DELETE' }).flush(elemDefault);
        tick();
    }));

    it('should find all grade steps for exam', fakeAsync(() => {
        const dto: GradeStepsDTO = {
            gradeSteps,
            examTitle: 'Exam Title',
            gradeType: GradeType.GRADE,
        };

        service
            .findGradeStepsForExam(123, 456)
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(dto));

        httpMock.expectOne({ method: 'GET' }).flush(dto);
        tick();
    }));

    it('should match a grade step for exam to a percentage', fakeAsync(() => {
        service
            .matchPercentageToGradeStepForExam(123, 456, 70)
            .pipe(take(1))
            .subscribe((data) => expect(data.body).toEqual(gradeStep2));

        httpMock.expectOne({ method: 'GET' }).flush(gradeStep2);
        tick();
    }));

    it('should sort correctly', () => {
        service.sortGradeSteps(gradeSteps);

        expect(gradeSteps[0]).toEqual(gradeStep1);
        expect(gradeSteps[1]).toEqual(gradeStep2);
        expect(gradeSteps[2]).toEqual(gradeStep3);
    });

    it('should match grade percentage correctly', () => {
        expect(service.matchGradePercentage(gradeStep2, 40)).toEqual(true);
        expect(service.matchGradePercentage(gradeStep2, 70)).toEqual(true);
        expect(service.matchGradePercentage(gradeStep2, 80)).toEqual(false);
    });

    it('should find matching grade step', () => {
        expect(service.findMatchingGradeStep(gradeSteps, 30)).toEqual(gradeStep1);
        expect(service.findMatchingGradeStep(gradeSteps, 90)).toEqual(gradeStep3);
        expect(service.findMatchingGradeStep(gradeSteps, 150)).toEqual(undefined);
        expect(service.findMatchingGradeStep(gradeSteps, -10)).toEqual(undefined);
    });

    it('should find max grade correctly', () => {
        expect(service.maxGrade(gradeSteps)).toEqual(gradeStep3.gradeName);
        expect(service.maxGrade([])).toEqual('');
    });
});
