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
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import dayjs from 'dayjs/esm';
import { Subject, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

describe('ExerciseHeaderActionsComponent', () => {
    let fixture: ComponentFixture<ExerciseHeaderActionsComponent>;

    /** An exercise of exactly the given type, so that the type specific rules are not tested on an exercise of another class. */
    function createExerciseOfType(type: ExerciseType): Exercise {
        switch (type) {
            case ExerciseType.TEXT:
                return new TextExercise(undefined, undefined);
            case ExerciseType.MODELING:
                return new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            default:
                return new ProgrammingExercise(undefined, undefined);
        }
    }

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

    function createComponent(exercise: Exercise, options: { athenaEnabled?: boolean; examMode?: boolean; llmAccepted?: boolean; quizStatus?: QuizStatus } = {}) {
        const { athenaEnabled = true, examMode = false, llmAccepted = true, quizStatus } = options;

        const accountService = new MockAccountService();
        if (llmAccepted) {
            accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);
        }

        TestBed.configureTestingModule({
            imports: [ExerciseHeaderActionsComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(QuizExerciseService, { getStatus: () => quizStatus as QuizStatus }),
                MockProvider(AlertService),
                MockProvider(CourseExerciseService),
                MockProvider(ParticipationService, {
                    getSpecificStudentParticipation: (participations: StudentParticipation[], testRun: boolean) =>
                        participations.find((participation) => !!participation.testRun === testRun),
                }),
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

    describe('start exercise', () => {
        it('should send only one start request while the request is pending', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 7;
            exercise.studentParticipations = [];
            createComponent(exercise);
            const participationSubject = new Subject<StudentParticipation>();
            const startExerciseStub = vi.spyOn(TestBed.inject(CourseExerciseService), 'startExercise').mockReturnValue(participationSubject);

            fixture.componentInstance.startExercise();
            fixture.componentInstance.startExercise();

            expect(startExerciseStub).toHaveBeenCalledOnce();
            expect(fixture.componentInstance.isLoading()).toBe(true);

            participationSubject.error(new Error('failed'));
            expect(fixture.componentInstance.isLoading()).toBe(false);

            fixture.componentInstance.startExercise();
            expect(startExerciseStub).toHaveBeenCalledTimes(2);
        });
    });

    describe('practice mode of a team exercise', () => {
        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])(
            'should offer starting practice for a team %s exercise after the due date until the own practice participation exists',
            (type) => {
                const teamParticipation = { id: 10, testRun: false, initializationState: InitializationState.FINISHED } as StudentParticipation;
                const exercise = createExerciseOfType(type);
                exercise.teamMode = true;
                exercise.studentAssignedTeamId = 3;
                exercise.dueDate = dayjs().subtract(1, 'hour');
                exercise.studentParticipations = [teamParticipation];
                createComponent(exercise);

                expect(fixture.componentInstance.isStartPracticeAvailable()).toBe(true);

                fixture.componentInstance.receiveNewParticipation({ id: 20, testRun: true, initializationState: InitializationState.INITIALIZED } as StudentParticipation);

                expect(fixture.componentInstance.isStartPracticeAvailable()).toBe(false);
                expect(fixture.componentInstance.gradedParticipation()?.id).toBe(10);
                expect(fixture.componentInstance.practiceParticipation()?.id).toBe(20);
            },
        );
    });

    describe('practice mode and the individual due date of the graded participation', () => {
        const createTeamExercise = (type: ExerciseType, teamParticipation: StudentParticipation) => {
            const exercise = createExerciseOfType(type);
            exercise.teamMode = true;
            exercise.studentAssignedTeamId = 3;
            exercise.dueDate = dayjs().subtract(1, 'day');
            exercise.studentParticipations = [teamParticipation];
            return exercise;
        };

        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])(
            'should not offer starting practice for a %s exercise while an extension is running',
            (type) => {
                const graded = { id: 10, testRun: false, initializationState: InitializationState.FINISHED, individualDueDate: dayjs().add(1, 'hour') } as StudentParticipation;
                const exercise = createExerciseOfType(type);
                exercise.dueDate = dayjs().subtract(1, 'day');
                exercise.studentParticipations = [graded];
                createComponent(exercise);

                expect(fixture.componentInstance.isStartPracticeAvailable()).toBe(false);
            },
        );

        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])('should offer starting practice for a %s exercise once the extension has passed', (type) => {
            const graded = { id: 10, testRun: false, initializationState: InitializationState.FINISHED, individualDueDate: dayjs().subtract(1, 'hour') } as StudentParticipation;
            const exercise = createExerciseOfType(type);
            exercise.dueDate = dayjs().subtract(1, 'day');
            exercise.studentParticipations = [graded];
            createComponent(exercise);

            expect(fixture.componentInstance.isStartPracticeAvailable()).toBe(true);
        });

        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])(
            'should use the extension of the team and not of the own practice participation for a team %s exercise',
            (type) => {
                const teamParticipation = {
                    id: 10,
                    testRun: false,
                    initializationState: InitializationState.FINISHED,
                    individualDueDate: dayjs().add(1, 'hour'),
                } as StudentParticipation;
                createComponent(createTeamExercise(type, teamParticipation));

                expect(fixture.componentInstance.isStartPracticeAvailable()).toBe(false);

                fixture.componentInstance.receiveNewParticipation({ id: 20, testRun: true, initializationState: InitializationState.REPO_COPIED } as StudentParticipation);
                expect(fixture.componentInstance.gradedParticipation()?.id).toBe(10);
                expect(fixture.componentInstance.practiceParticipation()?.id).toBe(20);
                // the practice participation of a programming exercise still needs its setup, so only the extension of the team keeps the button hidden
                expect(fixture.componentInstance.isStartPracticeAvailable()).toBe(false);
            },
        );
    });

    describe('feedback button participation', () => {
        // Lives here rather than in the header spec, which mocks the button away.
        it('should follow the participation mode when choosing the participation for feedback', () => {
            const graded = { id: 10, testRun: false, submissions: [{ submitted: true }] } as StudentParticipation;
            const practice = { id: 20, testRun: true, submissions: [{ submitted: false }] } as StudentParticipation;

            const fixture = createComponent(new TextExercise(undefined, undefined));
            vi.spyOn(TestBed.inject(ParticipationService), 'getSpecificStudentParticipation').mockImplementation((participations: StudentParticipation[], testRun: boolean) =>
                participations.find((participation) => participation.testRun === testRun),
            );

            // Setting the exercise now, so the effect that reads the participations runs against the stub.
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 1;
            exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            exercise.allowOnlineEditor = false;
            exercise.studentParticipations = [graded, practice];
            fixture.componentRef.setInput('exercise', exercise);
            fixture.componentRef.setInput('participationMode', 'graded');
            fixture.detectChanges();

            expect(fixture.componentInstance.activeParticipationForCode()?.id).toBe(10);

            fixture.componentRef.setInput('participationMode', 'practice');
            fixture.detectChanges();

            expect(fixture.componentInstance.activeParticipationForCode()?.id).toBe(20);
        });
    });

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

    describe('showQuizStartPracticeButton', () => {
        const quizOpenForPractice = (options: { withPracticeParticipation?: boolean; dueDatePassed?: boolean } = {}): QuizExercise => {
            const { withPracticeParticipation = false, dueDatePassed = true } = options;
            const quiz = new QuizExercise(undefined, undefined);
            quiz.id = 42;
            quiz.dueDate = dueDatePassed ? dayjs().subtract(1, 'day') : dayjs().add(1, 'day');
            quiz.studentParticipations = [{ id: 1, testRun: false } as StudentParticipation];
            if (withPracticeParticipation) {
                quiz.studentParticipations.push({ id: 2, testRun: true } as StudentParticipation);
            }
            return quiz;
        };

        const setQuizInputs = (inputs: { participationMode?: ParticipationMode; quizPracticeAttemptFinished?: boolean; onContinueExercise?: () => void }) => {
            if (inputs.participationMode) {
                fixture.componentRef.setInput('participationMode', inputs.participationMode);
            }
            if (inputs.quizPracticeAttemptFinished !== undefined) {
                fixture.componentRef.setInput('quizPracticeAttemptFinished', inputs.quizPracticeAttemptFinished);
            }
            if (inputs.onContinueExercise) {
                fixture.componentRef.setInput('onContinueExercise', inputs.onContinueExercise);
            }
        };

        it('should not show the button when practice is not available yet', () => {
            createComponent(quizOpenForPractice({ dueDatePassed: false }));

            expect(fixture.componentInstance.showQuizStartPracticeButton()).toBe(false);
        });

        it('should not show the button in exam mode', () => {
            createComponent(quizOpenForPractice(), { examMode: true });

            expect(fixture.componentInstance.showQuizStartPracticeButton()).toBe(false);
        });

        it('should show the button in the graded view before the first practice attempt', () => {
            createComponent(quizOpenForPractice());

            expect(fixture.componentInstance.showQuizStartPracticeButton()).toBe(true);
        });

        it('should hide the button in the graded view once a practice attempt exists', () => {
            createComponent(quizOpenForPractice({ withPracticeParticipation: true }));

            expect(fixture.componentInstance.showQuizStartPracticeButton()).toBe(false);
        });

        it('should hide the button while a practice attempt is in progress', () => {
            createComponent(quizOpenForPractice({ withPracticeParticipation: true }));
            setQuizInputs({ participationMode: 'practice', quizPracticeAttemptFinished: false });

            expect(fixture.componentInstance.showQuizStartPracticeButton()).toBe(false);
        });

        it('should show the button once the practice attempt is finished', () => {
            createComponent(quizOpenForPractice({ withPracticeParticipation: true }));
            setQuizInputs({ participationMode: 'practice', quizPracticeAttemptFinished: true });

            expect(fixture.componentInstance.showQuizStartPracticeButton()).toBe(true);
        });

        it('should hide the button while a previous result is open', () => {
            createComponent(quizOpenForPractice({ withPracticeParticipation: true }));
            setQuizInputs({ participationMode: 'practice', quizPracticeAttemptFinished: true, onContinueExercise: () => {} });

            expect(fixture.componentInstance.showQuizStartPracticeButton()).toBe(false);
        });
    });

    describe('instructor actions', () => {
        const links = () => fixture.componentInstance.instructorActionItems().map((item) => item.routerLink);

        it('should offer no instructor action to a student', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 5;
            createComponent(exercise);

            expect(fixture.componentInstance.instructorActionItems()).toEqual([]);
        });

        it('should offer a tutor to view the exercise, its scores and its participations', () => {
            const exercise = new TextExercise(undefined, undefined);
            exercise.id = 5;
            exercise.isAtLeastTutor = true;
            createComponent(exercise);

            expect(links()).toEqual([
                '/course-management/1/text-exercises/5/',
                '/course-management/1/text-exercises/5/scores',
                '/course-management/1/text-exercises/5/participations',
            ]);
        });

        it('should offer a tutor the preview and the solution of a quiz instead of the participations', () => {
            const quiz = new QuizExercise(undefined, undefined);
            quiz.id = 5;
            quiz.isAtLeastTutor = true;
            createComponent(quiz);

            expect(links()).toEqual([
                '/course-management/1/quiz-exercises/5/',
                '/course-management/1/quiz-exercises/5/scores',
                '/course-management/1/quiz-exercises/5/preview',
                '/course-management/1/quiz-exercises/5/solution',
            ]);
        });

        it('should offer an editor the grading configuration of a programming exercise', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 5;
            exercise.isAtLeastTutor = true;
            exercise.isAtLeastEditor = true;
            createComponent(exercise);

            expect(links()).toContain('/course-management/1/programming-exercises/5/grading/test-cases');
        });

        it('should offer an editor the statistics of a modeling exercise', () => {
            const exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
            exercise.id = 5;
            exercise.isAtLeastEditor = true;
            createComponent(exercise);

            expect(links()).toEqual(['/course-management/1/modeling-exercises/5/exercise-statistics']);
        });

        it('should offer an editor to edit a quiz that is not started yet', () => {
            const quiz = new QuizExercise(undefined, undefined);
            quiz.id = 5;
            quiz.isAtLeastEditor = true;
            createComponent(quiz, { quizStatus: QuizStatus.VISIBLE });

            expect(links()).toEqual(['/course-management/1/quiz-exercises/5/quiz-point-statistic', '/course-management/1/quiz-exercises/5/edit']);
        });

        it('should not offer to edit a quiz that is open for practice, but to re-evaluate it as an instructor', () => {
            const quiz = new QuizExercise(undefined, undefined);
            quiz.id = 5;
            quiz.isAtLeastEditor = true;
            quiz.isAtLeastInstructor = true;
            createComponent(quiz, { quizStatus: QuizStatus.OPEN_FOR_PRACTICE });

            expect(links()).toEqual(['/course-management/1/quiz-exercises/5/quiz-point-statistic', '/course-management/1/quiz-exercises/5/re-evaluate']);
        });

        it('should not offer to re-evaluate a quiz to an editor who is no instructor', () => {
            const quiz = new QuizExercise(undefined, undefined);
            quiz.id = 5;
            quiz.isAtLeastEditor = true;
            createComponent(quiz, { quizStatus: QuizStatus.OPEN_FOR_PRACTICE });

            expect(links()).not.toContain('/course-management/1/quiz-exercises/5/re-evaluate');
        });
    });

    describe('exercise specific state', () => {
        it.each([
            [ExerciseType.MODELING, 'openModelingEditor'],
            [ExerciseType.TEXT, 'openTextEditor'],
            [ExerciseType.FILE_UPLOAD, 'uploadFile'],
        ])('should label the editor button of a %s exercise', (type, label) => {
            const exercise = type === ExerciseType.FILE_UPLOAD ? new FileUploadExercise(undefined, undefined) : createExerciseOfType(type);
            createComponent(exercise);

            expect(fixture.componentInstance.editorLabel()).toBe(label);
            expect(fixture.componentInstance.programmingExercise()).toBeUndefined();
        });

        it('should expose a programming exercise and no editor label', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            createComponent(exercise);

            expect(fixture.componentInstance.programmingExercise()).toBe(exercise);
            expect(fixture.componentInstance.editorLabel()).toBeUndefined();
        });

        it('should detect a quiz that the student did not start yet', () => {
            const quiz = new QuizExercise(undefined, undefined);
            quiz.quizEnded = false;
            quiz.studentParticipations = [];
            quiz.quizBatches = [];
            createComponent(quiz);

            expect(fixture.componentInstance.uninitializedQuiz()).toBe(false);
            expect(fixture.componentInstance.quizNotStarted()).toBe(true);
        });

        it('should detect a team that is available for the student', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.teamMode = true;
            exercise.studentAssignedTeamIdComputed = true;
            exercise.studentAssignedTeamId = 4;
            createComponent(exercise);

            expect(fixture.componentInstance.isTeamAvailable()).toBe(true);
        });
    });

    describe('participations', () => {
        const programmingExercise = (participations: StudentParticipation[]) => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 7;
            exercise.studentParticipations = participations;
            return exercise;
        };

        it('should replace a known participation and emit it', () => {
            createComponent(programmingExercise([{ id: 10, testRun: false, initializationState: InitializationState.INACTIVE } as StudentParticipation]));
            const emitted: StudentParticipation[] = [];
            fixture.componentInstance.newParticipation.subscribe((participation) => emitted.push(participation));
            const updated = { id: 10, testRun: false, initializationState: InitializationState.INITIALIZED } as StudentParticipation;

            fixture.componentInstance.receiveNewParticipation(updated);

            expect(fixture.componentInstance.studentParticipations()).toEqual([updated]);
            expect(fixture.componentInstance.gradedParticipation()).toBe(updated);
            expect(emitted).toEqual([updated]);
        });

        it('should count the results and detect a rated graded result without counting an Athena result', () => {
            const athenaResult = { rated: true, assessmentType: AssessmentType.AUTOMATIC_ATHENA } as Result;
            const automaticResult = { rated: true, assessmentType: AssessmentType.AUTOMATIC } as Result;
            const graded = { id: 10, testRun: false, submissions: [{ results: [athenaResult] }] } as StudentParticipation;
            createComponent(programmingExercise([graded]));

            expect(fixture.componentInstance.numberOfGradedParticipationResults()).toBe(1);
            expect(fixture.componentInstance.hasRatedGradedResult()).toBe(false);

            fixture.componentInstance.receiveNewParticipation({ ...graded, submissions: [{ results: [athenaResult, automaticResult] }] } as StudentParticipation);

            expect(fixture.componentInstance.numberOfGradedParticipationResults()).toBe(2);
            expect(fixture.componentInstance.hasRatedGradedResult()).toBe(true);
        });

        it('should link to the repository of the active participation and to the exercise without one', () => {
            const practice = { id: 20, testRun: true } as StudentParticipation;
            createComponent(programmingExercise([practice]));

            expect(fixture.componentInstance.routerLinkForRepositoryView()).toEqual(['/courses', 1, 'exercises', 7]);

            fixture.componentRef.setInput('participationMode', 'practice');
            fixture.detectChanges();

            expect(fixture.componentInstance.routerLinkForRepositoryView()).toEqual(['/courses', 1, 'exercises', 7, 'repository', 20]);
        });

        it('should fall back to the graded participation in the practice mode as long as no practice participation exists', () => {
            createComponent(programmingExercise([{ id: 10, testRun: false } as StudentParticipation]));
            fixture.componentRef.setInput('participationMode', 'practice');
            fixture.detectChanges();

            expect(fixture.componentInstance.activeParticipationForCode()?.id).toBe(10);
        });

        it('should only report a programming submission that was submitted', () => {
            createComponent(programmingExercise([{ id: 10, testRun: false, submissions: [{ submitted: false }] } as StudentParticipation]));
            expect(fixture.componentInstance.hasProgrammingSubmission()).toBe(false);

            fixture.componentInstance.receiveNewParticipation({ id: 10, testRun: false, submissions: [{ submitted: true }] } as StudentParticipation);
            expect(fixture.componentInstance.hasProgrammingSubmission()).toBe(true);
        });

        it('should take the assigned team from the participation and fall back to the exercise', () => {
            const exercise = programmingExercise([{ id: 10, testRun: false, team: { id: 3 } } as StudentParticipation]);
            exercise.studentAssignedTeamId = 9;
            createComponent(exercise);
            expect(fixture.componentInstance.assignedTeamId).toBe(3);

            fixture.componentInstance.receiveNewParticipation({ id: 10, testRun: false } as StudentParticipation);
            expect(fixture.componentInstance.assignedTeamId).toBe(9);
        });

        it('should not offer starting the exercise to a student of a team exercise without an assigned team', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.teamMode = true;
            exercise.studentParticipations = [];
            createComponent(exercise);

            expect(fixture.componentInstance.isStartExerciseAvailable()).toBe(false);

            exercise.studentAssignedTeamId = 3;
            fixture.componentRef.setInput('exercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise));
            fixture.detectChanges();

            expect(fixture.componentInstance.isStartExerciseAvailable()).toBe(true);
        });

        it('should not offer starting the exercise in the exam mode', () => {
            const exercise = programmingExercise([]);
            exercise.dueDate = dayjs().add(1, 'day');
            createComponent(exercise);
            expect(fixture.componentInstance.isStartExerciseAvailable()).toBe(true);
            TestBed.resetTestingModule();

            createComponent(exercise, { examMode: true });
            expect(fixture.componentInstance.isStartExerciseAvailable()).toBe(false);
        });

        it('should not offer resuming or practicing in the exam mode', () => {
            const exercise = programmingExercise([]);
            exercise.dueDate = dayjs().subtract(1, 'day');
            createComponent(exercise, { examMode: true });

            expect(fixture.componentInstance.isResumeExerciseAvailable({ testRun: true } as StudentParticipation)).toBe(false);
            expect(fixture.componentInstance.isStartPracticeAvailable()).toBe(false);
        });

        it('should offer resuming a practice participation only after the due date', () => {
            const exercise = programmingExercise([]);
            exercise.dueDate = dayjs().subtract(1, 'day');
            createComponent(exercise);

            expect(fixture.componentInstance.isResumeExerciseAvailable({ testRun: true } as StudentParticipation)).toBe(true);
            expect(fixture.componentInstance.isResumeExerciseAvailable({ testRun: false } as StudentParticipation)).toBe(false);
        });

        it('should detect that a student has to wait for the start date', () => {
            const exercise = programmingExercise([]);
            exercise.startDate = dayjs().add(1, 'day');
            createComponent(exercise);
            expect(fixture.componentInstance.isBeforeStartDateAndStudent).toBe(true);

            fixture.componentRef.setInput('exercise', Object.assign(new ProgrammingExercise(undefined, undefined), exercise, { isAtLeastTutor: true }));
            fixture.detectChanges();
            expect(fixture.componentInstance.isBeforeStartDateAndStudent).toBe(false);
        });
    });

    describe('allowEditing', () => {
        const withGradedParticipation = (initializationState: InitializationState | undefined, dueDate?: dayjs.Dayjs) => {
            const exercise = new TextExercise(undefined, undefined);
            exercise.dueDate = dueDate;
            exercise.studentParticipations = initializationState ? [{ id: 10, testRun: false, initializationState } as StudentParticipation] : [];
            createComponent(exercise);
            return fixture.componentInstance;
        };

        it('should allow editing an initialized participation before the due date', () => {
            expect(withGradedParticipation(InitializationState.INITIALIZED, dayjs().add(1, 'day')).allowEditing).toBe(true);
        });

        it('should not allow editing an initialized participation after the due date', () => {
            expect(withGradedParticipation(InitializationState.INITIALIZED, dayjs().subtract(1, 'day')).allowEditing).toBe(false);
        });

        it('should allow editing a finished participation', () => {
            expect(withGradedParticipation(InitializationState.FINISHED, dayjs().subtract(1, 'day')).allowEditing).toBe(true);
        });

        it('should not allow editing without a participation', () => {
            expect(withGradedParticipation(undefined).allowEditing).toBe(false);
        });
    });

    describe('start and resume', () => {
        const programming = (allowOfflineIde: boolean) => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 7;
            exercise.allowOfflineIde = allowOfflineIde;
            exercise.studentParticipations = [];
            return exercise;
        };

        it.each([
            { allowOfflineIde: true, key: 'artemisApp.exercise.personalRepositoryClone' },
            { allowOfflineIde: false, key: 'artemisApp.exercise.personalRepositoryOnline' },
        ])('should report a started programming exercise as $key', ({ allowOfflineIde, key }) => {
            createComponent(programming(allowOfflineIde));
            const participation = { id: 10, testRun: false, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
            vi.spyOn(TestBed.inject(CourseExerciseService), 'startExercise').mockReturnValue(of(participation));
            const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');

            fixture.componentInstance.startExercise();

            expect(successSpy).toHaveBeenCalledExactlyOnceWith(key);
            expect(fixture.componentInstance.gradedParticipation()).toBe(participation);
            expect(fixture.componentInstance.isLoading()).toBe(false);
        });

        it('should report an error when the started programming exercise is not initialized', () => {
            createComponent(programming(true));
            vi.spyOn(TestBed.inject(CourseExerciseService), 'startExercise').mockReturnValue(
                of({ id: 10, testRun: false, initializationState: InitializationState.REPO_COPIED } as StudentParticipation),
            );
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            fixture.componentInstance.startExercise();

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.exercise.startError');
        });

        it('should not alert when a text exercise was started', () => {
            const exercise = new TextExercise(undefined, undefined);
            exercise.id = 8;
            createComponent(exercise);
            vi.spyOn(TestBed.inject(CourseExerciseService), 'startExercise').mockReturnValue(of({ id: 11, testRun: false } as StudentParticipation));
            const alertService = TestBed.inject(AlertService);
            const successSpy = vi.spyOn(alertService, 'success');
            const errorSpy = vi.spyOn(alertService, 'error');

            fixture.componentInstance.startExercise();

            expect(successSpy).not.toHaveBeenCalled();
            expect(errorSpy).not.toHaveBeenCalled();
        });

        it.each([
            { status: 500, alerted: true },
            { status: 403, alerted: false },
        ])('should handle the start error with status $status (alerted: $alerted)', ({ status, alerted }) => {
            createComponent(programming(true));
            vi.spyOn(TestBed.inject(CourseExerciseService), 'startExercise').mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            fixture.componentInstance.startExercise();

            expect(errorSpy).toHaveBeenCalledTimes(alerted ? 1 : 0);
            expect(fixture.componentInstance.isLoading()).toBe(false);
        });

        it.each([
            { testRun: true, expectedId: 20 },
            { testRun: false, expectedId: 10 },
        ])('should resume the participation with testRun $testRun', ({ testRun, expectedId }) => {
            const exercise = programming(true);
            exercise.studentParticipations = [
                { id: 10, testRun: false, initializationState: InitializationState.INACTIVE } as StudentParticipation,
                { id: 20, testRun: true, initializationState: InitializationState.INACTIVE } as StudentParticipation,
            ];
            createComponent(exercise);
            const resumed = { id: expectedId, testRun, initializationState: InitializationState.INITIALIZED } as StudentParticipation;
            const resumeSpy = vi.spyOn(TestBed.inject(CourseExerciseService), 'resumeProgrammingExercise').mockReturnValue(of(resumed));
            const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');

            fixture.componentInstance.resumeProgrammingExercise(testRun);

            expect(resumeSpy).toHaveBeenCalledExactlyOnceWith(7, expectedId, exercise);
            expect(successSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.exercise.resumeProgrammingExercise');
            expect(fixture.componentInstance.studentParticipations().find((participation) => participation.id === expectedId)).toBe(resumed);
            expect(fixture.componentInstance.isLoading()).toBe(false);
        });

        it('should alert the error key of the server when resuming fails', () => {
            const exercise = programming(true);
            exercise.studentParticipations = [{ id: 10, testRun: false } as StudentParticipation];
            createComponent(exercise);
            vi.spyOn(TestBed.inject(CourseExerciseService), 'resumeProgrammingExercise').mockReturnValue(
                throwError(() => ({ error: { entityName: 'participation', errorKey: 'resumeFailed' } })),
            );
            const errorSpy = vi.spyOn(TestBed.inject(AlertService), 'error');

            fixture.componentInstance.resumeProgrammingExercise(false);

            expect(errorSpy).toHaveBeenCalledExactlyOnceWith('artemisApp.participation.errors.resumeFailed');
            expect(fixture.componentInstance.isLoading()).toBe(false);
        });
    });

    describe('shouldDisplayIDEButtons', () => {
        const programmingWith = (participations: StudentParticipation[], dueDate?: dayjs.Dayjs) => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 7;
            exercise.dueDate = dueDate;
            exercise.studentParticipations = participations;
            createComponent(exercise);
            // the mocked participation service decides whether the practice participation is preferred
            vi.spyOn(TestBed.inject(ParticipationService), 'shouldPreferPractice').mockImplementation((exercise) => !!exercise?.dueDate && dayjs().isAfter(exercise.dueDate));
            return fixture.componentInstance;
        };

        const participation = (testRun: boolean, initializationState: InitializationState, repositoryUri = 'https://repo.example/git') =>
            ({ id: testRun ? 20 : 10, testRun, initializationState, repositoryUri }) as StudentParticipation;

        it('should not display the buttons without a repository', () => {
            expect(programmingWith([participation(false, InitializationState.INITIALIZED, '')]).shouldDisplayIDEButtons()).toBe(false);
        });

        it('should display the buttons for an initialized graded participation', () => {
            expect(programmingWith([participation(false, InitializationState.INITIALIZED)]).shouldDisplayIDEButtons()).toBe(true);
        });

        it.each([InitializationState.INACTIVE, InitializationState.FINISHED])('should display the buttons for a graded participation in the state %s', (state) => {
            expect(programmingWith([participation(false, state)]).shouldDisplayIDEButtons()).toBe(true);
        });

        it('should not display the buttons while the repository is still being set up', () => {
            expect(programmingWith([participation(false, InitializationState.REPO_COPIED)]).shouldDisplayIDEButtons()).toBe(false);
        });

        it.each([
            { dueDate: dayjs().subtract(1, 'day'), expected: true },
            { dueDate: dayjs().add(1, 'day'), expected: false },
        ])('should display the buttons for an initialized practice participation only after the due date (due date passed: $expected)', ({ dueDate, expected }) => {
            expect(programmingWith([participation(true, InitializationState.INITIALIZED)], dueDate).shouldDisplayIDEButtons()).toBe(expected);
        });

        it('should display the buttons for an initialized practice participation in the exam mode', () => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.studentParticipations = [participation(true, InitializationState.INITIALIZED)];
            createComponent(exercise, { examMode: true });

            expect(fixture.componentInstance.shouldDisplayIDEButtons()).toBe(true);
        });
    });

    describe('quiz action', () => {
        const quiz = () => {
            const exercise = new QuizExercise(undefined, undefined);
            exercise.id = 12;
            return exercise;
        };

        it('should switch to the practice mode and navigate to the practice quiz', () => {
            createComponent(quiz());
            const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
            const modeChanges: ParticipationMode[] = [];
            fixture.componentInstance.participationModeChange.subscribe((mode) => modeChanges.push(mode));

            fixture.componentInstance.handleQuizAction('practice');

            expect(modeChanges).toEqual(['practice']);
            expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['/courses', 1, 'exercises', 'quiz-exercises', 12, 'practice']);
        });

        it('should not navigate when restarting the practice is handled by the parent', () => {
            createComponent(quiz());
            const restart = vi.fn().mockReturnValue(true);
            fixture.componentRef.setInput('onRestartPractice', restart);
            const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

            fixture.componentInstance.handleQuizAction('practice');

            expect(restart).toHaveBeenCalledOnce();
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('should navigate when the parent does not restart the practice', () => {
            createComponent(quiz());
            fixture.componentRef.setInput('onRestartPractice', () => false);
            const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

            fixture.componentInstance.handleQuizAction('practice');

            expect(navigateSpy).toHaveBeenCalledOnce();
        });

        it('should navigate to the live quiz without changing the participation mode', () => {
            createComponent(quiz());
            const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
            const modeChanges: ParticipationMode[] = [];
            fixture.componentInstance.participationModeChange.subscribe((mode) => modeChanges.push(mode));

            fixture.componentInstance.handleQuizAction('live');

            expect(modeChanges).toEqual([]);
            expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['/courses', 1, 'exercises', 'quiz-exercises', 12, 'live']);
        });
    });

    describe('submit popover', () => {
        const athenaResult = (successful: boolean) => ({ assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful }) as Result;

        const createWithSubmitAction = (participation?: StudentParticipation) => {
            const exercise = withCourse(manualAssessmentProgrammingExercise(), true);
            exercise.studentParticipations = participation ? [participation] : [];
            createComponent(exercise);
            const onSubmit = vi.fn();
            fixture.componentRef.setInput('onSubmitExercise', onSubmit);
            fixture.detectChanges();
            const popover = fixture.componentInstance['submitPopoverRef']()!;
            return { onSubmit, popover };
        };

        it('should submit and open the feedback popover', () => {
            const { onSubmit, popover } = createWithSubmitAction();
            const openSpy = vi.spyOn(popover, 'open').mockImplementation(() => {});

            fixture.componentInstance.submitAndShowPopover();

            expect(onSubmit).toHaveBeenCalledOnce();
            expect(openSpy).toHaveBeenCalledOnce();
        });

        it('should submit without a popover once the limit of feedback requests is reached', () => {
            const results = Array.from({ length: 10 }, () => athenaResult(true));
            const { onSubmit, popover } = createWithSubmitAction({ id: 10, testRun: false, submissions: [{ results }] } as StudentParticipation);
            const openSpy = vi.spyOn(popover, 'open').mockImplementation(() => {});

            fixture.componentInstance.submitAndShowPopover();

            expect(onSubmit).toHaveBeenCalledOnce();
            expect(openSpy).not.toHaveBeenCalled();
        });

        it('should close the popover', () => {
            const { popover } = createWithSubmitAction();
            const closeSpy = vi.spyOn(popover, 'close').mockImplementation(() => {});

            fixture.componentInstance.closeSubmitPopover();

            expect(closeSpy).toHaveBeenCalledOnce();
        });

        it('should close an open popover on a click outside of the component', () => {
            const { popover } = createWithSubmitAction();
            vi.spyOn(popover, 'isOpen').mockReturnValue(true);
            const closeSpy = vi.spyOn(popover, 'close').mockImplementation(() => {});

            fixture.componentInstance.onDocumentClick({ target: document.body } as unknown as MouseEvent);

            expect(closeSpy).toHaveBeenCalledOnce();
        });

        it('should keep an open popover on a click inside of the component or the popover', () => {
            const { popover } = createWithSubmitAction();
            vi.spyOn(popover, 'isOpen').mockReturnValue(true);
            const closeSpy = vi.spyOn(popover, 'close').mockImplementation(() => {});
            const insidePopover = document.createElement('div');
            insidePopover.className = 'popover';

            fixture.componentInstance.onDocumentClick({ target: fixture.nativeElement } as unknown as MouseEvent);
            fixture.componentInstance.onDocumentClick({ target: insidePopover } as unknown as MouseEvent);

            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should ignore a click while the popover is closed', () => {
            const { popover } = createWithSubmitAction();
            vi.spyOn(popover, 'isOpen').mockReturnValue(false);
            const closeSpy = vi.spyOn(popover, 'close').mockImplementation(() => {});

            fixture.componentInstance.onDocumentClick({ target: document.body } as unknown as MouseEvent);

            expect(closeSpy).not.toHaveBeenCalled();
        });
    });
});
