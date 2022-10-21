import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { CourseExamAttemptReviewDetailComponent } from 'app/overview/course-exams/course-exam-attempt-review-detail/course-exam-attempt-review-detail.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockPipe } from 'ng-mocks';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ArtemisTestModule } from '../../../test.module';

describe('CourseExamAttemptReviewDetailComponent', () => {
    let component: CourseExamAttemptReviewDetailComponent;
    let componentFixture: ComponentFixture<CourseExamAttemptReviewDetailComponent>;

    const TestExam3ForTesting = {
        id: 3,
        testExam: true,
        workingTime: 20 * 60,
    } as Exam;

    const studentExamSubmitted = {
        id: 11,
        started: true,
        startedDate: dayjs().subtract(2, 'hour'),
        submitted: true,
        submissionDate: dayjs().subtract(1, 'hour'),
    } as StudentExam;

    const studentExamWithinWorkingTime = {
        id: 11,
        started: true,
        startedDate: dayjs().subtract(10, 'minutes'),
    } as StudentExam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExamAttemptReviewDetailComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(ArtemisDurationFromSecondsPipe)],
            providers: [{ provide: Router, useClass: MockRouter }],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamAttemptReviewDetailComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should correctly determine, if the Exam is within the working time', () => {
        component.studentExam = studentExamSubmitted;
        component.exam = TestExam3ForTesting;
        componentFixture.detectChanges();
        component.ngOnInit();
        component.isWithinWorkingTime();
        expect(component.withinWorkingTime).toBeFalse();

        component.studentExam = studentExamWithinWorkingTime;
        component.exam = TestExam3ForTesting;
        componentFixture.detectChanges();
        component.ngOnInit();
        component.isWithinWorkingTime();
        expect(component.withinWorkingTime).toBeTrue();
    });

    it('should correctly determine the working time left', () => {
        component.studentExam = studentExamSubmitted;
        component.exam = TestExam3ForTesting;
        componentFixture.detectChanges();
        expect(component.workingTimeLeftInSeconds()).toBe(0);

        component.exam = new Exam();
        component.studentExam = studentExamWithinWorkingTime;
        component.exam = TestExam3ForTesting;
        componentFixture.detectChanges();
        // 10min working time should be left
        expect(component.workingTimeLeftInSeconds()).toBeWithin(10 * 60 - 3, 10 * 60);
    });
});
