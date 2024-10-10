import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { Exam } from 'app/entities/exam/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import dayjs from 'dayjs/esm';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CourseExamsComponent', () => {
    let component: CourseExamsComponent;
    let componentFixture: ComponentFixture<CourseExamsComponent>;
    let courseStorageService: CourseStorageService;
    let courseOverviewService: CourseOverviewService;
    let examParticipationService: ExamParticipationService;
    let subscribeToCourseUpdates: jest.SpyInstance;
    const router = new MockRouter();

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
        router.navigate.mockImplementation(() => Promise.resolve(true));

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterModule.forRoot([]), MockModule(FormsModule), MockModule(ReactiveFormsModule), MockDirective(TranslateDirective)],
            declarations: [CourseExamsComponent, SidebarComponent, SearchFilterComponent, MockPipe(ArtemisTranslatePipe), MockPipe(SearchFilterPipe)],
            providers: [
                { provide: Router, useValue: router },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            params: of({ courseId: '1' }),
                        },
                        params: of({ examId: visibleRealExam1.id }),
                    },
                },
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
                examParticipationService = TestBed.inject(ExamParticipationService);
                courseOverviewService = TestBed.inject(CourseOverviewService);
                subscribeToCourseUpdates = jest.spyOn(courseStorageService, 'subscribeToCourseUpdates').mockReturnValue(of());
                (examParticipationService as any).examIsStarted$ = of(false);
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

    it('should display/hide sidebar if exam is started/over', () => {
        (examParticipationService as any).examIsStarted$ = of(true);
        componentFixture.detectChanges();
        expect(componentFixture.nativeElement.querySelector('#exam-sidebar-test').hidden).toBeTrue();

        component.isExamStarted = false;
        componentFixture.detectChanges();
        expect(componentFixture.nativeElement.querySelector('#exam-sidebar-test').hidden).toBeFalse();
    });

    it('should group all exams as test when all exams are test exams', () => {
        const testExams: Exam[] = [
            { id: 1, title: 'Test Exam 1', testExam: true } as Exam,
            { id: 2, title: 'Test Exam 2', testExam: true } as Exam,
            { id: 3, title: 'Test Exam 3', testExam: true } as Exam,
        ];

        jest.spyOn(courseOverviewService, 'mapExamToSidebarCardElement');
        const groupedExams = component.groupExamsByRealOrTest([], testExams);

        expect(groupedExams['real'].entityData).toHaveLength(0);
        expect(groupedExams['test'].entityData).toHaveLength(3);
        expect(courseOverviewService.mapExamToSidebarCardElement).toHaveBeenCalledTimes(3);
        expect(groupedExams['test'].entityData[0].title).toBe('Test Exam 1');
        expect(groupedExams['test'].entityData[1].title).toBe('Test Exam 2');
        expect(groupedExams['test'].entityData[2].title).toBe('Test Exam 3');
    });

    it('should group all exam types correctly and map to sidebar card elements', () => {
        const testExams: Exam[] = [
            { id: 1, title: 'Test Exam 1', testExam: true } as Exam,
            { id: 2, title: 'Test Exam 2', testExam: true } as Exam,
            { id: 3, title: 'Test Exam 3', testExam: true } as Exam,
        ];

        const realExams: Exam[] = [
            { id: 1, title: 'Real Exam 1', testExam: false } as Exam,
            { id: 2, title: 'Real Exam 2', testExam: false } as Exam,
            { id: 3, title: 'Real Exam 3', testExam: false } as Exam,
        ];

        jest.spyOn(courseOverviewService, 'mapExamToSidebarCardElement');
        const groupedExams = component.groupExamsByRealOrTest(realExams, testExams);

        expect(groupedExams['real'].entityData).toHaveLength(3);
        expect(groupedExams['test'].entityData).toHaveLength(3);
        expect(courseOverviewService.mapExamToSidebarCardElement).toHaveBeenCalledTimes(6);
        expect(groupedExams['test'].entityData[0].title).toBe('Test Exam 1');
        expect(groupedExams['test'].entityData[1].title).toBe('Test Exam 2');
        expect(groupedExams['test'].entityData[2].title).toBe('Test Exam 3');
        expect(groupedExams['real'].entityData[0].title).toBe('Real Exam 1');
        expect(groupedExams['real'].entityData[1].title).toBe('Real Exam 2');
        expect(groupedExams['real'].entityData[2].title).toBe('Real Exam 3');
    });

    it('should sort exams by startDate', () => {
        const exams: Exam[] = [
            { id: 1, title: 'Exam 1', startDate: dayjs().subtract(10, 'minutes') } as Exam,
            { id: 2, title: 'Exam 2', startDate: dayjs().subtract(30, 'minutes') } as Exam,
            { id: 3, title: 'Exam 3', startDate: dayjs().subtract(20, 'minutes') } as Exam,
        ];

        const sortedExams = exams.sort((a, b) => component.sortExamsByStartDate(a, b));

        expect(sortedExams[0].id).toBe(2);
        expect(sortedExams[1].id).toBe(3);
        expect(sortedExams[2].id).toBe(1);
    });

    it('should toggle sidebar', () => {
        component.isCollapsed = false;
        component.toggleSidebar();
        expect(component.isCollapsed).toBeTrue();

        component.toggleSidebar();
        expect(component.isCollapsed).toBeFalse();
    });

    it('should not update sidebarData if there is no exam', () => {
        const course = new Course();
        course.exams = undefined;
        component.course = course;

        const updateSidebarDataStub = jest.spyOn(component, 'updateSidebarData');
        component.prepareSidebarData();
        expect(updateSidebarDataStub).not.toHaveBeenCalledOnce();
    });
});
