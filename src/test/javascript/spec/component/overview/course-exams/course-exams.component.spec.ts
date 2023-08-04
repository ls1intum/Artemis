import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import dayjs from 'dayjs/esm';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseExamAttemptReviewDetailComponent } from 'app/overview/course-exams/course-exam-attempt-review-detail/course-exam-attempt-review-detail.component';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

describe('CourseExamsComponent', () => {
    let component: CourseExamsComponent;
    let componentFixture: ComponentFixture<CourseExamsComponent>;
    let courseStorageService: CourseStorageService;
    let subscribeToCourseUpdates: jest.SpyInstance;

    const visibleRealExam1 = {
        id: 1,
        visibleDate: dayjs().subtract(1, 'days'),
        startDate: dayjs().subtract(30, 'minutes'),
        testExam: false,
    } as Exam;

    const visibleRealExam2 = {
        id: 2,

        visibleDate: dayjs().subtract(2, 'days'),
        startDate: dayjs().subtract(1, 'days'),
        testExam: false,
    } as Exam;

    const notVisibleRealExam = {
        id: 3,
        visibleDate: dayjs().add(2, 'days'),
        startDate: dayjs().add(1, 'days'),
        testExam: false,
    } as Exam;

    const visibleTestExam1 = {
        id: 11,
        visibleDate: dayjs().subtract(1, 'days'),
        startDate: dayjs().subtract(30, 'minutes'),
        testExam: true,
    } as Exam;

    const visibleTestExam2 = {
        id: 12,
        visibleDate: dayjs().subtract(4, 'days'),
        startDate: dayjs().subtract(1, 'days'),
        testExam: true,
    } as Exam;

    const notVisibleTestExam = {
        id: 13,
        visibleDate: dayjs().add(2, 'days'),
        startDate: dayjs().add(1, 'days'),
        testExam: true,
    } as Exam;

    const studentExamForExam3AndSubmitted = {
        id: 11,
        started: true,
        startedDate: dayjs().subtract(2, 'hour'),
        submitted: true,
        submissionDate: dayjs().subtract(1, 'hour'),
        exam: visibleTestExam1,
    } as StudentExam;

    const studentExamForExam3AndNotSubmitted = {
        id: 12,
        started: true,
        startedDate: dayjs().subtract(2, 'hour'),
        exam: visibleTestExam1,
    } as StudentExam;

    const studentExamForExam4AndSubmitted = {
        id: 13,
        started: true,
        submitted: true,
        submissionDate: dayjs().subtract(1, 'hour'),
        exam: visibleTestExam2,
    } as StudentExam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExamsComponent, MockComponent(CourseExamDetailComponent), MockComponent(CourseExamAttemptReviewDetailComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: ActivatedRoute, useValue: { parent: { params: of(1) } } },
                MockProvider(CourseStorageService),
                MockProvider(ArtemisServerDateService),
                MockProvider(ExamParticipationService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamsComponent);
                component = componentFixture.componentInstance;

                courseStorageService = TestBed.inject(CourseStorageService);
                subscribeToCourseUpdates = jest.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of());
                jest.spyOn(courseStorageService, 'getCourse').mockReturnValue({
                    exams: [visibleRealExam1, visibleRealExam2, notVisibleRealExam, visibleTestExam1, visibleTestExam2, notVisibleTestExam],
                });
                jest.spyOn(TestBed.inject(ExamParticipationService), 'loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage').mockReturnValue(
                    of([studentExamForExam3AndSubmitted, studentExamForExam3AndNotSubmitted, studentExamForExam4AndSubmitted]) as Observable<StudentExam[]>,
                );
            });
    });

    it('exam should be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(visibleRealExam1)).toBeTrue();
    });

    it('exam should not be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(notVisibleRealExam)).toBeFalse();
    });

    it('should correctly return StudentExams by id in reverse order', () => {
        componentFixture.detectChanges();
        const resultArray = [studentExamForExam3AndNotSubmitted, studentExamForExam3AndSubmitted];
        expect(component.getStudentExamForExamIdOrderedByIdReverse(11)).toEqual(resultArray);
    });

    it('should correctly initialize the expandAttemptsMap', () => {
        const expectedMap = new Map<number, boolean>();
        expectedMap.set(visibleTestExam1.id!, false);
        expectedMap.set(visibleTestExam2.id!, false);

        // Map gets initialized in OnInit-Method
        component.ngOnInit();

        expect(component.expandAttemptsMap).toEqual(expectedMap);
    });

    it('should correctly switch boolean value in expandAttemptsMap', () => {
        const expectedMap = new Map<number, boolean>();
        expectedMap.set(visibleTestExam1.id!, true);
        expectedMap.set(visibleTestExam2.id!, false);

        // Map gets initialized in OnInit-Method
        component.ngOnInit();
        component.changeExpandAttemptList(visibleTestExam1.id!);

        expect(component.expandAttemptsMap).toEqual(expectedMap);
    });

    it('should correctly update new exams', () => {
        const expectedMap = new Map<number, boolean>();
        expectedMap.set(visibleTestExam1.id!, false);
        expectedMap.set(visibleTestExam2.id!, false);
        expectedMap.set(42, false);

        let updateHandler: (course: Course) => void = () => {};
        subscribeToCourseUpdates.mockReturnValue({
            subscribe: (handler: (course: Course) => void): void => {
                updateHandler = handler;
            },
        });

        component.ngOnInit();

        const newExam = {
            id: 42,
            visibleDate: dayjs().subtract(1, 'minutes'),
            testExam: true,
        } as Exam;
        updateHandler({ exams: [visibleRealExam1, visibleRealExam2, notVisibleRealExam, visibleTestExam1, visibleTestExam2, notVisibleTestExam, newExam] });

        expect(component.expandAttemptsMap).toEqual(expectedMap);
        expect(component.testExamsOfCourse).toContain(newExam);
    });

    it('should correctly return visible real exams ordered according to startedDate', () => {
        component.ngOnInit();
        const resultArray = [visibleRealExam2, visibleRealExam1];
        expect(component.realExamsOfCourse).toEqual(resultArray);
    });

    it('should correctly return visible test exams ordered according to startedDate', () => {
        component.ngOnInit();
        const resultArray = [visibleTestExam2, visibleTestExam1];
        expect(component.testExamsOfCourse).toEqual(resultArray);
    });
});
