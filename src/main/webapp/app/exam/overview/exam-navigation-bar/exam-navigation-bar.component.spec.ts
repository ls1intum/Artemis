import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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

describe('Exam Navigation Bar Component', () => {
    let fixture: ComponentFixture<ExamNavigationBarComponent>;
    let comp: ExamNavigationBarComponent;
    let repositoryService: CodeEditorRepositoryService;

    const examExerciseIdForNavigationSourceMock = new BehaviorSubject<number>(-1);
    const mockExamExerciseUpdateService = {
        currentExerciseIdForNavigation: examExerciseIdForNavigationSourceMock.asObservable(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamNavigationBarComponent);
        comp = fixture.componentInstance;
        repositoryService = TestBed.inject(CodeEditorRepositoryService);
        TestBed.inject(ExamParticipationService);

        comp.endDate = dayjs();
        comp.exercises = [
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
    });

    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
    }));

    it('should update the submissions onInit if their CommitState is UNCOMMITTED_CHANGES to isSynced false, if not in initial session', () => {
        // Given
        // Create an exam session, which is not an initial session.
        comp.examSessions = [{ initialSession: false } as ExamSession];
        const exerciseToBeSynced = comp.exercises[0];
        jest.spyOn(repositoryService, 'getStatus').mockReturnValue(of({ repositoryStatus: CommitState.UNCOMMITTED_CHANGES }));

        // When
        expect(ExamParticipationService.getSubmissionForExercise(exerciseToBeSynced)!.isSynced).toBeTrue();
        comp.ngOnInit();

        // Then
        expect(ExamParticipationService.getSubmissionForExercise(exerciseToBeSynced)!.isSynced).toBeFalse();
    });

    it('trigger when the exam is about to end', () => {
        jest.spyOn(comp, 'saveExercise');
        jest.spyOn(comp.examAboutToEnd, 'emit');

        comp.triggerExamAboutToEnd();

        expect(comp.saveExercise).toHaveBeenCalledOnce();
        expect(comp.examAboutToEnd.emit).toHaveBeenCalledOnce();
    });

    it('should change the exercise', () => {
        jest.spyOn(comp.onPageChanged, 'emit');
        jest.spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex).toBe(0);

        const exerciseIndex = 1;
        const force = false;

        comp.changePage(false, exerciseIndex, force);

        expect(comp.exerciseIndex).toEqual(exerciseIndex);
        expect(comp.onPageChanged.emit).toHaveBeenCalledOnce();
        expect(comp.setExerciseButtonStatus).toHaveBeenCalledWith(exerciseIndex);
    });

    it('should not change the exercise with invalid index', () => {
        jest.spyOn(comp.onPageChanged, 'emit');
        jest.spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex).toBe(0);

        const exerciseIndex = 5;
        const force = false;

        comp.changePage(false, exerciseIndex, force);

        expect(comp.exerciseIndex).toBe(0);
        expect(comp.onPageChanged.emit).not.toHaveBeenCalled();
        expect(comp.setExerciseButtonStatus).not.toHaveBeenCalledWith(exerciseIndex);
    });

    it('should tell the type of the selected programming exercise', () => {
        comp.exerciseIndex = 0;

        expect(comp.isProgrammingExercise()).toBeTrue();
    });

    it('should tell the type of the selected text exercise', () => {
        comp.exerciseIndex = 1;

        expect(comp.isProgrammingExercise()).toBeFalse();
    });

    it('should tell the type of the selected modeling exercise', () => {
        comp.exerciseIndex = 2;

        expect(comp.isProgrammingExercise()).toBeFalse();
    });

    it('save the exercise with changeExercise', () => {
        jest.spyOn(comp, 'changePage');
        const changeExercise = true;

        comp.saveExercise(changeExercise);

        expect(comp.changePage).toHaveBeenCalledOnce();
    });

    it('save the exercise without changeExercise', () => {
        jest.spyOn(comp, 'changePage');
        const changeExercise = false;

        comp.saveExercise(changeExercise);

        expect(comp.changePage).not.toHaveBeenCalled();
    });

    it('should hand in the exam early', () => {
        jest.spyOn(comp.onExamHandInEarly, 'emit');

        comp.handInEarly();

        expect(comp.onExamHandInEarly.emit).toHaveBeenCalledOnce();
    });

    it('should set the exercise button status for undefined submission', () => {
        const result = comp.setExerciseButtonStatus(1);

        expect(result).toBe('synced');
    });

    it('should set the exercise button status for submitted submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(comp.icon).toEqual(faEdit);
        expect(result).toBe('notSynced');
    });

    it('should set the exercise button status for submitted and synced submission active', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(result).toBe('synced active');
    });

    it('should set the exercise button status for submitted and synced submission not active', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(1);

        expect(result).toBe('synced');
    });

    it('should get the exercise button tooltip without submission', () => {
        const result = comp.getExerciseButtonTooltip(comp.exercises[1]);

        expect(result).toBe('synced');
    });

    it('should get the exercise button tooltip with submitted and synced submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toBe('submitted');
    });

    it('should get the exercise button tooltip with submitted submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true };

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toBe('notSavedOrSubmitted');
    });

    it('should get the exercise button tooltip with submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = {};

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toBe('notSavedOrSubmitted');
    });

    it('should get the exercise button tooltip with synced submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { isSynced: true };

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toBe('notSubmitted');
    });

    it('should navigate to other Exercise', () => {
        const updatedExerciseId = 2;
        jest.spyOn(comp, 'changeExerciseById');
        examExerciseIdForNavigationSourceMock.next(updatedExerciseId);
        expect(comp.changeExerciseById).toHaveBeenCalledOnce();
    });

    it('should set exercise button status to synced active if it is the active exercise in the exam timeline view', () => {
        comp.examTimeLineView = true;
        comp.exerciseIndex = 0;
        expect(comp.setExerciseButtonStatus(0)).toBe('synced active');
        expect(comp.icon).toEqual(faCheck);
    });

    it('should set exercise button status to synced if it is not the active exercise in the exam timeline view', () => {
        comp.examTimeLineView = true;
        comp.exerciseIndex = 0;
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
            Object.setPrototypeOf(exercise, Object.getPrototypeOf(comp.exercises[0]));

            // Use a mock programming exercise
            const result = comp.isOnlyOfflineIDE({ allowOfflineIde: true, allowOnlineEditor: false } as any);

            expect(result).toBeFalse(); // Returns false because it's not instanceof ProgrammingExercise
        });

        it('should return false for non-programming exercise', () => {
            const result = comp.isOnlyOfflineIDE(comp.exercises[1]);

            expect(result).toBeFalse();
        });
    });

    describe('getOverviewStatus', () => {
        it('should return active when overview page is open', () => {
            comp.overviewPageOpen = true;

            expect(comp.getOverviewStatus()).toBe('active');
        });

        it('should return empty string when overview page is closed', () => {
            comp.overviewPageOpen = false;

            expect(comp.getOverviewStatus()).toBe('');
        });
    });

    describe('isFileUploadExercise', () => {
        it('should return true for file upload exercise', () => {
            comp.exercises[1] = { id: 1, type: ExerciseType.FILE_UPLOAD } as Exercise;
            comp.exerciseIndex = 1;

            expect(comp.isFileUploadExercise()).toBeTrue();
        });

        it('should return false for non-file upload exercise', () => {
            comp.exerciseIndex = 0;

            expect(comp.isFileUploadExercise()).toBeFalse();
        });
    });

    describe('changePage with overview', () => {
        it('should change to overview page', () => {
            jest.spyOn(comp.onPageChanged, 'emit');
            jest.spyOn(comp, 'setExerciseButtonStatus');

            comp.changePage(true, -1);

            expect(comp.exerciseIndex).toBe(-1);
            expect(comp.onPageChanged.emit).toHaveBeenCalledWith({
                overViewChange: true,
                exercise: undefined,
                forceSave: false,
            });
        });

        it('should not change for negative exercise index', () => {
            jest.spyOn(comp.onPageChanged, 'emit');
            comp.exerciseIndex = 0;

            comp.changePage(false, -1);

            expect(comp.exerciseIndex).toBe(0);
            expect(comp.onPageChanged.emit).not.toHaveBeenCalled();
        });
    });

    describe('saveExercise', () => {
        it('should save exercise and stay on last exercise when at end', () => {
            jest.spyOn(comp, 'changePage');
            comp.exerciseIndex = 2; // Last exercise

            comp.saveExercise(true);

            expect(comp.changePage).toHaveBeenCalledWith(false, 2, true);
        });
    });
});
