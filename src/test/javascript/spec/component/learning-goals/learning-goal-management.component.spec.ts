import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalService } from 'app/course/learning-goals/learningGoal.service';
import { of } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalManagementComponent } from 'app/course/learning-goals/learning-goal-management/learning-goal-management.component';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { Component, Input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { RouterTestingModule } from '@angular/router/testing';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { CourseLearningGoalProgress, CourseLectureUnitProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';
import { cloneDeep } from 'lodash-es';
import * as Sentry from '@sentry/browser';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';

@Component({ selector: 'jhi-learning-goal-card', template: '<div><ng-content></ng-content></div>' })
class LearningGoalCardStubComponent {
    @Input() learningGoal: LearningGoal;
    @Input() learningGoalProgress: CourseLearningGoalProgress;
    @Input() isPrerequisite: Boolean;
}

describe('LearningGoalManagementComponent', () => {
    let learningGoalManagementComponentFixture: ComponentFixture<LearningGoalManagementComponent>;
    let learningGoalManagementComponent: LearningGoalManagementComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                LearningGoalManagementComponent,
                LearningGoalCardStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
            ],
            providers: [
                MockProvider(AccountService),
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

    afterEach(() => {
        jest.restoreAllMocks();
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
        const courseProgressParticipantScores = cloneDeep(courseLearningGoalProgress);
        courseProgressParticipantScores.averagePointsAchievedByStudentInLearningGoal = 1;
        const learningGoalProgressParticipantScoreResponse: HttpResponse<CourseLearningGoalProgress> = new HttpResponse({
            body: courseProgressParticipantScores,
            status: 200,
        });
        const prerequisitesOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [],
            status: 200,
        });

        jest.spyOn(learningGoalService, 'getLearningGoalRelations').mockReturnValue(of(new HttpResponse({ body: [], status: 200 })));
        const getAllForCourseSpy = jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(learningGoalsOfCourseResponse));
        const getProgressSpy = jest.spyOn(learningGoalService, 'getCourseProgress');
        jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressResponse)); // when useParticipantScoreTable = false
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressResponse)); // when useParticipantScoreTable = false
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressParticipantScoreResponse)); // when useParticipantScoreTable = true
        getProgressSpy.mockReturnValueOnce(of(learningGoalProgressParticipantScoreResponse)); // when useParticipantScoreTable = true

        const captureExceptionSpy = jest.spyOn(Sentry, 'captureException');

        learningGoalManagementComponentFixture.detectChanges();

        const learningGoalCards = learningGoalManagementComponentFixture.debugElement.queryAll(By.directive(LearningGoalCardStubComponent));
        expect(learningGoalCards).toHaveLength(2);
        expect(getAllForCourseSpy).toHaveBeenCalledOnce();
        expect(getProgressSpy).toHaveBeenCalledTimes(4);
        expect(learningGoalManagementComponent.learningGoals).toHaveLength(2);
        expect(learningGoalManagementComponent.learningGoalIdToLearningGoalCourseProgress.has(1)).toBeTrue();
        expect(learningGoalManagementComponent.learningGoalIdToLearningGoalCourseProgressUsingParticipantScoresTables.has(1)).toBeTrue();
        expect(captureExceptionSpy).toHaveBeenCalledOnce();
    });

    it('should load prerequisites and display a card for each of them', () => {
        const learningGoalService = TestBed.inject(LearningGoalService);
        const learningGoal = new LearningGoal();
        learningGoal.id = 1;
        learningGoal.description = 'test';

        const learningGoalsOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [],
            status: 200,
        });
        const prerequisitesOfCourseResponse: HttpResponse<LearningGoal[]> = new HttpResponse({
            body: [learningGoal, new LearningGoal()],
            status: 200,
        });

        jest.spyOn(learningGoalService, 'getAllForCourse').mockReturnValue(of(learningGoalsOfCourseResponse));
        const getAllPrerequisitesForCourseSpy = jest.spyOn(learningGoalService, 'getAllPrerequisitesForCourse').mockReturnValue(of(prerequisitesOfCourseResponse));

        learningGoalManagementComponentFixture.detectChanges();

        const prerequisiteCards = learningGoalManagementComponentFixture.debugElement.queryAll(By.directive(LearningGoalCardStubComponent));
        expect(prerequisiteCards).toHaveLength(2);
        expect(getAllPrerequisitesForCourseSpy).toHaveBeenCalledOnce();
        expect(learningGoalManagementComponent.prerequisites).toHaveLength(2);
    });
});
