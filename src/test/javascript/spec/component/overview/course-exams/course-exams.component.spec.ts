import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import dayjs from 'dayjs/esm';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { MockComponent, MockProvider, MockPipe } from 'ng-mocks';
import { of, Observable } from 'rxjs';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CourseExamsComponent', () => {
    let component: CourseExamsComponent;
    let componentFixture: ComponentFixture<CourseExamsComponent>;

    const visibleRealExam = {
        id: 1,
        visibleDate: dayjs().subtract(2, 'days'),
        testExam: false,
    } as Exam;

    const notVisibleRealExam = {
        id: 2,
        visibleDate: dayjs().add(2, 'days'),
        testExam: false,
    } as Exam;

    const TestExam3ForTesting = {
        id: 3,
        testExam: true,
    } as Exam;

    const TestExam4ForTesting = {
        id: 4,
        testExam: true,
    } as Exam;

    const studentExamForExam3AndSubmitted = {
        id: 11,
        started: true,
        startedDate: dayjs().subtract(2, 'hour'),
        submitted: true,
        submissionDate: dayjs().subtract(1, 'hour'),
        exam: TestExam3ForTesting,
    } as StudentExam;

    const studentExamForExam3AndNotSubmitted = {
        id: 12,
        started: true,
        startedDate: dayjs().subtract(2, 'hour'),
        exam: TestExam3ForTesting,
    } as StudentExam;

    const studentExamForExam4AndSubmitted = {
        id: 13,
        started: true,
        submitted: true,
        submissionDate: dayjs().subtract(1, 'hour'),
        exam: TestExam4ForTesting,
    } as StudentExam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CourseExamsComponent, MockComponent(CourseExamDetailComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: ActivatedRoute, useValue: { parent: { params: of(1) } } },
                MockProvider(CourseScoreCalculationService),
                MockProvider(CourseManagementService),
                MockProvider(ArtemisServerDateService),
                MockProvider(ExamParticipationService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamsComponent);
                component = componentFixture.componentInstance;

                jest.spyOn(TestBed.inject(CourseManagementService), 'getCourseUpdates').mockReturnValue(of());
                jest.spyOn(TestBed.inject(CourseScoreCalculationService), 'getCourse').mockReturnValue({ exams: [visibleRealExam, notVisibleRealExam] });
                jest.spyOn(TestBed.inject(ExamParticipationService), 'loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage').mockReturnValue(
                    of([studentExamForExam3AndSubmitted, studentExamForExam3AndNotSubmitted, studentExamForExam4AndSubmitted]) as Observable<StudentExam[]>,
                );
            });
    });

    it('exam should be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(visibleRealExam)).toBeTrue();
    });

    it('exam should not be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(notVisibleRealExam)).toBeFalse();
    });

    it('isTestExam should return false for RealExams', () => {
        componentFixture.detectChanges();
        expect(component.isTestExam(visibleRealExam)).toBeFalse();
    });

    it('isTestExam should return true for TestExams', () => {
        componentFixture.detectChanges();
        expect(component.isTestExam(TestExam3ForTesting)).toBeTrue();
    });

    it('should correctly return StudentExams by id in reverse order', () => {
        componentFixture.detectChanges();
        const resultArray = [studentExamForExam3AndNotSubmitted, studentExamForExam3AndSubmitted];
        expect(component.getStudentExamForExamIdOrderedByIdReverse(3)).toEqual(resultArray);
    });
});
