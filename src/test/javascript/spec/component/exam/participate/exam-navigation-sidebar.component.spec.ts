import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockComponent, MockModule } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamSession } from 'app/entities/exam/exam-session.model';
import { of } from 'rxjs';
import { ExamNavigationSidebarComponent } from 'app/exam/participate/exam-navigation-sidebar/exam-navigation-sidebar.component';
import { CodeEditorRepositoryService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { MockTranslateService, TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { BehaviorSubject } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { CommitState } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { TranslateService } from '@ngx-translate/core';
import { facSaveSuccess, facSaveWarning } from 'src/main/webapp/content/icons/icons';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ExamLiveEventsButtonComponent } from 'app/exam/participate/events/exam-live-events-button.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

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
            imports: [ArtemisTestModule, TranslateTestingModule, MockModule(NgbTooltipModule), MockModule(ArtemisSharedCommonModule)],
            declarations: [ExamNavigationSidebarComponent, MockComponent(ExamTimerComponent), MockComponent(ExamLiveEventsButtonComponent)],
            providers: [
                ExamParticipationService,
                { provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamNavigationSidebarComponent);
        comp = fixture.componentInstance;
        repositoryService = fixture.debugElement.injector.get(CodeEditorRepositoryService);
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
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.collapsed')).not.toBeNull();

        comp.isCollapsed = false;
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('.collapsed')).toBeNull();
    });

    it('should call toggleCollapseState when clicking on collapse', () => {
        comp.isCollapsed = false;
        const toggleCollapseStateStub = jest.spyOn(comp, 'toggleCollapseState');
        fixture.detectChanges();
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
        fixture.detectChanges();
        window.dispatchEvent(event);
        expect(toggleCollapseStateSpy).toHaveBeenCalled();
    });

    it('should prevent default action on Control+M keydown event', () => {
        const event = new KeyboardEvent('keydown', {
            key: 'm',
            ctrlKey: true,
        });
        const preventDefaultSpy = jest.spyOn(event, 'preventDefault');
        fixture.detectChanges();
        window.dispatchEvent(event);
        expect(preventDefaultSpy).toHaveBeenCalled();
    });
});
