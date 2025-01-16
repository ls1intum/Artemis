import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/entities/exam/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { TestExamWorkingTimeComponent } from '../../../../../../main/webapp/app/exam/shared/testExam-workingTime/test-exam-working-time.component';
import { round } from 'app/shared/util/utils';
import { ArtemisTestModule } from '../../../test.module';

describe('TestExamWorkingTimeComponent', () => {
    let fixture: ComponentFixture<TestExamWorkingTimeComponent>;
    let comp: TestExamWorkingTimeComponent;

    let exam: Exam;
    let studentExam: StudentExam;
    const currentDate = dayjs();

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TestExamWorkingTimeComponent);
                comp = fixture.componentInstance;

                exam = new Exam();
                exam.startDate = currentDate.subtract(4, 'hour');
                exam.endDate = currentDate;
                exam.workingTime = 3 * 3600;
                exam.testExam = true;

                studentExam = new StudentExam();
                studentExam.exam = exam;
                studentExam.submitted = true;
                studentExam.started = true;
                studentExam.startedDate = currentDate.subtract(4, 'hour');
                studentExam.submissionDate = currentDate.subtract(2, 'hour');
                studentExam.workingTime = exam.workingTime;
            });
    });

    it('should have a difference of 0 if the studentExam is not submitted', () => {
        studentExam.submitted = false;
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if the studentExam is linked to a RealExam', () => {
        studentExam.exam!.testExam = false;
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if started is false', () => {
        studentExam.started = false;
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if the startedDate is not defined', () => {
        studentExam.startedDate = undefined;
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if the submissionDate is not defined', () => {
        studentExam.submissionDate = undefined;
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should calculate the usedWorkingTime correctly', () => {
        // For submitted Exams, the individualWorkingTime time should be calculated
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.usedWorkingTime).toBe(2 * 3600);
    });

    it('should calculate the percentUsedWorkingTime correctly', () => {
        // For submitted Exams, the individualWorkingTime time should be calculated
        const percentUsedWorkingTime = round(((2 * 3600) / (3 * 3600)) * 100, 2);
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(percentUsedWorkingTime);
    });

    it('usedWorkingTime should not exceed the defaultWorkingTime', () => {
        studentExam.submissionDate = currentDate;
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.usedWorkingTime).toBe(3 * 3600);
    });

    it('should not exceed 100% in the usedWorkingTime', () => {
        studentExam.submissionDate = currentDate;
        comp.studentExam = studentExam;
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(100);
    });
});
