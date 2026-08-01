import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExamExerciseOverviewPageComponent } from 'app/exam/overview/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { By } from '@angular/platform-browser';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { computed } from '@angular/core';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { facSaveSuccess, facSaveWarning } from 'app/foundation/icons/icons';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

describe('ExamExerciseOverviewPageComponent', () => {
    let fixture: ComponentFixture<ExamExerciseOverviewPageComponent>;
    let comp: ExamExerciseOverviewPageComponent;
    let studentExam: StudentExam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamExerciseOverviewPageComponent);
        comp = fixture.componentInstance;
        studentExam = new StudentExam();
        studentExam.exercises = [
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
        fixture.componentRef.setInput('studentExam', studentExam);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        fixture.detectChanges();
        await Promise.resolve();
    });

    it('should open the exercise', () => {
        vi.spyOn(comp.onPageChanged, 'emit');

        comp.openExercise(studentExam.exercises![0]);

        expect(comp.onPageChanged.emit).toHaveBeenCalledOnce();
    });

    it('jhi-updating-result component should be defined', () => {
        const studentExamValue = comp.studentExam?.(); // Optional chaining to handle potential undefined.

        const exerciseWithParticipations = studentExamValue?.exercises?.find((ex) => ex.studentParticipations && ex.studentParticipations.length > 0);
        expect(exerciseWithParticipations).toBeDefined();

        fixture.detectChanges();

        const resultComponent = fixture.debugElement.query(By.css(`#jhi-updating-result-0`));

        expect(resultComponent).not.toBeNull();
    });

    it('should re-evaluate the exercise status icon when a submission editor reports an isSynced change', () => {
        // Regression guard, mirroring exam-navigation-sidebar.component.spec.ts: `isSynced` is mutated in
        // place on a plain submission object, so under zoneless change detection this binding only
        // re-evaluates if it reads the service-wide signal the submission editors bump. A cached
        // `computed` stands in for the template binding and would keep the stale icon without that read.
        const examParticipationService = TestBed.inject(ExamParticipationService);
        const submission = { id: 9, submitted: true, isSynced: true } as Submission;
        const exercise = { id: 4, type: ExerciseType.TEXT, studentParticipations: [{ submissions: [submission] } as StudentParticipation] } as Exercise;
        const item = { exercise, icon: facSaveSuccess } as any;

        const status = TestBed.runInInjectionContext(() => computed(() => comp.setExerciseIconStatus(item)));
        expect(status()).toBe('synced saved');
        expect(item.icon).toEqual(facSaveSuccess);

        // Exactly what the text/quiz/modeling editors do when the student edits their answer.
        submission.isSynced = false;
        examParticipationService.notifySubmissionSyncStateChanged();

        expect(status()).toBe('notSynced');
        expect(item.icon).toEqual(facSaveWarning);
    });
});
