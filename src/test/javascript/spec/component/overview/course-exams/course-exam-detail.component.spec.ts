import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import dayjs from 'dayjs/esm';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { CourseExamAttemptReviewDetailComponent } from 'app/overview/course-exams/course-exam-attempt-review-detail/course-exam-attempt-review-detail.component';

describe('CourseExamDetailComponent', () => {
    let component: CourseExamDetailComponent;
    let componentFixture: ComponentFixture<CourseExamDetailComponent>;

    const currentDate = dayjs();
    const currentDateMinus60 = currentDate.subtract(60, 'minutes');
    const currentDateMinus35 = currentDate.subtract(35, 'minutes');
    const currentDateMinus30 = currentDate.subtract(30, 'minutes');
    const currentDateMinus20 = currentDate.subtract(20, 'minutes');
    const currentDatePlus5 = currentDate.add(5, 'minutes');
    const currentDatePlus15 = currentDate.add(15, 'minutes');
    const currentDatePlus30 = currentDate.add(30, 'minutes');
    const currentDatePlus60 = currentDate.add(60, 'minutes');
    const currentDatePlus90 = currentDate.add(90, 'minutes');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseExamAttemptReviewDetailComponent,
                CourseExamDetailComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisDurationFromSecondsPipe),
            ],
            providers: [{ provide: Router, useClass: MockRouter }],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamDetailComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should determine the exam state to be undefined', () => {
        component.exam = new Exam();
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('UNDEFINED');
    });

    it('should determine the exam state to be upcoming', () => {
        component.exam = new Exam();
        component.exam.startDate = currentDatePlus15;
        component.exam.endDate = currentDatePlus30;
        component.exam.workingTime = 15 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('UPCOMING');

        component.exam.examStudentReviewStart = currentDatePlus60;
        component.exam.examStudentReviewStart = currentDatePlus90;
        component.updateExamState();
        expect(component.examState).toBe('UPCOMING');
    });

    it('should determine the exam state to be imminent', () => {
        component.exam = new Exam();
        component.exam.startDate = currentDatePlus5;
        component.exam.endDate = currentDatePlus30;
        component.exam.workingTime = 25 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('IMMINENT');
    });

    it('should determine the exam state to be conducting', () => {
        component.exam = new Exam();
        component.exam.startDate = currentDate;
        component.exam.endDate = currentDatePlus15;
        component.exam.workingTime = 15 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('CONDUCTING');
    });

    it('should determine the exam state to be timeExtension', () => {
        component.exam = new Exam();
        component.exam.startDate = currentDateMinus60;
        component.exam.endDate = currentDateMinus30;
        component.exam.workingTime = 30 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('TIMEEXTENSION');
    });

    it('should determine the exam state to be closed', () => {
        component.exam = new Exam();
        component.exam.startDate = currentDateMinus35;
        component.exam.endDate = currentDateMinus30;
        component.exam.workingTime = 5 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('CLOSED');

        component.exam.examStudentReviewStart = currentDatePlus5;
        component.exam.examStudentReviewEnd = currentDatePlus15;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('CLOSED');

        component.exam.startDate = currentDateMinus60;
        component.exam.endDate = currentDateMinus35;
        component.exam.workingTime = 5 * 60;
        component.exam.examStudentReviewStart = currentDateMinus30;
        component.exam.examStudentReviewEnd = currentDateMinus20;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('CLOSED');

        component.exam.testExam = true;
        component.exam.startDate = currentDateMinus35;
        component.exam.endDate = currentDateMinus30;
        component.exam.workingTime = 3 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('CLOSED');
    });

    it('should determine the exam state to be studentReview', () => {
        component.exam = new Exam();
        component.exam.startDate = currentDateMinus35;
        component.exam.endDate = currentDateMinus30;
        component.exam.workingTime = 5 * 60;
        component.exam.examStudentReviewStart = currentDateMinus20;
        component.exam.examStudentReviewEnd = currentDatePlus5;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('STUDENTREVIEW');
    });

    it('should determine the time left to exam start correctly', () => {
        component.exam = new Exam();
        component.exam.startDate = currentDatePlus15;
        component.exam.endDate = currentDatePlus30;
        component.exam.workingTime = 15 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.timeLeftToStart).toBeWithin(15 * 60 - 3, 15 * 60);

        component.exam = new Exam();
        component.exam.startDate = currentDatePlus5;
        component.exam.endDate = currentDatePlus30;
        component.exam.workingTime = 25 * 60;
        component.ngOnInit();
        component.updateExamState();
        expect(component.timeLeftToStart).toBeWithin(5 * 60 - 3, 5 * 60);
    });

    it('should determine the exam state to be no_more_attempts', () => {
        component.exam = new Exam();
        component.maxAttemptsReached = true;
        component.ngOnInit();
        component.updateExamState();
        expect(component.examState).toBe('NO_MORE_ATTEMPTS');
    });
});
