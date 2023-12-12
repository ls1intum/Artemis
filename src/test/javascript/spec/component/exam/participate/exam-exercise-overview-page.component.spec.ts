import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockComponent, MockModule } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { MockTranslateService, TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { StudentExam } from 'app/entities/student-exam.model';
import { TranslateService } from '@ngx-translate/core';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { QuizExam } from 'app/entities/quiz-exam.model';

describe('ExamExerciseOverviewPageComponent', () => {
    let fixture: ComponentFixture<ExamExerciseOverviewPageComponent>;
    let comp: ExamExerciseOverviewPageComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule, MockModule(NgbTooltipModule)],
            declarations: [ExamExerciseOverviewPageComponent, MockComponent(ExamTimerComponent)],
            providers: [
                ExamParticipationService,
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamExerciseOverviewPageComponent);
        comp = fixture.componentInstance;
        comp.studentExam = new StudentExam();
        comp.studentExam.exercises = [
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

    it('should open the exercise', () => {
        jest.spyOn(comp.onPageChanged, 'emit');

        comp.openExercise(comp.studentExam.exercises![0]);

        expect(comp.onPageChanged.emit).toHaveBeenCalledOnce();
    });

    it('should emit an event with quizExamChange set to true when quizExamChange is true', () => {
        comp.quizExam = new QuizExam();
        const emitSpy = jest.spyOn(comp.onPageChanged, 'emit');

        comp.openQuizExam();

        expect(emitSpy).toHaveBeenCalledWith({ overViewChange: false, quizExamChange: true, exercise: undefined, forceSave: false });
    });

    describe('getQuizExamButtonTooltip', () => {
        it('should return synced when quizExam.submission exists and isSynced is true', () => {
            comp.quizExam = {
                type: ExerciseType.QUIZ,
                submission: {
                    isSynced: true,
                    submitted: false,
                },
            };
            const tooltip = comp.getQuizExamButtonTooltip();
            expect(tooltip).toBe('synced');
        });

        it('should return notSynced when quizExam.submission exists and isSynced is false', () => {
            comp.quizExam = {
                type: ExerciseType.QUIZ,
                submission: {
                    isSynced: false,
                    submitted: false,
                },
            };

            const tooltip = comp.getQuizExamButtonTooltip();
            expect(tooltip).toBe('notSynced');
        });
    });

    describe('setQuizExamButtonStatus', () => {
        it('should return synced when quizExam is undefined', () => {
            comp.quizExam = undefined;

            const result = comp.setQuizExamIconStatus();

            expect(result).toBe('synced');
        });

        it('should return synced when none of the input parameters are defined', () => {
            const result = comp.setQuizExamIconStatus();
            expect(result).toBe('synced');
        });

        it('should return notSynced when quizExam.submission.isSubmitted is false and quizExam.submission.isSynced is false', () => {
            comp.quizExam = {
                type: ExerciseType.QUIZ,
                submission: {
                    isSynced: false,
                    submitted: false,
                },
            };

            const result = comp.setQuizExamIconStatus();

            expect(result).toBe('notSynced');
        });
    });
});
