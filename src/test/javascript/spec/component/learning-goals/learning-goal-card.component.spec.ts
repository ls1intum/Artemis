import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';

import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { LearningGoalCardComponent } from 'app/course/learning-goals/learning-goal-card/learning-goal-card.component';
import { LearningGoalRingsComponent } from 'app/course/learning-goals/learning-goal-rings/learning-goal-rings.component';
import { LearningGoal, LearningGoalProgress } from 'app/entities/learningGoal.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('LearningGoalCardComponent', () => {
    let learningGoalCardComponentFixture: ComponentFixture<LearningGoalCardComponent>;
    let learningGoalCardComponent: LearningGoalCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [LearningGoalCardComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockComponent(LearningGoalRingsComponent)],
            providers: [MockProvider(TranslateService)],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                learningGoalCardComponentFixture = TestBed.createComponent(LearningGoalCardComponent);
                learningGoalCardComponent = learningGoalCardComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should calculate correct progress, confidence and mastery', () => {
        learningGoalCardComponent.learningGoal = {
            id: 1,
            masteryThreshold: 80,
            userProgress: [
                {
                    progress: 45,
                    confidence: 60,
                } as LearningGoalProgress,
            ],
        } as LearningGoal;

        learningGoalCardComponentFixture.detectChanges();

        expect(learningGoalCardComponent.progress).toBe(45);
        expect(learningGoalCardComponent.confidence).toBe(75);
        expect(learningGoalCardComponent.mastery).toBe(65);
        expect(learningGoalCardComponent.isMastered).toBeFalse();
    });

    it('should display learning goal as mastered', () => {
        learningGoalCardComponent.learningGoal = {
            id: 1,
            masteryThreshold: 40,
            userProgress: [
                {
                    progress: 100,
                    confidence: 60,
                } as LearningGoalProgress,
            ],
        } as LearningGoal;

        learningGoalCardComponentFixture.detectChanges();

        expect(learningGoalCardComponent.progress).toBe(100);
        expect(learningGoalCardComponent.confidence).toBe(100);
        expect(learningGoalCardComponent.mastery).toBe(100);
        expect(learningGoalCardComponent.isMastered).toBeTrue();
    });
});
