import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ArtemisTestModule } from '../../../test.module';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExamExerciseOverviewPageComponent } from 'app/exam/participate/exercises/exercise-overview-page/exam-exercise-overview-page.component';
import { StudentExam } from 'app/entities/student-exam.model';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';

describe('ExamExerciseOverviewPageComponent', () => {
    let fixture: ComponentFixture<ExamExerciseOverviewPageComponent>;
    let comp: ExamExerciseOverviewPageComponent;
    let studentExam: StudentExam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
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
