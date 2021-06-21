import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { MockComponent, MockDirective } from 'ng-mocks';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExamTimerComponent } from 'app/exam/participate/timer/exam-timer.component';
import { TranslateTestingModule } from '../../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../../test.module';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';

describe('Exam Exercise Overview Component', () => {
    let fixture: ComponentFixture<ExamExerciseOverviewPageComponent>;
    let comp: ExamExerciseOverviewPageComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [ExamExerciseOverviewPageComponent, MockComponent(ExamTimerComponent), MockDirective(NgbTooltip)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamExerciseOverviewPageComponent);
        comp = fixture.componentInstance;

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

    it('should open the exercise', () => {
        spyOn(comp.onPageChanged, 'emit');

        comp.openExercise(comp.exercises[0]);

        expect(comp.onPageChanged.emit).toHaveBeenCalled();
    });
});
