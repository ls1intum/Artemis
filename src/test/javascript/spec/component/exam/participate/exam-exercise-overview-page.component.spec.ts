import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/exercise/entities/exercise.model';
import { Submission } from 'app/exercise/entities/submission.model';
import { StudentParticipation } from 'app/exercise/entities/participation/student-participation.model';
import { ExamExerciseOverviewPageComponent } from 'app/exam/overview/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { StudentExam } from 'app/entities/student-exam.model';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';

describe('ExamExerciseOverviewPageComponent', () => {
    let fixture: ComponentFixture<ExamExerciseOverviewPageComponent>;
    let comp: ExamExerciseOverviewPageComponent;
    let studentExam: StudentExam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
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

    beforeEach(fakeAsync(() => {
        fixture.detectChanges();
        tick();
    }));

    it('should open the exercise', () => {
        jest.spyOn(comp.onPageChanged, 'emit');

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
});
