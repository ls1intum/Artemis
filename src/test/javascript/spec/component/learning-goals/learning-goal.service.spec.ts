import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { CourseLearningGoalProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';

describe('LearningGoalService', () => {
    let learningGoalService: LearningGoalService;
    let httpTestingController: HttpTestingController;
    let defaultLearningGoal: LearningGoal;
    let defaultLearningGoalProgress: IndividualLearningGoalProgress;
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
        expectedResultLearningGoalProgress = {} as HttpResponse<IndividualLearningGoalProgress>;

        learningGoalService = TestBed.inject(LearningGoalService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultLearningGoal = new LearningGoal();
        defaultLearningGoal.id = 0;
        defaultLearningGoal.title = 'title';
        defaultLearningGoal.description = 'description';

        defaultLearningGoalProgress = new IndividualLearningGoalProgress();
        defaultLearningGoalProgress.learningGoalId = 0;
        defaultLearningGoalProgress.learningGoalTitle = 'title';

        defaultLearningGoalCourseProgress = new CourseLearningGoalProgress();
        defaultLearningGoalCourseProgress.learningGoalId = 0;
        defaultLearningGoalCourseProgress.learningGoalTitle = 'title';
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find a learning goal', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoal };
        learningGoalService
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(defaultLearningGoal);
    }));

    it('should find all learning goals', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoal };
        learningGoalService.getAllForCourse(1).subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(defaultLearningGoal);
    }));

    it('should find all prerequisites', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoal };
        learningGoalService.getAllPrerequisitesForCourse(1).subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(defaultLearningGoal);
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
        const returnedFromService = { ...defaultLearningGoal, id: 0 };
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
        const returnedFromService = { ...defaultLearningGoal, title: 'Test' };
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

    it('should add a prerequisite', fakeAsync(() => {
        const returnedFromService = { ...defaultLearningGoal, id: 0 };
        const expected = { ...returnedFromService };
        learningGoalService
            .addPrerequisite(defaultLearningGoal.id!, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();

        expect(expectedResultLearningGoal.body).toEqual(expected);
    }));
});
