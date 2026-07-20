import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterLink, provideRouter } from '@angular/router';
import { NgTemplateOutlet } from '@angular/common';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PopoverModule } from 'primeng/popover';
import { TooltipModule } from 'primeng/tooltip';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseActionsComponent } from 'app/course/manage/exercises/exercise-row/exercise-actions.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { Exercise, ExerciseMode, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { EntitySummary } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';

@Component({ selector: 'jhi-quiz-exercise-lifecycle-buttons', template: '' })
class QuizLifecycleButtonsStubComponent {
    quizExercise = input.required<QuizExercise>();
    loadOne = output<number>();
    handleNewQuizExercise = output<QuizExercise>();
}

describe('ExerciseActionsComponent', () => {
    let fixture: ComponentFixture<ExerciseActionsComponent>;
    let component: ExerciseActionsComponent;
    let exerciseService: ExerciseService;
    let eventManager: EventManager;
    let textExerciseService: TextExerciseService;
    let fileUploadExerciseService: FileUploadExerciseService;
    let quizExerciseService: QuizExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;
    let modelingExerciseService: ModelingExerciseService;

    const course = { id: 1, title: 'Course', testCourse: false } as Course;

    const textExercise = (overrides?: Partial<Exercise>): Exercise => ({ id: 1, type: ExerciseType.TEXT, title: 'Text', ...overrides }) as Exercise;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseActionsComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ExerciseService),
                EventManager,
                MockProvider(TextExerciseService),
                MockProvider(FileUploadExerciseService),
                MockProvider(QuizExerciseService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(ModelingExerciseService),
                MockProvider(DeleteDialogService),
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .overrideComponent(ExerciseActionsComponent, {
                set: {
                    imports: [
                        RouterLink,
                        NgTemplateOutlet,
                        FaIconComponent,
                        PopoverModule,
                        TooltipModule,
                        ArtemisTranslatePipe,
                        DeleteButtonDirective,
                        QuizLifecycleButtonsStubComponent,
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseActionsComponent);
        component = fixture.componentInstance;
        exerciseService = TestBed.inject(ExerciseService);
        eventManager = TestBed.inject(EventManager);
        textExerciseService = TestBed.inject(TextExerciseService);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        quizExerciseService = TestBed.inject(QuizExerciseService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        modelingExerciseService = TestBed.inject(ModelingExerciseService);

        vi.spyOn(exerciseService, 'getDeletionSummary').mockReturnValue(of({} as EntitySummary));

        fixture.componentRef.setInput('exercise', textExercise());
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('course', course);
    });

    describe('mainActions', () => {
        it('includes teams only for team exercises', () => {
            fixture.componentRef.setInput('exercise', textExercise({ mode: ExerciseMode.TEAM }));
            expect(component.mainActions().map((a) => a.id)).toContain('teams');

            fixture.componentRef.setInput('exercise', textExercise({ mode: ExerciseMode.INDIVIDUAL }));
            expect(component.mainActions().map((a) => a.id)).not.toContain('teams');
        });

        it('always includes participations and scores', () => {
            const ids = component.mainActions().map((a) => a.id);
            expect(ids).toEqual(expect.arrayContaining(['participations', 'scores']));
        });

        it('adds quiz-specific actions, including re-evaluate only when ended and instructor', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, isAtLeastInstructor: true, quizEnded: true } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            const ids = component.mainActions().map((a) => a.id);
            expect(ids).toEqual(expect.arrayContaining(['statistics', 'preview', 'solution', 're-evaluate']));
        });

        it('omits re-evaluate when the quiz has not ended', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, isAtLeastInstructor: true, quizEnded: false } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            expect(component.mainActions().map((a) => a.id)).not.toContain('re-evaluate');
        });

        it('adds example submissions for modeling and text exercises', () => {
            fixture.componentRef.setInput('exercise', textExercise({ type: ExerciseType.MODELING }));
            expect(component.mainActions().map((a) => a.id)).toContain('examples');

            fixture.componentRef.setInput('exercise', textExercise({ type: ExerciseType.PROGRAMMING }));
            expect(component.mainActions().map((a) => a.id)).not.toContain('examples');
        });

        it('adds edit-in-editor only for programming exercises with editor rights', () => {
            fixture.componentRef.setInput('exercise', textExercise({ type: ExerciseType.PROGRAMMING, isAtLeastEditor: true }));
            expect(component.mainActions().map((a) => a.id)).toContain('edit-in-editor');

            fixture.componentRef.setInput('exercise', textExercise({ type: ExerciseType.PROGRAMMING, isAtLeastEditor: false }));
            expect(component.mainActions().map((a) => a.id)).not.toContain('edit-in-editor');
        });

        it('adds a plain edit action for non-quiz exercises with editor rights', () => {
            fixture.componentRef.setInput('exercise', textExercise({ isAtLeastEditor: true }));
            const edit = component.mainActions().find((a) => a.id === 'edit');
            expect(edit).toBeDefined();
            expect(edit?.disabled).toBeUndefined();
        });

        it('omits edit when lacking editor rights', () => {
            fixture.componentRef.setInput('exercise', textExercise({ isAtLeastEditor: false }));
            expect(component.mainActions().map((a) => a.id)).not.toContain('edit');
        });

        it('disables edit for a quiz that has ended', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, isAtLeastEditor: true, quizEnded: true, isEditable: true } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            const edit = component.mainActions().find((a) => a.id === 'edit');
            expect(edit?.disabled).toBe(true);
            expect(edit?.disabledTooltip).toBe('artemisApp.quizExercise.edit.editNotPossibleAfterEnd');
        });

        it('disables edit for an active quiz that is not editable', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, isAtLeastEditor: true, quizEnded: false, isEditable: false, status: QuizStatus.ACTIVE } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            const edit = component.mainActions().find((a) => a.id === 'edit');
            expect(edit?.disabled).toBe(true);
            expect(edit?.disabledTooltip).toBe('artemisApp.quizExercise.editNotPossibleDuringQuiz');
        });

        it('disables edit with the students-started tooltip when not editable and not active', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, isAtLeastEditor: true, quizEnded: false, isEditable: false, status: QuizStatus.VISIBLE } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            const edit = component.mainActions().find((a) => a.id === 'edit');
            expect(edit?.disabled).toBe(true);
            expect(edit?.disabledTooltip).toBe('artemisApp.quizExercise.editNotPossibleStudentsStarted');
        });

        it('enables edit for an editable quiz', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, isAtLeastEditor: true, quizEnded: false, isEditable: true, status: QuizStatus.VISIBLE } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            const edit = component.mainActions().find((a) => a.id === 'edit');
            expect(edit?.disabled).toBeUndefined();
        });

        it('adds delete only for instructors', () => {
            fixture.componentRef.setInput('exercise', textExercise({ isAtLeastInstructor: true }));
            expect(component.mainActions().map((a) => a.id)).toContain('delete');

            fixture.componentRef.setInput('exercise', textExercise({ isAtLeastInstructor: false }));
            expect(component.mainActions().map((a) => a.id)).not.toContain('delete');
        });
    });

    describe('hasQuizButtons', () => {
        it('is false for non-quiz exercises', () => {
            expect(component.hasQuizButtons()).toBe(false);
        });

        it('is true when the quiz is invisible and editor can make it visible', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, status: QuizStatus.INVISIBLE, isAtLeastEditor: true, visibleToStudents: false } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            expect(component.hasQuizButtons()).toBe(true);
        });

        it('is true when a synchronized quiz can be started', () => {
            const quiz = {
                id: 2,
                type: ExerciseType.QUIZ,
                status: QuizStatus.VISIBLE,
                quizMode: QuizMode.SYNCHRONIZED,
                isAtLeastEditor: true,
                quizStarted: false,
            } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            expect(component.hasQuizButtons()).toBe(true);
        });

        it('is true for a batched quiz that can show batches', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, status: QuizStatus.ACTIVE, quizMode: QuizMode.BATCHED } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            expect(component.hasQuizButtons()).toBe(true);
        });

        it('is true when an unsynchronized quiz can be ended by an instructor', () => {
            const quiz = {
                id: 2,
                type: ExerciseType.QUIZ,
                status: QuizStatus.ACTIVE,
                quizMode: QuizMode.INDIVIDUAL,
                isAtLeastInstructor: true,
                quizEnded: false,
            } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            expect(component.hasQuizButtons()).toBe(true);
        });

        it('is false when none of the button conditions apply', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ, status: QuizStatus.OPEN_FOR_PRACTICE } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            expect(component.hasQuizButtons()).toBe(false);
        });
    });

    describe('hiddenIds / hasOverflow', () => {
        it('shows nothing hidden before the row width is measured', () => {
            expect(component.hiddenIds().size).toBe(0);
            expect(component.hasOverflow()).toBe(false);
            expect(component.hiddenActions()).toEqual([]);
        });

        /** Seeds every current action's cached natural width to the same value, so only the row width drives collapsing. */
        const seedEqualWidths = (buttonWidth: number): void => {
            const widths = new Map<string, number>();
            for (const action of component.mainActions()) {
                widths.set(component['signatureOf'](action), buttonWidth);
            }
            component['buttonWidths'].set(widths);
        };

        it('keeps scores, edit and delete inline and overflows the type-specific quiz actions first', () => {
            // Editor+instructor quiz: participations, scores, statistics, preview, solution, edit, delete.
            const quiz = { id: 2, type: ExerciseType.QUIZ, title: 'Quiz', isAtLeastEditor: true, isAtLeastInstructor: true } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);

            // Each button 100px wide. With a 360px row (available 352 after the safety margin, budget 308 after the
            // ellipsis + gap) exactly three 100px buttons plus their gaps fit.
            seedEqualWidths(100);
            component['quizWidth'].set(0);
            component['rowWidth'].set(360);

            const hidden = component.hiddenActions().map((a) => a.id);
            expect(component.hasOverflow()).toBe(true);
            // The three highest-priority actions stay inline regardless of their display position.
            expect(hidden).not.toContain('scores');
            expect(hidden).not.toContain('edit');
            expect(hidden).not.toContain('delete');
            // The type-specific extras collapse into the ellipsis menu.
            expect(hidden).toEqual(expect.arrayContaining(['participations', 'statistics', 'preview', 'solution']));
        });

        it('hides nothing when every button fits', () => {
            seedEqualWidths(50);
            component['quizWidth'].set(0);
            component['rowWidth'].set(2000);
            expect(component.hiddenIds().size).toBe(0);
            expect(component.hasOverflow()).toBe(false);
        });
    });

    describe('deletionSummary / deleteTranslateValues', () => {
        it('delegates to the exercise service for the deletion summary', () => {
            component.deletionSummary();
            expect(exerciseService.getDeletionSummary).toHaveBeenCalledWith(component.exercise());
        });

        it('resolves the course type translation for the delete confirmation', () => {
            const values = component.deleteTranslateValues();
            expect(values.courseTitle).toBe('Course');
            expect(values.courseType).toBe('artemisApp.exercise.delete.realCourse');
        });

        it('uses the test-course translation for test courses', () => {
            fixture.componentRef.setInput('course', { ...course, testCourse: true });
            const values = component.deleteTranslateValues();
            expect(values.courseType).toBe('artemisApp.exercise.delete.testCourse');
        });
    });

    describe('menu interactions', () => {
        it('toggleMenu, runAction and closeMenuIfOpen do not throw when the popover is not rendered', () => {
            expect(() => component['toggleMenu']({} as Event)).not.toThrow();
            const action = component.mainActions()[0];
            expect(() => component['runAction'](action)).not.toThrow();
            expect(() => component['closeMenuIfOpen'](true)).not.toThrow();
            expect(() => component['closeMenuIfOpen'](false)).not.toThrow();
        });

        it('runAction invokes the action onClick callback', () => {
            const onClick = vi.fn();
            component['runAction']({ id: 'x', labelKey: 'k', icon: 'trash' as never, severity: 'primary', kind: 'button', onClick });
            expect(onClick).toHaveBeenCalledOnce();
        });
    });

    describe('onQuizLifecycleUpdate', () => {
        it('recomputes client-derived quiz status and emits the exercise', () => {
            vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.ACTIVE);
            const quiz = { id: 2, type: ExerciseType.QUIZ } as QuizExercise;
            const emitted: Exercise[] = [];
            component.exerciseUpdated.subscribe((e) => emitted.push(e));

            component['onQuizLifecycleUpdate'](quiz);

            expect(quiz.status).toBe(QuizStatus.ACTIVE);
            expect(quiz.quizStarted).toBe(true);
            expect(emitted).toEqual([quiz]);
        });
    });

    describe('onQuizReload', () => {
        it('does nothing when the exercise has no id', () => {
            fixture.componentRef.setInput('exercise', textExercise({ id: undefined }));
            const findSpy = vi.spyOn(quizExerciseService, 'find');
            component['onQuizReload']();
            expect(findSpy).not.toHaveBeenCalled();
        });

        it('reloads the quiz and emits it on success', () => {
            const quiz = { id: 2, type: ExerciseType.QUIZ } as QuizExercise;
            fixture.componentRef.setInput('exercise', quiz);
            vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
            vi.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: quiz })));
            const emitted: Exercise[] = [];
            component.exerciseUpdated.subscribe((e) => emitted.push(e));

            component['onQuizReload']();

            expect(emitted).toEqual([quiz]);
        });

        it('surfaces an error via dialogError$ when the reload fails', () => {
            fixture.componentRef.setInput('exercise', { id: 2, type: ExerciseType.QUIZ } as QuizExercise);
            vi.spyOn(quizExerciseService, 'find').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'boom' })));
            const errors: string[] = [];
            component.dialogError$.subscribe((e) => errors.push(e));

            component['onQuizReload']();

            expect(errors).toHaveLength(1);
        });
    });

    describe('onDelete', () => {
        it('does nothing when the exercise has no id', () => {
            fixture.componentRef.setInput('exercise', textExercise({ id: undefined }));
            const deleteSpy = vi.spyOn(textExerciseService, 'delete');
            component['onDelete']({});
            expect(deleteSpy).not.toHaveBeenCalled();
        });

        it.each([
            [ExerciseType.TEXT, 'textExerciseListModification'],
            [ExerciseType.FILE_UPLOAD, 'fileUploadExerciseListModification'],
            [ExerciseType.QUIZ, 'quizExerciseListModification'],
            [ExerciseType.MODELING, 'modelingExerciseListModification'],
        ])('deletes a %s exercise and broadcasts %s', (type, eventName) => {
            const services: Record<string, unknown> = {
                [ExerciseType.TEXT]: textExerciseService,
                [ExerciseType.FILE_UPLOAD]: fileUploadExerciseService,
                [ExerciseType.QUIZ]: quizExerciseService,
                [ExerciseType.MODELING]: modelingExerciseService,
            };
            const service = services[type] as { delete: (id: number) => unknown };
            const deleteSpy = vi.spyOn(service, 'delete').mockReturnValue(of(new HttpResponse<void>()));
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
            const exercise = textExercise({ id: 3, type });
            fixture.componentRef.setInput('exercise', exercise);
            const deleted: Exercise[] = [];
            component.exerciseDeleted.subscribe((e) => deleted.push(e));

            component['onDelete']({});

            expect(deleteSpy).toHaveBeenCalledWith(3);
            expect(broadcastSpy).toHaveBeenCalledWith({ name: eventName, content: 'Deleted an exercise' });
            expect(deleted).toEqual([exercise]);
        });

        it('deletes a programming exercise with the repo/build-plan flags', () => {
            const deleteSpy = vi.spyOn(programmingExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
            fixture.componentRef.setInput('exercise', textExercise({ id: 4, type: ExerciseType.PROGRAMMING }));

            component['onDelete']({ deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: false });

            expect(deleteSpy).toHaveBeenCalledWith(4, true, false);
        });

        it('offers the repo/build-plan cleanup checks for programming exercises when LocalCI is inactive', () => {
            fixture.componentRef.setInput('exercise', textExercise({ id: 4, type: ExerciseType.PROGRAMMING }));

            expect(component.deleteAdditionalChecks()).toEqual({
                deleteStudentReposBuildPlans: 'artemisApp.programmingExercise.delete.studentReposBuildPlans',
                deleteBaseReposBuildPlans: 'artemisApp.programmingExercise.delete.baseReposBuildPlans',
            });
        });

        it('hides the cleanup checks when LocalCI is active', () => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile) => profile === PROFILE_LOCALCI);
            const localCIFixture = TestBed.createComponent(ExerciseActionsComponent);
            localCIFixture.componentRef.setInput('exercise', textExercise({ id: 4, type: ExerciseType.PROGRAMMING }));
            localCIFixture.componentRef.setInput('courseId', 1);
            localCIFixture.componentRef.setInput('course', course);

            expect(localCIFixture.componentInstance.deleteAdditionalChecks()).toEqual({});
        });

        it('offers no cleanup checks for non-programming exercises', () => {
            fixture.componentRef.setInput('exercise', textExercise({ id: 3, type: ExerciseType.TEXT }));

            expect(component.deleteAdditionalChecks()).toEqual({});
        });

        it('surfaces an error via dialogError$ when deletion fails', () => {
            vi.spyOn(textExerciseService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'boom' })));
            fixture.componentRef.setInput('exercise', textExercise({ id: 3, type: ExerciseType.TEXT }));
            const errors: string[] = [];
            component.dialogError$.subscribe((e) => errors.push(e));

            component['onDelete']({});

            expect(errors).toHaveLength(1);
        });
    });
});
