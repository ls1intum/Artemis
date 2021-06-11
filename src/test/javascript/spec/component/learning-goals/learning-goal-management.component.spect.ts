import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalManagementComponent } from 'app/course/learning-goals/learning-goal-management/learning-goal-management.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ActivatedRoute } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { Component, Input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseLearningGoalProgress, CourseLectureUnitProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';
import * as Sentry from '@sentry/browser';
import * as _ from 'lodash';

chai.use(sinonChai);
const expect = chai.expect;

@Component({ selector: 'jhi-learning-goal-card', template: '<div><ng-content></ng-content></div>' })
class LearningGoalCardStubComponent {
    @Input() learningGoal: LearningGoal;
    @Input() learningGoalProgress: CourseLearningGoalProgress;
}

describe('LearningGoalManagementComponent', () => {
    let learningGoalManagementComponentFixture: ComponentFixture<LearningGoalManagementComponent>;
    let learningGoalManagementComponent: LearningGoalManagementComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [
                LearningGoalManagementComponent,
                LearningGoalCardStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(AlertComponent),
                MockComponent(FaIconComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
            ],
            providers: [
                MockProvider(JhiAlertService),
                MockProvider(LearningGoalService),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({
                                courseId: 1,
                            }),
                        },
                    },
                },
            ],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalManagementComponentFixture = TestBed.createComponent(LearningGoalManagementComponent);
                learningGoalManagementComponent = learningGoalManagementComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        learningGoalManagementComponentFixture.detectChanges();
        expect(learningGoalManagementComponent).to.be.ok;
    });

    it('should load learning goal and associated progress and display a card for each of them', () => {
        const learningGoalService = TestBed.inject(LearningGoalService);
        const learningGoal = new LearningGoal();
        const textUnit = new TextUnit();
        learningGoal.id = 1;
        learningGoal.description = 'test';
        learningGoal.lectureUnits = [textUnit];
        const courseLectureUnitProgress = new CourseLectureUnitProgress();
        courseLectureUnitProgress.lectureUnitId = 1;
        courseLectureUnitProgress.totalPointsAchievableByStudentsInLectureUnit = 10;
        const courseLearningGoalProgress = new CourseLearningGoalProgress();
        courseLearningGoalProgress.courseId = 1;
        courseLearningGoalProgress.learningGoalId = 1;
        courseLearningGoalProgress.learningGoalTitle = 'test';
        courseLearningGoalProgress.averagePointsAchievedByStudentInLearningGoal = 5;
        courseLearningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 10;
        courseLearningGoalProgress.progressInLectureUnits = [courseLectureUnitProgress];

        const learningGoalsOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [learningGoal, new LearningGoal()],
            status: 200,
        });
        const learningGoalProgressResponse: HttpResponse<CourseLearningGoalProgress> = new HttpResponse({
            body: courseLearningGoalProgress,
            status: 200,
        });
        const courseProgressParticipantScores = _.cloneDeep(courseLearningGoalProgress);
        courseProgressParticipantScores.averagePointsAchievedByStudentInLearningGoal = 1;
        const learningGoalProgressParticipantScoreResponse: HttpResponse<CourseLearningGoalProgress> = new HttpResponse({
            body: courseProgressParticipantScores,
            status: 200,
        });
        const getAllForCourseStub = sinon.stub(learningGoalService, 'getAllForCourse').returns(of(learningGoalsOfCourseResponse));
        const getProgressStub = sinon.stub(learningGoalService, 'getCourseProgress');
        getProgressStub.withArgs(sinon.match.any, sinon.match.any, false).returns(of(learningGoalProgressResponse));
        getProgressStub.withArgs(sinon.match.any, sinon.match.any, true).returns(of(learningGoalProgressParticipantScoreResponse));

        const captureExceptionSpy = sinon.spy(Sentry, 'captureException');

        learningGoalManagementComponentFixture.detectChanges();

        const learningGoalCards = learningGoalManagementComponentFixture.debugElement.queryAll(By.directive(LearningGoalCardStubComponent));
        expect(learningGoalCards).to.have.lengthOf(2);
        expect(getAllForCourseStub).to.have.been.calledOnce;
        expect(getProgressStub).to.have.callCount(4);
        expect(captureExceptionSpy).to.have.been.calledOnce;
    });
});
