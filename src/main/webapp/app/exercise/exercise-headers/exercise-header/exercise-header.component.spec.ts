import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseHeaderComponent } from 'app/exercise/exercise-headers/exercise-header/exercise-header.component';
import { ExerciseHeaderActionsComponent } from 'app/exercise/exercise-headers/exercise-header-actions/exercise-header-actions.component';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { ParticipationModeToggleComponent } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { UMLDiagramType } from '@tumaet/apollon';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { RequestFeedbackButtonComponent } from 'app/core/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { StartPracticeModeButtonComponent } from 'app/core/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';

describe('ExerciseHeaderComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseHeaderComponent>;

    const submitCallback = vi.fn();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ExerciseHeaderComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(QuizExerciseService),
                MockProvider(AlertService),
                MockProvider(CourseExerciseService),
                MockProvider(ParticipationService),
                MockProvider(ProfileService),
            ],
        });

        // Mock child components of ExerciseHeaderComponent not under test
        TestBed.overrideComponent(ExerciseHeaderComponent, {
            remove: { imports: [ExerciseHeadersInformationComponent, ParticipationModeToggleComponent] },
            add: { imports: [MockComponent(ExerciseHeadersInformationComponent), MockComponent(ParticipationModeToggleComponent)] },
        });

        // Mock complex child imports of ExerciseHeaderActionsComponent to avoid deep dependency chains
        TestBed.overrideComponent(ExerciseHeaderActionsComponent, {
            remove: {
                imports: [TranslateDirective, ArtemisTranslatePipe, FeatureToggleDirective, RequestFeedbackButtonComponent, StartPracticeModeButtonComponent, CodeButtonComponent],
            },
            add: {
                imports: [
                    MockDirective(TranslateDirective),
                    MockPipe(ArtemisTranslatePipe),
                    MockDirective(FeatureToggleDirective),
                    MockComponent(RequestFeedbackButtonComponent),
                    MockComponent(StartPracticeModeButtonComponent),
                    MockComponent(CodeButtonComponent),
                ],
            },
        });

        fixture = TestBed.createComponent(ExerciseHeaderComponent);
    });

    it('should hide submit button when the due date has passed', () => {
        const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        exercise.id = 1;
        exercise.type = ExerciseType.MODELING;
        exercise.dueDate = dayjs().subtract(1, 'days');
        const participation = new StudentParticipation();
        participation.initializationDate = dayjs().subtract(2, 'days');

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseId', 5);
        fixture.componentRef.setInput('studentParticipation', participation);
        fixture.componentRef.setInput('onSubmitExercise', submitCallback);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('#submit-exercise'))).toBeNull();
    });

    it('should show submit button when no due date is set', () => {
        const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        exercise.id = 1;
        exercise.type = ExerciseType.MODELING;

        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseId', 5);
        fixture.componentRef.setInput('onSubmitExercise', submitCallback);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('#submit-exercise'))).not.toBeNull();
    });
});
