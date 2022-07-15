import { Component, Input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoalCardComponent } from 'app/course/learning-goals/learning-goal-card/learning-goal-card.component';
import { LearningGoalCourseDetailModalComponent } from 'app/course/learning-goals/learning-goal-course-detail-modal/learning-goal-course-detail-modal.component';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';

@Component({ selector: 'jhi-circular-progress-bar', template: '' })
class CircularProgressBarStubComponent {
    @Input()
    progressInPercent = 0;
    @Input()
    progressText = 'Completed';
}

describe('LearningGoalCardComponent', () => {
    let learningGoalCardComponentFixture: ComponentFixture<LearningGoalCardComponent>;
    let learningGoalCardComponent: LearningGoalCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [LearningGoalCardComponent, MockPipe(ArtemisTranslatePipe), CircularProgressBarStubComponent],
            providers: [MockProvider(LectureUnitService), MockProvider(TranslateService), MockProvider(NgbModal)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalCardComponentFixture = TestBed.createComponent(LearningGoalCardComponent);
                learningGoalCardComponent = learningGoalCardComponentFixture.componentInstance;
                learningGoalCardComponent.DetailModalComponent = MockComponent(LearningGoalDetailModalComponent);
                learningGoalCardComponent.CourseDetailModalComponent = MockComponent(LearningGoalCourseDetailModalComponent);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display progress bar when progress is available', () => {
        const learningGoal = new LearningGoal();
        learningGoal.id = 1;
        const learningGoalProgress = new IndividualLearningGoalProgress();
        learningGoalProgress.studentId = 1;
        learningGoalProgress.learningGoalId = 1;
        learningGoalProgress.pointsAchievedByStudentInLearningGoal = 5;
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 10;

        learningGoalCardComponent.learningGoal = learningGoal;
        learningGoalCardComponent.learningGoalProgress = learningGoalProgress;

        learningGoalCardComponentFixture.detectChanges();

        expect(learningGoalCardComponent.isProgressAvailable).toBeTrue();
        const circularProgress = learningGoalCardComponentFixture.debugElement.query(By.directive(CircularProgressBarStubComponent)).componentInstance;
        expect(circularProgress).toBeDefined();
        expect(circularProgress.progressInPercent).toEqual(50);
    });

    it('should not display progress bar when progress is not available', () => {
        const learningGoal = new LearningGoal();
        learningGoal.id = 1;
        learningGoalCardComponent.learningGoal = learningGoal;

        learningGoalCardComponentFixture.detectChanges();

        expect(learningGoalCardComponent.isProgressAvailable).toBeFalse();
        const circularProgress = learningGoalCardComponentFixture.debugElement.query(By.directive(CircularProgressBarStubComponent));
        expect(circularProgress).toBeNull();
    });

    it('should open learning details modal when card is clicked', () => {
        const learningGoal = new LearningGoal();
        learningGoal.id = 1;
        learningGoalCardComponent.learningGoal = learningGoal;
        learningGoalCardComponentFixture.detectChanges();

        const card = learningGoalCardComponentFixture.debugElement.nativeElement.querySelector('.course-goal-card');
        const modalService = TestBed.inject(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open');
        card.click();
        expect(openSpy).toHaveBeenCalledOnce();
    });
});
