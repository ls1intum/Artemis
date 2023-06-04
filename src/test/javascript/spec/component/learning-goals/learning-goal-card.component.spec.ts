import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LearningGoalCardComponent } from 'app/course/learning-goals/learning-goal-card/learning-goal-card.component';
import { LearningGoal, LearningGoalProgress } from 'app/entities/learningGoal.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { LearningGoalRingsComponent } from 'app/course/learning-goals/learning-goal-rings/learning-goal-rings.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import dayjs from 'dayjs/esm';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { By } from '@angular/platform-browser';

describe('LearningGoalCardComponent', () => {
    let learningGoalCardComponentFixture: ComponentFixture<LearningGoalCardComponent>;
    let learningGoalCardComponent: LearningGoalCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [
                LearningGoalCardComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(LearningGoalRingsComponent),
                MockPipe(ArtemisTimeAgoPipe),
            ],
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

    it('should detect if due date is passed', () => {
        const learningGoalFuture = { dueDate: dayjs().add(1, 'days') } as LearningGoal;
        learningGoalCardComponent.learningGoal = learningGoalFuture;
        learningGoalCardComponentFixture.detectChanges();
        expect(learningGoalCardComponent.dueDatePassed).toBeFalse();

        const learningGoalPast = { dueDate: dayjs().subtract(1, 'days') } as LearningGoal;
        learningGoalCardComponent.learningGoal = learningGoalPast;
        learningGoalCardComponentFixture.detectChanges();
        expect(learningGoalCardComponent.dueDatePassed).toBeTrue();
    });

    it.each([
        { learningGoal: { dueDate: dayjs().add(1, 'days') } as LearningGoal, expectedBadge: 'bg-success' },
        { learningGoal: { dueDate: dayjs().subtract(1, 'days') } as LearningGoal, expectedBadge: 'bg-danger' },
    ])('should have [ngClass] resolve to correct date badge', ({ learningGoal, expectedBadge }) => {
        learningGoalCardComponent.learningGoal = learningGoal;
        learningGoalCardComponentFixture.detectChanges();
        const badge = learningGoalCardComponentFixture.debugElement.query(By.css('#date-badge'));
        expect(badge.attributes['ng-reflect-ng-class']).toBe(expectedBadge);
    });
});
