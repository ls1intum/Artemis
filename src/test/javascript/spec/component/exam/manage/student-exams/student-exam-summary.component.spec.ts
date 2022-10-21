import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary.component';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';
import { MockComponent } from 'ng-mocks';
import { of } from 'rxjs';

describe('StudentExamSummaryComponent', () => {
    let fixture: ComponentFixture<StudentExamSummaryComponent>;
    let component: StudentExamSummaryComponent;

    const courseValue = { id: 1 } as Course;
    const examValue = { course: courseValue, id: 2 } as Exam;
    const studentExamValue = { exam: examValue, id: 3 } as StudentExam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [StudentExamSummaryComponent, MockComponent(ExamParticipationSummaryComponent)],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { data: of({ studentExam: { studentExam: studentExamValue } }) },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentExamSummaryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.studentExam).toEqual(studentExamValue);
    });
});
