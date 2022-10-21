import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time.component';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';

describe('StudentExamWorkingTimeComponent', () => {
    let fixture: ComponentFixture<StudentExamWorkingTimeComponent>;
    let comp: StudentExamWorkingTimeComponent;

    let exam: Exam;
    let studentExam: StudentExam;
    const regularWorkingTime = 7200;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [StudentExamWorkingTimeComponent, MockPipe(ArtemisDurationFromSecondsPipe)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentExamWorkingTimeComponent);
                comp = fixture.componentInstance;

                exam = new Exam();
                exam.startDate = dayjs('2022-01-06T13:00:00Z');
                exam.endDate = dayjs('2022-01-06T15:00:00Z');

                studentExam = new StudentExam();
                studentExam.exam = exam;
            });
    });

    const setExamWithWorkingTime = (workingTimeSeconds: number) => {
        studentExam.workingTime = workingTimeSeconds;
        comp.studentExam = studentExam;
        comp.ngOnInit();
    };

    it('should have a difference of zero if the student working time is the regular working time', () => {
        setExamWithWorkingTime(regularWorkingTime);
        expect(comp.percentDifference).toBe(0);
    });

    it('should have a positive difference if the student is allowed to work longer', () => {
        setExamWithWorkingTime(regularWorkingTime + 3600);
        expect(comp.percentDifference).toBe(50);
    });

    it('should have a negative difference if the student has a shorter working time', () => {
        setExamWithWorkingTime(regularWorkingTime - 1800);
        expect(comp.percentDifference).toBe(-25);
    });

    it('should correctly calculate working time extensions over double the time', () => {
        setExamWithWorkingTime(regularWorkingTime * 3);
        expect(comp.percentDifference).toBe(200);
    });

    it('should only count exams as test runs if they explicitly are', () => {
        comp.studentExam = studentExam;

        studentExam.testRun = undefined;
        comp.ngOnInit();
        expect(comp.isTestRun).toBeFalse();

        studentExam.testRun = false;
        comp.ngOnInit();
        expect(comp.isTestRun).toBeFalse();

        studentExam.testRun = true;
        comp.ngOnInit();
        expect(comp.isTestRun).toBeTrue();
    });
});
