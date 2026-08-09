import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { CourseExamsComponent } from 'app/exam/shared/course-exams/course-exams.component';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { SearchFilterPipe } from 'app/foundation/pipes/search-filter.pipe';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';

describe('CourseExamsComponent', () => {
    let component: CourseExamsComponent;
    let componentFixture: ComponentFixture<CourseExamsComponent>;
    let courseStorageService: CourseStorageService;
    let courseOverviewService: CourseOverviewService;
    let examParticipationService: ExamParticipationService;
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
        router.navigate.mockClear();
        router.navigate.mockImplementation(() => Promise.resolve(true));

        TestBed.configureTestingModule({
            imports: [
                RouterModule.forRoot([]),
                MockModule(FormsModule),
                MockModule(ReactiveFormsModule),
                MockDirective(TranslateDirective),
                CourseExamsComponent,
                SidebarComponent,
                MockComponent(SearchFilterComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(SearchFilterPipe),
            ],
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
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseExamsComponent);
                component = componentFixture.componentInstance;

                courseStorageService = TestBed.inject(CourseStorageService);
                examParticipationService = TestBed.inject(ExamParticipationService);
                courseOverviewService = TestBed.inject(CourseOverviewService);
                (examParticipationService as any).examIsStarted$ = of(false);
                examParticipationService.shouldUpdateTestExamsObservable = new BehaviorSubject<boolean>(false).asObservable();
                examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
                vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({});
                // The exams tab loads the visible exams itself instead of reading them off the course
                vi.spyOn(examParticipationService, 'getExamsForOverview').mockReturnValue(
                    of([visibleRealExam1, visibleRealExam2, notVisibleRealExam, visibleTestExam1, visibleTestExam2, notVisibleTestExam] as ExamForOverview[]),
                );
                vi.spyOn(TestBed.inject(ExamParticipationService), 'loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage').mockReturnValue(
                    of([studentExamForExam3AndSubmitted, studentExamForExam3AndNotSubmitted, studentExamForExam4AndSubmitted]) as Observable<StudentExam[]>,
                );
                vi.spyOn(examParticipationService, 'getRealExamSidebarData').mockReturnValue(of([]));
            });
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('exam should be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(visibleRealExam1)).toBe(true);
    });

    it('exam should not be visible', () => {
        componentFixture.detectChanges();
        expect(component.isVisible(notVisibleRealExam)).toBe(false);
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

    it('should reuse the loaded exams when the routed tab component is recreated during the same course visit', () => {
        component.ngOnInit();
        const firstExams = component.exams();
        component.ngOnDestroy();
        const returnedFixture = TestBed.createComponent(CourseExamsComponent);
        const returnedComponent = returnedFixture.componentInstance;
        returnedComponent.ngOnInit();

        expect(examParticipationService.getExamsForOverview).toHaveBeenCalledExactlyOnceWith(1);
        expect(returnedComponent.exams()).toBe(firstExams);
        expect(returnedComponent.realExamsOfCourse).toEqual([visibleRealExam2, visibleRealExam1]);
        expect(returnedComponent.testExamsOfCourse).toEqual([visibleTestExam2, visibleTestExam1]);

        returnedComponent.ngOnDestroy();
        returnedFixture.destroy();
    });

    it('should cancel, reset, and reload when Angular reuses the tab for another course', async () => {
        const params = new BehaviorSubject({ courseId: '1' });
        const firstExams = new Subject<ExamForOverview[]>();
        const secondExams = new Subject<ExamForOverview[]>();
        const firstStudentExams = new Subject<StudentExam[]>();
        const secondStudentExams = new Subject<StudentExam[]>();
        const firstRealExamSidebar = new Subject<Exam[]>();
        const secondRealExamSidebar = new Subject<Exam[]>();
        (TestBed.inject(ActivatedRoute) as any).parent.params = params.asObservable();
        const examsSpy = vi.spyOn(examParticipationService, 'getExamsForOverview').mockImplementation((courseId) => (courseId === 1 ? firstExams : secondExams));
        const studentExamsSpy = vi
            .spyOn(examParticipationService, 'loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage')
            .mockImplementation((courseId) => (courseId === 1 ? firstStudentExams : secondStudentExams));
        vi.spyOn(courseStorageService, 'getCourse').mockImplementation((courseId) => ({ id: courseId, title: `Course ${courseId}` }) as Course);
        vi.spyOn(examParticipationService, 'getRealExamSidebarData').mockImplementation((courseId) => (courseId === 1 ? firstRealExamSidebar : secondRealExamSidebar));
        const oldExam = { ...visibleRealExam1, id: 41 } as ExamForOverview;
        const newExam = { ...visibleRealExam2, id: 42 } as ExamForOverview;

        component.ngOnInit();
        firstExams.next([oldExam]);
        firstStudentExams.next([studentExamForExam3AndSubmitted]);
        component.setPageTitle('Old exam');
        expect(component.exams()).toEqual([oldExam]);
        expect(Object.values(component.accordionExamGroups).some((group) => group.entityData.length > 0)).toBe(true);
        expect(firstExams.observed).toBe(true);
        expect(firstStudentExams.observed).toBe(true);

        params.next({ courseId: '2' });

        expect(component.courseId()).toBe(2);
        expect(component.course()?.id).toBe(2);
        expect(component.exams()).toBeUndefined();
        expect(component.realExamsOfCourse).toEqual([]);
        expect(component.testExamsOfCourse).toEqual([]);
        expect(component.expandAttemptsMap.size).toBe(0);
        expect(component.sidebarExams).toEqual([]);
        expect(component.sidebarData()).toBeUndefined();
        expect(Object.values(component.accordionExamGroups).every((group) => group.entityData.length === 0)).toBe(true);
        expect(component.pageTitle()).toBe('');
        expect(firstExams.observed).toBe(false);
        expect(firstStudentExams.observed).toBe(false);
        expect(secondExams.observed).toBe(true);
        expect(secondStudentExams.observed).toBe(true);
        expect(examsSpy).toHaveBeenNthCalledWith(1, 1);
        expect(examsSpy).toHaveBeenNthCalledWith(2, 2);
        expect(studentExamsSpy).toHaveBeenNthCalledWith(1, 1);
        expect(studentExamsSpy).toHaveBeenNthCalledWith(2, 2);

        firstExams.next([{ ...oldExam, title: 'Stale exam' }]);
        secondExams.next([newExam]);
        secondStudentExams.next([]);
        firstRealExamSidebar.next([{ id: 91 } as Exam]);
        firstRealExamSidebar.complete();
        secondRealExamSidebar.next([{ id: 92 } as Exam]);
        secondRealExamSidebar.complete();
        await Promise.resolve();
        await Promise.resolve();
        params.next({ courseId: '2' });

        expect(component.exams()).toEqual([newExam]);
        expect(component.studentExamsForRealExams.has(91)).toBe(false);
        expect(component.studentExamsForRealExams.has(92)).toBe(true);
        expect(examsSpy).toHaveBeenCalledTimes(2);
        expect(studentExamsSpy).toHaveBeenCalledTimes(2);
    });

    it('should replace and append test-exam attempts received while the tab is active', () => {
        const shouldUpdate = new BehaviorSubject(false);
        const currentStudentExam = new Subject<StudentExam>();
        examParticipationService.shouldUpdateTestExamsObservable = shouldUpdate.asObservable();
        examParticipationService.currentlyLoadedStudentExam = currentStudentExam;
        const resetUpdateSpy = vi.spyOn(examParticipationService, 'setShouldUpdateTestExams');
        const prepareSidebarSpy = vi.spyOn(component, 'prepareSidebarData');
        component.ngOnInit();
        const replacement = {
            ...studentExamForExam3AndSubmitted,
            startedDate: dayjs().subtract(30, 'minutes'),
            exam: { ...visibleTestExam1, course: { id: 1 } as Course },
        } as StudentExam;
        const added = {
            ...studentExamForExam3AndSubmitted,
            id: 99,
            startedDate: dayjs().subtract(10, 'minutes'),
            exam: { ...visibleTestExam1, course: { id: 1 } as Course },
        } as StudentExam;

        currentStudentExam.next(replacement);
        shouldUpdate.next(true);
        currentStudentExam.next(added);

        const attempts = component.getStudentExamForExamIdOrderedByIdReverse(visibleTestExam1.id!);
        expect(attempts).toHaveLength(3);
        expect(attempts).toEqual([added, studentExamForExam3AndNotSubmitted, replacement]);
        expect(attempts).not.toContain(studentExamForExam3AndSubmitted);
        expect(resetUpdateSpy).toHaveBeenCalledTimes(2);
        expect(resetUpdateSpy).toHaveBeenNthCalledWith(1, false);
        expect(resetUpdateSpy).toHaveBeenNthCalledWith(2, false);
        expect(prepareSidebarSpy).toHaveBeenCalledTimes(4);
    });

    it('should expose an empty exam list and a non-selected state when the overview request fails', () => {
        vi.spyOn(examParticipationService, 'getExamsForOverview').mockReturnValue(throwError(() => new Error('network')));

        component.ngOnInit();

        expect(component.exams()).toEqual([]);
        expect(component.examSelected()).toBe(false);
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should navigate to the last selected exam before considering an upcoming exam', () => {
        vi.spyOn(TestBed.inject(SessionStorageService), 'retrieve').mockReturnValue(42);
        const upcomingSpy = vi.spyOn(courseOverviewService, 'getUpcomingExam').mockReturnValue({ id: 43 } as Exam);

        component.navigateToExam();

        expect(upcomingSpy).toHaveBeenCalledWith([]);
        expect(router.navigate).toHaveBeenCalledExactlyOnceWith([42], { relativeTo: TestBed.inject(ActivatedRoute), replaceUrl: true });
    });

    it('should navigate to the upcoming exam when none was selected previously', () => {
        vi.spyOn(TestBed.inject(SessionStorageService), 'retrieve').mockReturnValue(undefined);
        vi.spyOn(courseOverviewService, 'getUpcomingExam').mockReturnValue({ id: 43 } as Exam);

        component.navigateToExam();

        expect(router.navigate).toHaveBeenCalledExactlyOnceWith([43], { relativeTo: TestBed.inject(ActivatedRoute), replaceUrl: true });
    });

    it('should keep the selected exam from the URL without redirecting', () => {
        (TestBed.inject(ActivatedRoute) as any).firstChild = { snapshot: { params: { examId: 1 } } };

        component.navigateToExam();

        expect(component.examSelected()).toBe(true);
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should stop checking a latest test-exam attempt once its working time expires', () => {
        vi.useFakeTimers();
        const attempt = { started: true, startedDate: dayjs(), exam: visibleTestExam1 } as StudentExam;
        const workingTimeSpy = vi.spyOn(component, 'isWithinWorkingTime').mockImplementation(() => {
            component.withinWorkingTime = false;
        });

        component.calculateIndividualWorkingTimeForTestExams(attempt, true);
        vi.advanceTimersByTime(1000);

        expect(workingTimeSpy).toHaveBeenCalledExactlyOnceWith(attempt, visibleTestExam1);
        expect(component.studentExamState?.closed).toBe(true);
    });

    it('should calculate whether an active test-exam attempt remains within its individual working time', () => {
        const attempt = { started: true, submitted: false, startedDate: dayjs().subtract(30, 'minutes') } as StudentExam;
        const exam = { workingTime: 60 * 60 } as Exam;

        component.isWithinWorkingTime(attempt, exam);
        expect(component.withinWorkingTime).toBe(true);

        attempt.startedDate = dayjs().subtract(2, 'hours');
        component.isWithinWorkingTime(attempt, exam);
        expect(component.withinWorkingTime).toBe(false);
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

    it('should correctly update new exams', async () => {
        const newExam = {
            id: 42,
            visibleDate: dayjs().subtract(1, 'minutes'),
        } as Exam;
        const course = new Course();
        course.exams = [visibleRealExam1, visibleRealExam2];
        component.course.set(course);

        vi.spyOn(examParticipationService, 'getRealExamSidebarData').mockReturnValue(of([visibleRealExam1, visibleRealExam2, newExam]));
        examParticipationService.currentlyLoadedStudentExam = new Subject<StudentExam>();
        examParticipationService.shouldUpdateTestExamsObservable = new BehaviorSubject<boolean>(false).asObservable();
        component.ngOnInit();
        // Allow promise from lastValueFrom in updateExams() to resolve
        await Promise.resolve();
        await Promise.resolve();
        expect(component.studentExamsForRealExams.has(newExam.id!)).toBe(true);
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
        expect(componentFixture.nativeElement.querySelector('#exam-sidebar-test').hidden).toBe(true);

        component.isExamStarted.set(false);
        componentFixture.changeDetectorRef.detectChanges();
        expect(componentFixture.nativeElement.querySelector('#exam-sidebar-test').hidden).toBe(false);
    });

    it('should group all exams as test when all exams are test exams', () => {
        const testExams: Exam[] = [
            { id: 1, title: 'Test Exam 1', testExam: true } as Exam,
            { id: 2, title: 'Test Exam 2', testExam: true } as Exam,
            { id: 3, title: 'Test Exam 3', testExam: true } as Exam,
        ];

        vi.spyOn(courseOverviewService, 'mapExamToSidebarCardElement');
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

        vi.spyOn(courseOverviewService, 'mapExamToSidebarCardElement');
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
        component.isCollapsed.set(false);
        component.toggleSidebar();
        expect(component.isCollapsed()).toBe(true);

        component.toggleSidebar();
        expect(component.isCollapsed()).toBe(false);
    });

    it('should not update sidebarData if there is no exam', () => {
        const course = new Course();
        course.exams = undefined;
        component.course.set(course);

        const updateSidebarDataStub = vi.spyOn(component, 'updateSidebarData');
        component.prepareSidebarData();
        expect(updateSidebarDataStub).not.toHaveBeenCalledOnce();
    });
});
