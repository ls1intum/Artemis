import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency, CompetencyProgress, CompetencyRelation, CourseCompetencyProgress } from 'app/entities/competency.model';

describe('CompetencyService', () => {
    let competencyService: CompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultCompetencies: Competency[];
    let defaultCompetencyProgress: CompetencyProgress;
    let defaultCompetencyCourseProgress: CourseCompetencyProgress;
    let expectedResultCompetency: any;
    let expectedResultCompetencyProgress: any;
    let expectedResultCompetencyCourseProgress: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(LectureUnitService, {
                    convertLectureUnitArrayDatesFromServer<T extends LectureUnit>(res: T[]): T[] {
                        return res;
                    },
                    convertLectureUnitArrayDatesFromClient<T extends LectureUnit>(lectureUnits: T[]): T[] {
                        return lectureUnits;
                    },
                }),
            ],
        });
        expectedResultCompetency = {} as HttpResponse<Competency>;
        expectedResultCompetencyProgress = {} as HttpResponse<CompetencyProgress>;

        competencyService = TestBed.inject(CompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultCompetencies = [{ id: 0, title: 'title', description: 'description' } as Competency];
        defaultCompetencyProgress = { progress: 20, confidence: 50 } as CompetencyProgress;
        defaultCompetencyCourseProgress = { competencyId: 0, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseCompetencyProgress;
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find a competency', fakeAsync(() => {
        const returnedFromService = [...defaultCompetencies];
        competencyService
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultCompetencies);
    }));

    it('should find all competencies', fakeAsync(() => {
        const returnedFromService = [...defaultCompetencies];
        competencyService.getAllForCourse(1).subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultCompetencies);
    }));

    it('should find all prerequisites', fakeAsync(() => {
        const returnedFromService = [...defaultCompetencies];
        competencyService.getAllPrerequisitesForCourse(1).subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(defaultCompetencies);
    }));

    it('should get individual progress for a competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencyProgress };
        competencyService
            .getProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetencyProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetencyProgress.body).toEqual(defaultCompetencyProgress);
    }));

    it('should get course progress for a competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencyCourseProgress };
        competencyService
            .getCourseProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetencyCourseProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetencyCourseProgress.body).toEqual(defaultCompetencyCourseProgress);
    }));

    it('should create a Competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencies.first(), id: 0 };
        const expected = { ...returnedFromService };
        competencyService
            .create(new Competency(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(expected);
    }));

    it('should update a Competency', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencies.first(), title: 'Test' };
        const expected = { ...returnedFromService };
        competencyService
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(expected);
    }));

    it('should delete a Competency', fakeAsync(() => {
        let result: any;
        competencyService.delete(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should add a Competency relation', fakeAsync(() => {
        const returnedFromService = { tailCompetency: 1, headCompetency: 2, type: 'assumes' } as CompetencyRelation;
        let result: any;
        competencyService
            .createCompetencyRelation(1, 2, 'assumes', 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(result.body).toEqual(returnedFromService);
    }));

    it('should remove a Competency relation', fakeAsync(() => {
        let result: any;
        competencyService.removeCompetencyRelation(1, 1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should add a prerequisite', fakeAsync(() => {
        const returnedFromService = { ...defaultCompetencies.first(), id: 0 };
        const expected = { ...returnedFromService };
        competencyService
            .addPrerequisite(defaultCompetencies.first()!.id!, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultCompetency.body).toEqual(expected);
    }));

    it('should remove a prerequisite', fakeAsync(() => {
        let result: any;
        competencyService.removePrerequisite(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));
});
