import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { Submission } from 'app/entities/submission.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { MockComponent, MockDirective } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService, TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';

describe('Exam Exercise Overview Component', () => {
    let fixture: ComponentFixture<ExamExerciseOverviewPageComponent>;
    let comp: ExamExerciseOverviewPageComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [ExamExerciseOverviewPageComponent, MockComponent(ExamTimerComponent), MockDirective(NgbTooltip)],
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
});
