import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { MockComponent, MockDirective } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { BehaviorSubject } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';

describe('Exam Navigation Bar Component', () => {
    let fixture: ComponentFixture<ExamNavigationBarComponent>;
    let comp: ExamNavigationBarComponent;

    const examExerciseIdForNavigationSourceMock = new BehaviorSubject<number>(-1);
    const mockExamExerciseUpdateService = {
        currentExerciseIdForNavigation: examExerciseIdForNavigationSourceMock.asObservable(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [ExamNavigationBarComponent, MockComponent(ExamTimerComponent), MockDirective(NgbTooltip)],
            providers: [
                ExamParticipationService,
                { provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamNavigationBarComponent);
        comp = fixture.componentInstance;
        TestBed.inject(ExamParticipationService);

        comp.endDate = moment();
        comp.exercises = [
            {
                id: 0,
                type: ExerciseType.PROGRAMMING,
                studentParticipations: [
                    {
                        submissions: [{ id: 3 } as Submission],
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

    it('trigger when the exam is about to end', () => {
        spyOn(comp, 'saveExercise');
        spyOn(comp.examAboutToEnd, 'emit');

        comp.triggerExamAboutToEnd();

        expect(comp.saveExercise).toHaveBeenCalled();
        expect(comp.examAboutToEnd.emit).toHaveBeenCalled();
    });

    it('should change the exercise', () => {
        spyOn(comp.onPageChanged, 'emit');
        spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex).toEqual(0);

        const exerciseIndex = 1;
        const force = false;

        comp.changePage(false, exerciseIndex, force);

        expect(comp.exerciseIndex).toEqual(exerciseIndex);
        expect(comp.onPageChanged.emit).toHaveBeenCalled();
        expect(comp.setExerciseButtonStatus).toHaveBeenCalledWith(exerciseIndex);
    });

    it('should not change the exercise with invalid index', () => {
        spyOn(comp.onPageChanged, 'emit');
        spyOn(comp, 'setExerciseButtonStatus');

        expect(comp.exerciseIndex).toEqual(0);

        const exerciseIndex = 5;
        const force = false;

        comp.changePage(false, exerciseIndex, force);

        expect(comp.exerciseIndex).toEqual(0);
        expect(comp.onPageChanged.emit).not.toHaveBeenCalled();
        expect(comp.setExerciseButtonStatus).not.toHaveBeenCalledWith(exerciseIndex);
    });

    it('should tell the type of the selected programming exercise', () => {
        comp.exerciseIndex = 0;

        expect(comp.isProgrammingExercise()).toBe(true);
    });

    it('should tell the type of the selected text exercise', () => {
        comp.exerciseIndex = 1;

        expect(comp.isProgrammingExercise()).toBe(false);
    });

    it('should tell the type of the selected modeling exercise', () => {
        comp.exerciseIndex = 2;

        expect(comp.isProgrammingExercise()).toBe(false);
    });

    it('save the exercise with changeExercise', () => {
        spyOn(comp, 'changePage');
        const changeExercise = true;

        comp.saveExercise(changeExercise);

        expect(comp.changePage).toHaveBeenCalled();
    });

    it('save the exercise without changeExercise', () => {
        spyOn(comp, 'changePage');
        const changeExercise = false;

        comp.saveExercise(changeExercise);

        expect(comp.changePage).not.toHaveBeenCalled();
    });

    it('should hand in the exam early', () => {
        spyOn(comp, 'saveExercise');
        spyOn(comp.onExamHandInEarly, 'emit');

        comp.handInEarly();

        expect(comp.saveExercise).toHaveBeenCalled();
        expect(comp.onExamHandInEarly.emit).toHaveBeenCalled();
    });

    it('should set the exercise button status for undefined submission', () => {
        const result = comp.setExerciseButtonStatus(1);

        expect(result).toEqual('synced');
    });

    it('should set the exercise button status for submitted submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(comp.icon).toEqual('edit');
        expect(result).toEqual('notSynced');
    });

    it('should set the exercise button status for submitted and synced submission active', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(0);

        expect(result).toEqual('synced active');
    });

    it('should set the exercise button status for submitted and synced submission not active', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.setExerciseButtonStatus(1);

        expect(result).toEqual('synced');
    });

    it('should get the exercise button tooltip without submission', () => {
        const result = comp.getExerciseButtonTooltip(comp.exercises[1]);

        expect(result).toEqual('synced');
    });

    it('should get the exercise button tooltip with submitted and synced submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true, isSynced: true };

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toEqual('submitted');
    });

    it('should get the exercise button tooltip with submitted submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { submitted: true };

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toEqual('notSavedOrSubmitted');
    });

    it('should get the exercise button tooltip with submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = {};

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toEqual('notSavedOrSubmitted');
    });

    it('should get the exercise button tooltip with synced submission', () => {
        comp.exercises[0].studentParticipations![0].submissions![0] = { isSynced: true };

        const result = comp.getExerciseButtonTooltip(comp.exercises[0]);

        expect(result).toEqual('notSubmitted');
    });

    describe('Exam Exercise Update', () => {
        const updatedExerciseId = 2;

        it('should navigate to other Exercise', () => {
            spyOn(comp, 'changeExerciseById');
            examExerciseIdForNavigationSourceMock.next(updatedExerciseId);
            expect(comp.changeExerciseById).toHaveBeenCalled();
        });
    });
});
