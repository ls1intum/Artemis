import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ExamBarComponent } from 'app/exam/overview/exam-bar/exam-bar.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { AlertService } from 'app/shared/service/alert.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExamBarComponent', () => {
    let fixture: ComponentFixture<ExamBarComponent>;
    let comp: ExamBarComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }, MockProvider(AlertService), provideHttpClient()],
        }).compileComponents();
        // Required because exam bar uses the ResizeObserver for height calculations
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        fixture = TestBed.createComponent(ExamBarComponent);
        comp = fixture.componentInstance;

        comp.exam = new Exam();
        comp.exam.title = 'Test Exam';
        comp.studentExam = new StudentExam();
        comp.endDate = dayjs();
        comp.studentExam.exercises = [
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

    beforeEach(() => {
        fixture.detectChanges();
    });

    it('trigger emit and call saveExercise when the exam is about to end', () => {
        jest.spyOn(comp, 'saveExercise');
        jest.spyOn(comp.examAboutToEnd, 'emit');

        comp.triggerExamAboutToEnd();

        expect(comp.saveExercise).toHaveBeenCalledOnce();
        expect(comp.examAboutToEnd.emit).toHaveBeenCalledOnce();
    });

    it('should hand in the exam early', () => {
        jest.spyOn(comp.onExamHandInEarly, 'emit');
        jest.spyOn(comp, 'saveExercise');

        comp.handInEarly();
        expect(comp.onExamHandInEarly.emit).toHaveBeenCalledOnce();
    });
});
