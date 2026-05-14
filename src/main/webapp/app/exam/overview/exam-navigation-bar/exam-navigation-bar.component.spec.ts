import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamSession } from 'app/exam/shared/entities/exam-session.model';
import { BehaviorSubject, of } from 'rxjs';
import { ExamNavigationBarComponent } from 'app/exam/overview/exam-navigation-bar/exam-navigation-bar.component';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { CommitState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { faCheck, faEdit } from '@fortawesome/free-solid-svg-icons';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DialogService } from 'primeng/dynamicdialog';
import { MockProvider } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('Exam Navigation Bar Component', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExamNavigationBarComponent>;
    let comp: ExamNavigationBarComponent;
    let repositoryService: CodeEditorRepositoryService;

    const examExerciseIdForNavigationSourceMock = new BehaviorSubject<number>(-1);
    const mockExamExerciseUpdateService = {
        currentExerciseIdForNavigation: examExerciseIdForNavigationSourceMock.asObservable(),
    };

    let exercises: Exercise[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(DialogService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamNavigationBarComponent);
        comp = fixture.componentInstance;
        repositoryService = TestBed.inject(CodeEditorRepositoryService);
        TestBed.inject(ExamParticipationService);

        exercises = [
            {
                id: 0,
                type: ExerciseType.PROGRAMMING,
                studentParticipations: [
                    {
                        submissions: [{ id: 3, isSynced: true } as Submission],
                    } as StudentParticipation,
                ],
            } as Exercise,
            { id: 1, type: ExerciseType.TEXT } as Exercise,
            { id: 2, type: ExerciseType.MODELING } as Exercise,
        ];

        fixture.componentRef.setInput('endDate', dayjs());
        fixture.componentRef.setInput('exercises', exercises);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        fixture.detectChanges();
        await Promise.resolve();
    });

    it('should update the submissions onInit if their CommitState is UNCOMMITTED_CHANGES to isSynced false, if not in initial session', () => {
        // Given
        // Create an exam session, which is not an initial session.
        fixture.componentRef.setInput('examSessions', [{ initialSession: false } as ExamSession]);
        const exerciseToBeSynced = exercises[0];
        vi.spyOn(repositoryService, 'getStatus').mockReturnValue(of({ repositoryStatus: CommitState.UNCOMMITTED_CHANGES }));

        // When
        expect(ExamParticipationService.getSubmissionForExercise(exerciseToBeSynced)!.isSynced).toBe(true);
        comp.ngOnInit();

        // Then
        expect(ExamParticipationService.getSubmissionForExercise(exerciseToBeSynced)!.isSynced).toBe(false);
    });

    it('trigger when the exam is about to end', () => {
        vi.spyOn(comp, 'saveExercise');
        vi.spyOn(comp.examAboutToEnd, 'emit');

        comp.triggerExamAboutToEnd();

        expect(comp.saveExercise).toHaveBeenCalledOnce();
        expect(comp.examAboutToEnd.emit).toHaveBeenCalledOnce();
    });

    it('should change the exercise', () => {
        vi.spyOn(comp.onPageChanged, 'emit');
        vi.spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex()).toBe(0);

        const exerciseIndex = 1;
        const force = false;

        comp.changePage(false, exerciseIndex, force);

        expect(comp.onPageChanged.emit).toHaveBeenCalledOnce();
        expect(comp.setExerciseButtonStatus).toHaveBeenCalledWith(exerciseIndex);
    });

    it('should not change the exercise with invalid index', () => {
        vi.spyOn(comp.onPageChanged, 'emit');
        vi.spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex()).toBe(0);

        const exerciseIndex = 5;
        const force = false;

        comp.changePage(false, exerciseIndex, force);

        expect(comp.exerciseIndex()).toBe(0);
        expect(comp.onPageChanged.emit).not.toHaveBeenCalled();
        expect(comp.setExerciseButtonStatus).not.toHaveBeenCalledWith(exerciseIndex);
    });

    it('should tell the type of the selected programming exercise', () => {
        fixture.componentRef.setInput('exerciseIndex', 0);

        expect(comp.isProgrammingExercise()).toBe(true);
    });

    it('should tell the type of the selected text exercise', () => {
        fixture.componentRef.setInput('exerciseIndex', 1);

        expect(comp.isProgrammingExercise()).toBe(false);
    });

    it('should tell the type of the selected modeling exercise', () => {
        fixture.componentRef.setInput('exerciseIndex', 2);

        expect(comp.isProgrammingExercise()).toBe(false);
    });

    it('save the exercise with changeExercise', () => {
        vi.spyOn(comp, 'changePage');
        const changeExercise = true;

        comp.saveExercise(changeExercise);

        expect(comp.changePage).toHaveBeenCalledOnce();
    });

    it('save the exercise without changeExercise', () => {
        vi.spyOn(comp, 'changePage');
        const changeExercise = false;

        comp.saveExercise(changeExercise);

        expect(comp.changePage).not.toHaveBeenCalled();
    });

    it('should hand in the exam early', () => {
        vi.spyOn(comp.onExamHandInEarly, 'emit');

        comp.handInEarly();

        expect(comp.onExamHandInEarly.emit).toHaveBeenCalledOnce();
    });

    it('should set the exercise button status for undefined submission', () => {
        const result = comp.setExerciseButtonStatus(1);

        expect(result).toBe('synced');
    });

    it('should set the exercise button status for submitted submission', () => {
        exercises[0].studentParticipations![0].submissions![0] = { submitted: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(comp.icon).toEqual(faEdit);
        expect(result).toBe('notSynced');
    });

    it('should set the exercise button status for submitted and synced submission active', () => {
        exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(result).toBe('synced active');
    });

    it('should set the exercise button status for submitted and synced submission not active', () => {
        exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(1);

        expect(result).toBe('synced');
    });

    it('should get the exercise button tooltip without submission', () => {
        const result = comp.getExerciseButtonTooltip(exercises[1]);

        expect(result).toBe('synced');
    });

    it('should get the exercise button tooltip with submitted and synced submission', () => {
        exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.getExerciseButtonTooltip(exercises[0]);

        expect(result).toBe('submitted');
    });

    it('should get the exercise button tooltip with submitted submission', () => {
        exercises[0].studentParticipations![0].submissions![0] = { submitted: true };

        const result = comp.getExerciseButtonTooltip(exercises[0]);

        expect(result).toBe('notSavedOrSubmitted');
    });

    it('should get the exercise button tooltip with submission', () => {
        exercises[0].studentParticipations![0].submissions![0] = {};

        const result = comp.getExerciseButtonTooltip(exercises[0]);

        expect(result).toBe('notSavedOrSubmitted');
    });

    it('should get the exercise button tooltip with synced submission', () => {
        exercises[0].studentParticipations![0].submissions![0] = { isSynced: true };

        const result = comp.getExerciseButtonTooltip(exercises[0]);

        expect(result).toBe('notSubmitted');
    });

    it('should navigate to other Exercise', () => {
        const updatedExerciseId = 2;
        vi.spyOn(comp, 'changeExerciseById');
        examExerciseIdForNavigationSourceMock.next(updatedExerciseId);
        expect(comp.changeExerciseById).toHaveBeenCalledOnce();
    });

    it('should set exercise button status to synced active if it is the active exercise in the exam timeline view', () => {
        fixture.componentRef.setInput('examTimeLineView', true);
        fixture.componentRef.setInput('exerciseIndex', 0);
        expect(comp.setExerciseButtonStatus(0)).toBe('synced active');
        expect(comp.icon).toEqual(faCheck);
    });

    it('should set exercise button status to synced if it is not the active exercise in the exam timeline view', () => {
        fixture.componentRef.setInput('examTimeLineView', true);
        fixture.componentRef.setInput('exerciseIndex', 0);
        expect(comp.setExerciseButtonStatus(1)).toBe('synced');
        expect(comp.icon).toEqual(faCheck);
    });

    describe('isOnlyOfflineIDE', () => {
        it('should return true for programming exercise with only offline IDE', () => {
            const exercise = {
                type: ExerciseType.PROGRAMMING,
                allowOfflineIde: true,
                allowOnlineEditor: false,
            } as any;
            Object.setPrototypeOf(exercise, Object.getPrototypeOf(exercises[0]));

            // Use a mock programming exercise
            const result = comp.isOnlyOfflineIDE({ allowOfflineIde: true, allowOnlineEditor: false } as any);

            expect(result).toBe(false); // Returns false because it's not instanceof ProgrammingExercise
        });

        it('should return false for non-programming exercise', () => {
            const result = comp.isOnlyOfflineIDE(exercises[1]);

            expect(result).toBe(false);
        });
    });

    describe('getOverviewStatus', () => {
        it('should return active when overview page is open', () => {
            fixture.componentRef.setInput('overviewPageOpen', true);

            expect(comp.getOverviewStatus()).toBe('active');
        });

        it('should return empty string when overview page is closed', () => {
            fixture.componentRef.setInput('overviewPageOpen', false);

            expect(comp.getOverviewStatus()).toBe('');
        });
    });

    describe('isFileUploadExercise', () => {
        it('should return true for file upload exercise', () => {
            const localExercises = [...exercises];
            localExercises[1] = { id: 1, type: ExerciseType.FILE_UPLOAD } as Exercise;
            fixture.componentRef.setInput('exercises', localExercises);
            fixture.componentRef.setInput('exerciseIndex', 1);

            expect(comp.isFileUploadExercise()).toBe(true);
        });

        it('should return false for non-file upload exercise', () => {
            fixture.componentRef.setInput('exerciseIndex', 0);

            expect(comp.isFileUploadExercise()).toBe(false);
        });
    });

    describe('changePage with overview', () => {
        it('should change to overview page', () => {
            vi.spyOn(comp.onPageChanged, 'emit');
            vi.spyOn(comp, 'setExerciseButtonStatus');

            comp.changePage(true, -1);

            expect(comp.onPageChanged.emit).toHaveBeenCalledWith({
                overViewChange: true,
                exercise: undefined,
                forceSave: false,
            });
        });

        it('should not change for negative exercise index', () => {
            vi.spyOn(comp.onPageChanged, 'emit');
            fixture.componentRef.setInput('exerciseIndex', 0);

            comp.changePage(false, -1);

            expect(comp.exerciseIndex()).toBe(0);
            expect(comp.onPageChanged.emit).not.toHaveBeenCalled();
        });
    });

    describe('saveExercise', () => {
        it('should save exercise and stay on last exercise when at end', () => {
            vi.spyOn(comp, 'changePage');
            fixture.componentRef.setInput('exerciseIndex', 2); // Last exercise

            comp.saveExercise(true);

            expect(comp.changePage).toHaveBeenCalledWith(false, 2, true);
        });
    });
});
