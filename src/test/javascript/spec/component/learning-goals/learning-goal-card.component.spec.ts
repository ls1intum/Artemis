import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LearningGoalCardComponent } from 'app/course/learning-goals/learning-goal-card/learning-goal-card.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { LearningGoalDetailModalComponent } from 'app/course/learning-goals/learning-goal-detail-modal/learning-goal-detail-modal.component';
import { Component, Input } from '@angular/core';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LearningGoalProgress } from 'app/course/learning-goals/learning-goal-progress-dtos.model';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;

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
            declarations: [LearningGoalCardComponent, MockPipe(TranslatePipe), CircularProgressBarStubComponent],
            providers: [MockProvider(LectureUnitService), MockProvider(TranslateService), MockProvider(NgbModal)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalCardComponentFixture = TestBed.createComponent(LearningGoalCardComponent);
                learningGoalCardComponent = learningGoalCardComponentFixture.componentInstance;
                learningGoalCardComponent.ModalComponent = MockComponent(LearningGoalDetailModalComponent);
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        learningGoalCardComponentFixture.detectChanges();
        expect(learningGoalCardComponent).to.be.ok;
    });

    it('should display progress bar when progress is available', () => {
        const learningGoal = new LearningGoal();
        learningGoal.id = 1;
        const learningGoalProgress = new LearningGoalProgress();
        learningGoalProgress.learningGoalId = 1;
        learningGoalProgress.pointsAchievedByStudentInLearningGoal = 5;
        learningGoalProgress.totalPointsAchievableByStudentsInLearningGoal = 10;

        learningGoalCardComponent.learningGoal = learningGoal;
        learningGoalCardComponent.learningGoalProgress = learningGoalProgress;

        learningGoalCardComponentFixture.detectChanges();

        const circularProgress = learningGoalCardComponentFixture.debugElement.query(By.directive(CircularProgressBarStubComponent)).componentInstance;
        expect(circularProgress).to.be.ok;
        expect(circularProgress.progressInPercent).to.equal(50);
    });

    it('should not display progress bar when progress is not available', () => {
        const learningGoal = new LearningGoal();
        learningGoal.id = 1;
        learningGoalCardComponent.learningGoal = learningGoal;

        learningGoalCardComponentFixture.detectChanges();

        const circularProgress = learningGoalCardComponentFixture.debugElement.query(By.directive(CircularProgressBarStubComponent));
        expect(circularProgress).to.not.exist;
    });

    it('should open learning details modal when card is clicked', () => {
        const learningGoal = new LearningGoal();
        learningGoal.id = 1;
        learningGoalCardComponent.learningGoal = learningGoal;
        learningGoalCardComponentFixture.detectChanges();

        const card = learningGoalCardComponentFixture.debugElement.nativeElement.querySelector('.course-goal-card');
        const modalService = TestBed.inject(NgbModal);
        const openSpy = sinon.spy(modalService, 'open');
        card.click();
        expect(openSpy).to.have.been.called;
    });
});
