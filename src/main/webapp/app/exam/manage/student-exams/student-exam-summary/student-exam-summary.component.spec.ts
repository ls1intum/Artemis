import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { MockComponent } from 'ng-mocks';
import { Course } from 'app/course/shared/entities/course.model';
import { of } from 'rxjs';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary/student-exam-summary.component';
import { ExamResultSummaryComponent } from 'app/exam/overview/summary/exam-result-summary.component';

describe('StudentExamSummaryComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<StudentExamSummaryComponent>;
    let component: StudentExamSummaryComponent;

    const courseValue = { id: 1 } as Course;
    const examValue = { course: courseValue, id: 2 } as Exam;
    const studentExamValue = { exam: examValue, id: 3 } as StudentExam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [StudentExamSummaryComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { data: of({ studentExam: { studentExam: studentExamValue } }) },
                },
            ],
        })
            .overrideComponent(StudentExamSummaryComponent, {
                set: {
                    imports: [MockComponent(ExamResultSummaryComponent)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentExamSummaryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.studentExam).toEqual(studentExamValue);
    });
});
