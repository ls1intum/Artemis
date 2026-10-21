import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EmbeddedViewRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent } from 'ng-mocks';
import { Course } from 'app/course/shared/entities/course.model';
import { of } from 'rxjs';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary/student-exam-summary.component';
import { ExamResultSummaryComponent } from 'app/exam/overview/summary/exam-result-summary.component';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('StudentExamSummaryComponent', () => {
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
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(StudentExamSummaryComponent, {
                remove: { imports: [ExamResultSummaryComponent] },
                add: { imports: [MockComponent(ExamResultSummaryComponent)] },
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
        expect(component.studentExam()).toEqual(studentExamValue);
    });

    describe('title bar', () => {
        let titleView: EmbeddedViewRef<unknown>;

        /** Renders the template the component registers for the course title bar, as the title bar does. */
        const renderTitle = (): HTMLElement => {
            fixture.detectChanges();
            const template = TestBed.inject(CourseTitleBarService).titleTemplate();
            expect(template).toBeDefined();
            titleView = template!.createEmbeddedView({});
            titleView.detectChanges();
            return titleView.rootNodes[0] as HTMLElement;
        };

        afterEach(() => {
            titleView?.destroy();
        });

        it('should not show the test run tag for a regular student exam', () => {
            const title = renderTitle();

            expect(title.querySelector('h5')).not.toBeNull();
            expect(title.querySelector('#testRunRibbon')).toBeNull();
        });

        it('should show the test run tag next to the title for a test run', () => {
            const title = renderTitle();
            expect(title.querySelector('#testRunRibbon')).toBeNull();

            const testRun = new StudentExam();
            testRun.id = studentExamValue.id;
            testRun.testRun = true;
            component.studentExam.set(testRun);
            titleView.detectChanges();

            const tag = title.querySelector('tumaet-ui-tag#testRunRibbon');
            expect(tag).not.toBeNull();
            expect(tag!.querySelector('[data-severity="danger"]')).not.toBeNull();
            expect(tag!.querySelector('[jhiTranslate="artemisApp.examManagement.testRun.testRun"]')).not.toBeNull();
            // The tag is a sibling of the title, in the same flex row, so it sits next to it instead of overlaying the summary.
            expect(tag!.previousElementSibling).toBe(title.querySelector('h5'));
        });
    });
});
