import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import { round } from 'app/shared/util/utils';

describe('TestExamWorkingTimeComponent', () => {
    let fixture: ComponentFixture<TestExamWorkingTimeComponent>;
    let comp: TestExamWorkingTimeComponent;

    let exam: Exam;
    let studentExam: StudentExam;
    const currentDate = dayjs();

    beforeEach(() => {
        TestBed.configureTestingModule({
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
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if the studentExam is linked to a RealExam', () => {
        studentExam.exam!.testExam = false;
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if started is false', () => {
        studentExam.started = false;
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if the startedDate is not defined', () => {
        studentExam.startedDate = undefined;
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should have a difference of 0 if the submissionDate is not defined', () => {
        studentExam.submissionDate = undefined;
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(0);
        expect(comp.usedWorkingTime).toBe(0);
    });

    it('should calculate the usedWorkingTime correctly', () => {
        // For submitted Exams, the individualWorkingTime time should be calculated
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.usedWorkingTime).toBe(2 * 3600);
    });

    it('should calculate the percentUsedWorkingTime correctly', () => {
        // For submitted Exams, the individualWorkingTime time should be calculated
        const percentUsedWorkingTime = round(((2 * 3600) / (3 * 3600)) * 100, 2);
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(percentUsedWorkingTime);
    });

    it('usedWorkingTime should not exceed the defaultWorkingTime', () => {
        studentExam.submissionDate = currentDate;
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.usedWorkingTime).toBe(3 * 3600);
    });

    it('should not exceed 100% in the usedWorkingTime', () => {
        studentExam.submissionDate = currentDate;
        fixture.componentRef.setInput('studentExam', studentExam);
        comp.ngOnInit();
        expect(comp.percentUsedWorkingTime).toBe(100);
    });
});
