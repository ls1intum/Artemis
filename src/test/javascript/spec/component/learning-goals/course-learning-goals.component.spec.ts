import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslatePipe } from '@ngx-translate/core';
import { MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { IndividualLearningGoalProgress, IndividualLectureUnitProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { Component, Input } from '@angular/core';
import { CourseLearningGoalsComponent } from 'app/overview/course-learning-goals/course-learning-goals.component';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';

chai.use(sinonChai);
const expect = chai.expect;

@Component({ selector: 'jhi-learning-goal-card', template: '<div><ng-content></ng-content></div>' })
class LearningGoalCardStubComponent {
    @Input() learningGoal: LearningGoal;
    @Input() learningGoalProgress: IndividualLearningGoalProgress;
}

class MockActivatedRoute {
    parent: any;
    params: any;

    constructor(options: { parent?: any; params?: any }) {
        this.parent = options.parent;
        this.params = options.params;
    }
}

const mockActivatedRoute = new MockActivatedRoute({
    parent: new MockActivatedRoute({
        params: of({ courseId: '1' }),
    }),
});
describe('CourseLearningGoals', () => {
    let courseLearningGoalsComponentFixture: ComponentFixture<CourseLearningGoalsComponent>;
    let courseLearningGoalsComponent: CourseLearningGoalsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CourseLearningGoalsComponent, LearningGoalCardStubComponent, MockPipe(TranslatePipe)],
            providers: [
                MockProvider(JhiAlertService),
                MockProvider(LearningGoalService),
                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                courseLearningGoalsComponentFixture = TestBed.createComponent(CourseLearningGoalsComponent);
                courseLearningGoalsComponent = courseLearningGoalsComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        courseLearningGoalsComponentFixture.detectChanges();
        expect(courseLearningGoalsComponent).to.be.ok;
        expect(courseLearningGoalsComponent.courseId).to.equal(1);
    });

    it('should load learning goal and associated progress and display a card for each of them', () => {
        const learningGoalService = TestBed.inject(LearningGoalService);
        const learningGoal = new LearningGoal();
        const textUnit = new TextUnit();
        learningGoal.id = 1;
        learningGoal.description = 'test';
        learningGoal.lectureUnits = [textUnit];
        const learningUnitProgress = new IndividualLectureUnitProgress();
        learningUnitProgress.lectureUnitId = 1;
        learningUnitProgress.totalPointsAchievableByStudentsInLectureUnit = 10;
        const learningGoalProgress = new IndividualLearningGoalProgress();
        learningGoalProgress.learningGoalId = 1;
        learningGoalProgress.learningGoalTitle = 'test';
        learningGoalProgress.pointsAchievedByStudentInLearningGoal = 5;
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 10;
        learningGoalProgress.progressInLectureUnits = [learningUnitProgress];

        const learningGoalsOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [learningGoal, new LearningGoal()],
            status: 200,
        });
        const learningGoalProgressResponse: HttpResponse<IndividualLearningGoalProgress> = new HttpResponse({
            body: learningGoalProgress,
            status: 200,
        });

        const getAllForCourseStub = sinon.stub(learningGoalService, 'getAllForCourse').returns(of(learningGoalsOfCourseResponse));
        const getProgressStub = sinon.stub(learningGoalService, 'getProgress').returns(of(learningGoalProgressResponse));

        courseLearningGoalsComponentFixture.detectChanges();

        const learningGoalCards = courseLearningGoalsComponentFixture.debugElement.queryAll(By.directive(LearningGoalCardStubComponent));
        expect(learningGoalCards).to.have.lengthOf(2);
        expect(getAllForCourseStub).to.have.been.calledOnce;
        expect(getProgressStub).to.have.been.calledTwice;
    });
});
