import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { LearningGoalService } from 'app/course/competencies/learningGoal.service';
import { CourseLearningGoalProgress, LearningGoal, LearningGoalProgress, LearningGoalRelation } from 'app/entities/learningGoal.model';

describe('LearningGoalService', () => {
    let learningGoalService: LearningGoalService;
    let httpTestingController: HttpTestingController;
    let defaultLearningGoals: LearningGoal[];
    let defaultLearningGoalProgress: LearningGoalProgress;
    let defaultLearningGoalCourseProgress: CourseLearningGoalProgress;
    let expectedResultLearningGoal: any;
    let expectedResultLearningGoalProgress: any;
    let expectedResultLearningGoalCourseProgress: any;

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
        expectedResultLearningGoal = {} as HttpResponse<LearningGoal>;
        expectedResultLearningGoalProgress = {} as HttpResponse<LearningGoalProgress>;

        learningGoalService = TestBed.inject(LearningGoalService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultLearningGoals = [{ id: 0, title: 'title', description: 'description' } as LearningGoal];
        defaultLearningGoalProgress = { progress: 20, confidence: 50 } as LearningGoalProgress;
        defaultLearningGoalCourseProgress = { competencyId: 0, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseLearningGoalProgress;
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find a learning goal', fakeAsync(() => {
        const returnedFromService = [...defaultLearningGoals];
        learningGoalService
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(defaultLearningGoals);
    }));

    it('should find all learning goals', fakeAsync(() => {
        const returnedFromService = [...defaultLearningGoals];
        learningGoalService.getAllForCourse(1).subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(defaultLearningGoals);
    }));

    it('should find all prerequisites', fakeAsync(() => {
        const returnedFromService = [...defaultLearningGoals];
        learningGoalService.getAllPrerequisitesForCourse(1).subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(defaultLearningGoals);
    }));

    it('should get individual progress for a learning goal', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoalProgress };
        learningGoalService
            .getProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoalProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoalProgress.body).toEqual(defaultLearningGoalProgress);
    }));

    it('should get course progress for a learning goal', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoalCourseProgress };
        learningGoalService
            .getCourseProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoalCourseProgress = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoalCourseProgress.body).toEqual(defaultLearningGoalCourseProgress);
    }));

    it('should create a LearningGoal', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoals.first(), id: 0 };
        const expected = { ...returnedFromService };
        learningGoalService
            .create(new LearningGoal(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(expected);
    }));

    it('should update a LearningGoal', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoals.first(), title: 'Test' };
        const expected = { ...returnedFromService };
        learningGoalService
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(expected);
    }));

    it('should delete a LearningGoal', fakeAsync(() => {
        let result: any;
        learningGoalService.delete(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should add a LearningGoal relation', fakeAsync(() => {
        const returnedFromService = { tailCompetency: 1, headCompetency: 2, type: 'assumes' } as LearningGoalRelation;
        let result: any;
        learningGoalService
            .createLearningGoalRelation(1, 2, 'assumes', 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(result.body).toEqual(returnedFromService);
    }));

    it('should remove a LearningGoal relation', fakeAsync(() => {
        let result: any;
        learningGoalService.removeLearningGoalRelation(1, 1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));

    it('should add a prerequisite', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoals.first(), id: 0 };
        const expected = { ...returnedFromService };
        learningGoalService
            .addPrerequisite(defaultLearningGoals.first()!.id!, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(expected);
    }));

    it('should remove a prerequisite', fakeAsync(() => {
        let result: any;
        learningGoalService.removePrerequisite(1, 1).subscribe((resp) => (result = resp.ok));
        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
        tick();

        expect(result).toBeTrue();
    }));
});
