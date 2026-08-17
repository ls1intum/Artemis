import { Component, computed, effect, inject, signal, DestroyRef } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterOutlet } from '@angular/router';
import { of, Subscription, distinctUntilChanged } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { isRealExam } from 'app/exam/overview/exam.utils';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { StudentExamOrDTO } from 'app/exam/shared/entities/student-exam-dto.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { ExamParticipationComponent } from 'app/exam/overview/exam-participation/exam-participation.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData } from 'app/foundation/types/sidebar';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { SidebarView } from 'app/course/shared/sidebar-view.interface';
import { CourseOverviewTabDataService } from 'app/course/overview/services/course-overview-tab-data.service';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';
import { cloneDeep } from 'lodash-es';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    real: { entityData: [] },
    test: { entityData: [] },
    attempt: { entityData: [] },
};

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    real: false,
    test: false,
    attempt: false,
};

const DEFAULT_SHOW_ALWAYS: CollapseState = {
    real: false,
    test: false,
    attempt: false,
};

@Component({
    selector: 'jhi-course-exams',
    templateUrl: './course-exams.component.html',
    styleUrls: ['./course-exams.component.scss'],
    imports: [SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseExamsComponent implements SidebarView {
    private route = inject(ActivatedRoute);
    private serverDateService = inject(ArtemisServerDateService);
    private examParticipationService = inject(ExamParticipationService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private router = inject(Router);
    private courseOverviewTabDataService = inject(CourseOverviewTabDataService);
    private courseTabRefreshService = inject(CourseTabRefreshService);
    private destroyRef = inject(DestroyRef);

    private readonly parentParams = toSignal(this.route.parent?.params ?? of<Params>({}), { initialValue: this.route.parent?.snapshot?.params ?? {} });
    private readonly childParams = toSignal(this.route.firstChild?.params ?? of<Params>({}), { initialValue: this.route.firstChild?.snapshot?.params ?? {} });
    readonly courseId = computed(() => Number(this.parentParams()['courseId'] ?? 0));

    private examsSubscription?: Subscription;
    private studentExamTestExamInitialFetchSubscription?: Subscription;

    /** The exams of the course visible to the user, loaded by this tab rather than shipped with the course. */
    readonly exams = signal<ExamForOverview[] | undefined>(undefined);

    private readonly visibleExams = computed(
        () => this.exams()?.filter((exam) => this.isVisible(exam))
            .sort((exam1, exam2) => this.sortExamsByStartDate(exam1, exam2)) ?? [],
    );

    protected readonly realExamsOfCourse = computed(() => this.visibleExams().filter((exam) => isRealExam(exam)));
    protected readonly realExamWorkingTimeByExamId = signal<Map<number, number>>(new Map());

    protected readonly testExamsOfCourse = computed(() => this.visibleExams().filter((exam) => !isRealExam(exam)));

    readonly sidebarData = computed<SidebarData | undefined>(() => this.buildSidebarData());

    readonly isCollapsed = signal(this.courseOverviewService.getSidebarCollapseStateFromStorage('exam'));
    readonly examSelected = signal(true);
    readonly pageTitle = signal<string>('');
    readonly isExamStarted = toSignal(this.examParticipationService.examIsStarted$, { initialValue: false });

    readonly testStudentExamsLoaded = signal(false);
    // Simulation test exams need their attempt list before the participation component decides whether to request another attempt.
    readonly canRenderSelectedExam = computed(() => {
        const selectedExamId = Number(this.childParams()['examId']);
        const selectedExam = this.exams()?.find((exam) => exam.id === selectedExamId);
        return selectedExam?.examMode !== ExamMode.TEST_WITH_SIMULATION || this.testStudentExamsLoaded();
    });

    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    private readonly activeExamDetails = signal<ExamParticipationComponent | undefined>(undefined);
    protected readonly activeExamDetailsSidebarSync = effect(() => this.activeExamDetails()?.setSidebarToggle(this.isCollapsed(), () => this.toggleSidebar()));

    /**
     * subscribe to changes in the course and fetch course by the path parameter
     */
    constructor() {
        toObservable(this.courseId).pipe(distinctUntilChanged(), takeUntilDestroyed()).subscribe(
            (courseId) => this.activateCourse(courseId)
        );

        // Selecting the exams tab while already on it acts as a refresh
        this.courseTabRefreshService.reselections(this.route).pipe(takeUntilDestroyed())
            .subscribe(() => this.loadExams(this.courseId()));
    }

    /** Cancels all course-scoped work, clears rendered state, and loads the newly active course. */
    private activateCourse(courseId: number): void {
        this.examParticipationService.testStudentExams.set([]);
        this.testStudentExamsLoaded.set(false);
        this.realExamWorkingTimeByExamId.set(new Map());

        this.exams.set(undefined);

        this.loadExams(courseId);
    }

    /**
     * Fetches the exams of the course without touching what is currently rendered.
     *
     * Separate from {@link activateCourse}, which clears the rendered state because it is switching to a different
     * course. Re-selecting the exams tab is a refresh of the same course: the exam list stays on screen until the new
     * one arrives, so the sidebar does not flash empty.
     *
     * @param courseId the course to load the exams of
     */
    private loadExams(courseId: number): void {
        // test exams have multiple attempts, therefore those need to be loaded so the attempts can be viewed by the student
        this.studentExamTestExamInitialFetchSubscription?.unsubscribe();
        this.studentExamTestExamInitialFetchSubscription = this.examParticipationService.loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                next: () => this.testStudentExamsLoaded.set(true),
                error: () => this.testStudentExamsLoaded.set(true),
            });

        this.examsSubscription?.unsubscribe();
        this.examsSubscription = this.courseOverviewTabDataService.loadExamsIfNeeded(courseId)
            .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
                next: (exams) => {
                    this.exams.set(exams);
                    // If no exam is selected navigate to the last selected or upcoming Exam
                    this.navigateToExam();
                },
                error: () => {
                    this.exams.set([]);
                    this.navigateToExam();
                }
            });

        // load real exam working times for the current student as a student may have an adjusted working time
        this.examParticipationService.getRealExamSidebarData(courseId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(
            (studentExams) => this.realExamWorkingTimeByExamId.set(new Map(
                studentExams.map((studentExam) => [studentExam.id ?? 0, studentExam.workingTime ?? 0])
            ))
        );
    }

    private navigateToExam() {
        const upcomingExam = this.courseOverviewService.getUpcomingExam([...this.visibleExams()]);
        const lastSelectedExam = this.getLastSelectedExam();
        const examId = this.route.firstChild?.snapshot.params.examId;
        if (!examId && lastSelectedExam) {
            // First, try to navigate to the last selected exam
            void this.router.navigate([lastSelectedExam], { relativeTo: this.route, replaceUrl: true });
        } else if (!examId && upcomingExam) {
            // Second, try to navigate to the upcoming exam
            void this.router.navigate([upcomingExam.id], { relativeTo: this.route, replaceUrl: true });
        } else {
            // If both is not defined, do not navigate and only set examSelected to true when the examId was found in the client URL
            this.examSelected.set(!!examId);
        }
    }

    /**
     * check for given exam if it is visible
     * @param {Exam} exam
     */
    protected isVisible(exam: Exam): boolean {
        return exam.visibleDate ? dayjs(exam.visibleDate).isBefore(this.serverDateService.now()) : false;
    }

    /**
     * Filters the studentExams for the examId and sorts them according to the studentExam.id in reverse order
     * @param studentExams the student exams to sort
     * @param examId the examId for which the StudentExams should be retrieved
     * @return a by id descending ordered list of studentExams
     */
    protected getStudentExamForExamIdOrderedByIdReverse(studentExams: StudentExamOrDTO[], examId: number): StudentExamOrDTO[] {
        if (!studentExams) {
            return [];
        }
        return studentExams.filter((studentExam) => studentExam.exam?.id === examId && studentExam.startedDate).sort((se1, se2) => se2.id! - se1.id!);
    }

    /**
     * Used for the sort()-function to order the Exams according to their startDate.
     * @param exam1 exam1 for comparison
     * @param exam2 exam2 for comparison
     * @return value for sort()-function
     */
    protected sortExamsByStartDate(exam1: Exam, exam2: Exam): number {
        if (dayjs(exam1.startDate).isBefore(exam2.startDate)) {
            return -1;
        }
        if (dayjs(exam1.startDate).isAfter(exam2.startDate)) {
            return 1;
        }
        return 0;
    }

    protected groupExamsByRealOrTestOrAttempt(realExams: Exam[], testExams: Exam[], testExamAttemptsMap: Map<number, StudentExamOrDTO[]>): AccordionGroups {
        const groupedExamGroups = cloneDeep(DEFAULT_UNIT_GROUPS);

        const realExamWorkingTimeByExamId = this.realExamWorkingTimeByExamId();
        for (const realExam of realExams) {
            const examCardItem = this.courseOverviewService.mapExamToSidebarCardElement(realExam, { workingTime: realExamWorkingTimeByExamId.get(realExam.id!) });
            groupedExamGroups['real'].entityData.push(examCardItem);
        }

        testExams.forEach((testExam) => {
            const examCardItem = this.courseOverviewService.mapExamToSidebarCardElement(testExam, {
                numberOfAttempts: testExamAttemptsMap.get(testExam.id!)?.length ?? 0,
            });
            groupedExamGroups['test'].entityData.push(examCardItem);
            const testExamAttempts = testExamAttemptsMap.get(testExam.id!);
            if (testExamAttempts) {
                testExamAttempts.forEach((attempt, index) => {
                    const attemptNumber = testExamAttempts.length - index;
                    const attemptCardItem = this.courseOverviewService.mapAttemptToSidebarCardElement(attempt, attemptNumber);
                    groupedExamGroups['attempt'].entityData.push(attemptCardItem);
                });
            }
        });
        return groupedExamGroups;
    }

    private getLastSelectedExam(): number | undefined {
        return this.sessionStorageService.retrieve<number>('sidebar.lastSelectedItem.exam.byCourse.' + this.courseId());
    }

    setPageTitle(pageTitle: string): void {
        this.pageTitle.set(pageTitle);
    }

    toggleSidebar() {
        const newState = !this.isCollapsed();
        this.isCollapsed.set(newState);
        this.courseOverviewService.setSidebarCollapseState('exam', newState);
    }

    protected buildSidebarData(): SidebarData | undefined {
        if (!this.exams()) {
            return undefined;
        }

        const sortedRealExams: Exam[] = [...this.realExamsOfCourse()].sort((a, b) => this.sortExamsByStartDate(a, b));
        const sortedTestExams: Exam[] = [...this.testExamsOfCourse()].sort((a, b) => this.sortExamsByStartDate(a, b));

        const testExamAttempts = this.examParticipationService.testStudentExams();
        const testExamAttemptsMap: Map<number, StudentExamOrDTO[]> = new Map();
        for (const testExam of sortedTestExams) {
            const orderedTestExamAttempts = this.getStudentExamForExamIdOrderedByIdReverse(testExamAttempts, testExam.id!);
            const submittedAttempts = orderedTestExamAttempts.filter((attempt) => attempt.submitted);
            testExamAttemptsMap.set(testExam.id!, submittedAttempts);
        }

        const accordionExamGroups = this.groupExamsByRealOrTestOrAttempt(sortedRealExams, sortedTestExams, testExamAttemptsMap);
        const sidebarExams: SidebarCardElement[] = [
            ...accordionExamGroups['real'].entityData,
            ...accordionExamGroups['test'].entityData,
            ...accordionExamGroups['attempt'].entityData,
        ];

        return {
            groupByCategory: true,
            sidebarType: 'exam',
            storageId: 'exam',
            groupedData: accordionExamGroups,
            ungroupedData: sidebarExams,
        };
    }

    onSubRouteActivate(componentRef: unknown) {
        if (componentRef instanceof ExamParticipationComponent) {
            this.activeExamDetails.set(componentRef);
        }
    }

    onSubRouteDeactivate() {
        if (this.route.firstChild) {
            return;
        }
        this.navigateToExam();
    }
}
