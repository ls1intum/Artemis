import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamSession } from 'app/exam/shared/entities/exam-session.model';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { BehaviorSubject, of } from 'rxjs';
import { ExamNavigationSidebarComponent } from 'app/exam/overview/exam-navigation-sidebar/exam-navigation-sidebar.component';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { CommitState } from 'app/programming/shared/code-editor/model/code-editor.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { facSaveSuccess, facSaveWarning } from 'app/shared/icons/icons';

describe('ExamNavigationSidebarComponent', () => {
    let fixture: ComponentFixture<ExamNavigationSidebarComponent>;
    let comp: ExamNavigationSidebarComponent;
    let repositoryService: CodeEditorRepositoryService;

    const examExerciseIdForNavigationSourceMock = new BehaviorSubject<number>(-1);
    const mockExamExerciseUpdateService = {
        currentExerciseIdForNavigation: examExerciseIdForNavigationSourceMock.asObservable(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService },
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamNavigationSidebarComponent);
        comp = fixture.componentInstance;
        repositoryService = TestBed.inject(CodeEditorRepositoryService);
        TestBed.inject(ExamParticipationService);

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

    it('should set the exercise button status for undefined submission', () => {
        const result = comp.setExerciseButtonStatus(1);

        expect(result).toBe('synced');
    });

    it('should set the exercise button status for submitted and synced submission active', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(result).toBe('synced saved');
    });

    it('should set the exercise button status for submitted submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(comp.icon).toEqual(facSaveWarning);
        expect(result).toBe('notSynced');
    });

    it('should set the exercise button status for submitted and synced submission saved', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(0);
        expect(comp.icon).toEqual(facSaveSuccess);
        expect(result).toBe('synced saved');
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
        expect(comp.setExerciseButtonStatus(0)).toBe('synced saved');
        expect(comp.icon).toEqual(facSaveSuccess);
    });

    it('should set exercise button status to synced if it is not the active exercise in the exam timeline view', () => {
        comp.examTimeLineView = true;
        comp.exerciseIndex = 0;
        expect(comp.setExerciseButtonStatus(1)).toBe('synced');
        expect(comp.icon).toEqual(facSaveSuccess);
    });

    it('should toggle sidebar based on isCollapsed', () => {
        comp.isCollapsed = true;
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.collapsed')).not.toBeNull();

        comp.isCollapsed = false;
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('.collapsed')).toBeNull();
    });

    it('should call toggleCollapseState when clicking on collapse', () => {
        comp.isCollapsed = false;
        const toggleCollapseStateStub = jest.spyOn(comp, 'toggleCollapseState');
        fixture.changeDetectorRef.detectChanges();
        const actionItem = fixture.nativeElement.querySelector('#test-collapse');
        actionItem.click();
        expect(toggleCollapseStateStub).toHaveBeenCalled();
        expect(comp.isCollapsed).toBeTrue();
    });

    it('should toggle collapse state on Control+M keydown event', () => {
        const event = new KeyboardEvent('keydown', {
            key: 'm',
            ctrlKey: true,
        });
        const toggleCollapseStateSpy = jest.spyOn(comp, 'toggleCollapseState');
        fixture.changeDetectorRef.detectChanges();
        window.dispatchEvent(event);
        expect(toggleCollapseStateSpy).toHaveBeenCalled();
    });

    it('should prevent default action on Control+M keydown event', () => {
        const event = new KeyboardEvent('keydown', {
            key: 'm',
            ctrlKey: true,
        });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');
        fixture.changeDetectorRef.detectChanges();
        window.dispatchEvent(event);
        expect(preventDefaultSpy).toHaveBeenCalled();
    });
});
