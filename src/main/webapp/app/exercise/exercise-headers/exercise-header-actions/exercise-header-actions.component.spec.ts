import { describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { UMLDiagramType } from '@tumaet/apollon';
import { ExerciseHeaderActionsComponent } from 'app/exercise/exercise-headers/exercise-header-actions/exercise-header-actions.component';
import { RequestFeedbackButtonComponent } from 'app/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { StartPracticeModeButtonComponent } from 'app/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

describe('ExerciseHeaderActionsComponent', () => {
    let fixture: ComponentFixture<ExerciseHeaderActionsComponent>;

    function withCourse(exercise: Exercise, athenaFormativeFeedbackEnabled: boolean): Exercise {
        const course = new Course();
        course.athenaFormativeFeedbackEnabled = athenaFormativeFeedbackEnabled;
        exercise.course = course;
        return exercise;
    }

    function manualAssessmentProgrammingExercise(): ProgrammingExercise {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
        return exercise;
    }

    function createComponent(exercise: Exercise, options: { athenaEnabled?: boolean; examMode?: boolean; llmAccepted?: boolean } = {}) {
        const { athenaEnabled = true, examMode = false, llmAccepted = true } = options;

        const accountService = new MockAccountService();
        if (llmAccepted) {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
        }

        TestBed.configureTestingModule({
            imports: [ExerciseHeaderActionsComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(QuizExerciseService),
                MockProvider(AlertService),
                MockProvider(CourseExerciseService),
                MockProvider(ParticipationService),
                { provide: ProfileService, useValue: { isModuleFeatureActive: () => athenaEnabled } },
                { provide: AccountService, useValue: accountService },
            ],
        });

        // Mock complex child imports to avoid deep dependency chains unrelated to the AI feedback popover logic
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

        fixture = TestBed.createComponent(ExerciseHeaderActionsComponent);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('examMode', examMode);
        fixture.detectChanges();
        return fixture;
    }

    describe('showFeedbackPopover', () => {
        it.each([
            ['PROGRAMMING', manualAssessmentProgrammingExercise(), true],
            ['TEXT', new TextExercise(undefined, undefined), true],
            ['MODELING', new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined), true],
            ['QUIZ', new QuizExercise(undefined, undefined), false],
            ['FILE_UPLOAD', new FileUploadExercise(undefined, undefined), false],
        ])('should only offer the AI feedback popover for Athena-supported exercise types (%s)', (_exerciseTypeName: string, exercise: Exercise, expected: boolean) => {
            createComponent(withCourse(exercise, true));

            expect(fixture.componentInstance.showFeedbackPopover()).toBe(expected);
        });

        it('should not show the popover when the course has not enabled formative feedback requests', () => {
            createComponent(withCourse(manualAssessmentProgrammingExercise(), false));

            expect(fixture.componentInstance.showFeedbackPopover()).toBe(false);
        });

        it('should not show the popover when the Athena module is not active', () => {
            createComponent(withCourse(manualAssessmentProgrammingExercise(), true), { athenaEnabled: false });

            expect(fixture.componentInstance.showFeedbackPopover()).toBe(false);
        });

        it('should not show the popover in exam mode', () => {
            createComponent(withCourse(manualAssessmentProgrammingExercise(), true), { examMode: true });

            expect(fixture.componentInstance.showFeedbackPopover()).toBe(false);
        });

        it('should not show the popover when the user has not accepted AI feedback usage', () => {
            createComponent(withCourse(manualAssessmentProgrammingExercise(), true), { llmAccepted: false });

            expect(fixture.componentInstance.showFeedbackPopover()).toBe(false);
        });

        it('should not show the popover for a programming exercise without manual assessment enabled', () => {
            const exercise = manualAssessmentProgrammingExercise();
            exercise.assessmentType = AssessmentType.AUTOMATIC;

            createComponent(withCourse(exercise, true));

            expect(fixture.componentInstance.showFeedbackPopover()).toBe(false);
        });
    });
});
