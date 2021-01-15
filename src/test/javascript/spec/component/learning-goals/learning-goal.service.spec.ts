import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { getTestBed, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { MockProvider } from 'ng-mocks';
import { take } from 'rxjs/operators';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { LearningGoal } from 'app/entities/learningGoal.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('LearningGoalService', () => {
    let testBed: TestBed;
    let learningGoalService: LearningGoalService;
    let httpTestingController: HttpTestingController;
    let defaultLearningGoal: LearningGoal;
    let defaultLearningGoalProgress: IndividualLearningGoalProgress;
    let expectedResultLearningGoal: any;
    let expectedResultLearningGoalProgress: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                MockProvider(LectureUnitService, {
                    convertDateArrayFromServerEntity<T extends LectureUnit>(res: T[]): T[] {
                        return res;
                    },
                    convertDateArrayFromClient<T extends LectureUnit>(lectureUnits: T[]): T[] {
                        return lectureUnits;
                    },
                }),
            ],
        });
        expectedResultLearningGoal = {} as HttpResponse<LearningGoal>;
        expectedResultLearningGoalProgress = {} as HttpResponse<IndividualLearningGoalProgress>;

        testBed = getTestBed();
        learningGoalService = testBed.get(LearningGoalService);
        httpTestingController = testBed.get(HttpTestingController);

        defaultLearningGoal = new LearningGoal();
        defaultLearningGoal.id = 0;
        defaultLearningGoal.title = 'title';
        defaultLearningGoal.description = 'description';

        defaultLearningGoalProgress = new IndividualLearningGoalProgress();
        defaultLearningGoalProgress.learningGoalId = 0;
        defaultLearningGoalProgress.learningGoalTitle = 'title';
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should find a LearningGoal', async () => {
        const returnedFromService = { ...defaultLearningGoal };
        learningGoalService
            .findById(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));
        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResultLearningGoal.body).to.deep.equal(defaultLearningGoal);
    });

    it('should get Progress for a LearningGoal', async () => {
        const returnedFromService = { ...defaultLearningGoalProgress };
        learningGoalService
            .getProgress(1, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoalProgress = resp));
        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(expectedResultLearningGoalProgress.body).to.deep.equal(defaultLearningGoalProgress);
    });

    it('should get all learning goals for a course', async () => {
        const returnedFromService = [{ ...defaultLearningGoalProgress }];
        let response: any = {} as HttpResponse<LearningGoal[]>;
        learningGoalService
            .getAllForCourse(1)
            .pipe(take(1))
            .subscribe((resp) => (response = resp));
        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(response.body).to.deep.equal([{ ...defaultLearningGoalProgress }]);
    });

    it('should create a LearningGoal', async () => {
        const returnedFromService = { ...defaultLearningGoal, id: 0 };
        const expected = { ...returnedFromService };
        learningGoalService
            .create(new LearningGoal(), 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));
        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(expectedResultLearningGoal.body).to.deep.equal(expected);
    });

    it('should update a LearningGoal', async () => {
        const returnedFromService = { ...defaultLearningGoal, title: 'Test' };
        const expected = { ...returnedFromService };
        learningGoalService
            .update(expected, 1)
            .pipe(take(1))
            .subscribe((resp) => (expectedResultLearningGoal = resp));
        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(expectedResultLearningGoal.body).to.deep.equal(expected);
    });
});
