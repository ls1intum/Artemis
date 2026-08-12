import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { Subscription, combineLatest, distinctUntilChanged, filter, interval, lastValueFrom, map } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamForOverview } from 'app/exam/shared/entities/exam-for-overview.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { StudentExamOrDTO } from 'app/exam/shared/entities/student-exam-dto.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { faAngleDown, faAngleUp, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { ExamParticipationComponent } from 'app/exam/overview/exam-participation/exam-participation.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData } from 'app/foundation/types/sidebar';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { CourseOverviewTabDataService } from 'app/course/overview/services/course-overview-tab-data.service';
import { CourseTabRefreshService } from 'app/course/overview/services/course-tab-refresh.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';

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
export class CourseExamsComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private courseStorageService = inject(CourseStorageService);
    private serverDateService = inject(ArtemisServerDateService);
    private examParticipationService = inject(ExamParticipationService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private router = inject(Router);
    private courseOverviewTabDataService = inject(CourseOverviewTabDataService);
    private courseTabRefreshService = inject(CourseTabRefreshService);
    private tabReselectionSubscription?: Subscription;

    courseId = signal<number>(0);
    course = signal<Course | undefined>(undefined);
    private parentParamSubscription?: Subscription;
    private examsSubscription?: Subscription;
    private studentExamTestExamInitialFetchSubscription?: Subscription;
    private studentExamTestExamUpdateSubscription?: Subscription;
    private examStartedSubscription?: Subscription;
    // Mixes StudentExamDTO items (from the migrated test-exams-per-user endpoint) with full StudentExam entities
    // (from the not-yet-migrated live-participation stream, see the combineLatest subscription below).
    private studentExams?: StudentExamOrDTO[];
    studentExamsForRealExams = new Map<number, StudentExam>();
    public expandAttemptsMap = new Map<number, boolean>();
    /** The exams of the course visible to the user, loaded by this tab rather than shipped with the course. */
    readonly exams = signal<ExamForOverview[] | undefined>(undefined);
    public realExamsOfCourse: ExamForOverview[] = [];
    public testExamsOfCourse: ExamForOverview[] = [];
    studentExamState?: Subscription;

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;
    faListAlt = faListAlt;

    sortedRealExams?: ExamForOverview[];
    sortedTestExams?: ExamForOverview[];
    testExamMap: Map<number, StudentExamOrDTO[]> = new Map();
    examSelected = signal(true);
    accordionExamGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sidebarData = signal<SidebarData | undefined>(undefined);
    sidebarExams: SidebarCardElement[] = [];
    isCollapsed = signal(false);
    readonly pageTitle = signal<string>('');
    isExamStarted = signal(false);
    withinWorkingTime = false;

    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    private readonly activeExamDetails = signal<ExamParticipationComponent | undefined>(undefined);
    protected readonly activeExamDetailsSidebarSync = effect(() => this.activeExamDetails()?.setSidebarToggle(this.isCollapsed(), () => this.toggleSidebar()));

    /**
     * subscribe to changes in the course and fetch course by the path parameter
     */
    ngOnInit(): void {
        this.isCollapsed.set(this.courseOverviewService.getSidebarCollapseStateFromStorage('exam'));
        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted) => {
            this.isExamStarted.set(isStarted);
        });

        this.studentExamTestExamUpdateSubscription = combineLatest([
            this.examParticipationService.shouldUpdateTestExamsObservable,
            this.examParticipationService.currentlyLoadedStudentExam,
        ])
            .pipe(filter(([shouldUpdate, studentExam]) => shouldUpdate === true && !!studentExam && studentExam.exam?.course?.id === this.courseId()))
            .subscribe(([_, latestExam]) => {
                const index = this.studentExams?.findIndex((se) => se?.id === latestExam?.id) ?? -1;
                if (index !== -1 && this.studentExams) {
                    this.studentExams[index] = latestExam;
                } else {
                    this.studentExams = [...(this.studentExams || []), latestExam];
                }
                this.prepareSidebarData();

                this.examParticipationService.setShouldUpdateTestExams(false);
            });

        this.parentParamSubscription = this.route.parent?.params
            .pipe(
                map((params) => Number(params.courseId)),
                distinctUntilChanged(),
            )
            .subscribe((courseId) => this.activateCourse(courseId));

        // Selecting the exams tab while already on it acts as a refresh
        this.tabReselectionSubscription = this.courseTabRefreshService.reselections(this.route).subscribe(() => this.loadExams(this.courseId()));
    }

    /** Cancels all course-scoped work, clears rendered state, and loads the newly active course. */
    private activateCourse(courseId: number): void {
        this.examsSubscription?.unsubscribe();
        this.studentExamTestExamInitialFetchSubscription?.unsubscribe();
        this.unsubscribeFromExamStateSubscription();

        this.courseId.set(courseId);
        this.course.set(this.courseStorageService.getCourse(courseId));
        this.studentExams = undefined;
        this.studentExamsForRealExams = new Map();
        this.expandAttemptsMap = new Map();
        this.exams.set(undefined);
        this.realExamsOfCourse = [];
        this.testExamsOfCourse = [];
        this.sortedRealExams = undefined;
        this.sortedTestExams = undefined;
        this.testExamMap = new Map();
        this.sidebarExams = [];
        this.sidebarData.set(undefined);
        this.accordionExamGroups = DEFAULT_UNIT_GROUPS;
        this.examSelected.set(true);
        this.pageTitle.set('');
        this.withinWorkingTime = false;

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
        this.studentExamTestExamInitialFetchSubscription?.unsubscribe();
        this.studentExamTestExamInitialFetchSubscription = this.examParticipationService.loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(courseId).subscribe({
            next: (response: StudentExamOrDTO[]) => {
                this.studentExams = response;
                this.prepareSidebarData();
            },
            error: () => {
                // Render the exams without their test-exam attempts rather than letting the failure escape
                this.studentExams = undefined;
                this.prepareSidebarData();
            },
        });

        this.examsSubscription?.unsubscribe();
        this.examsSubscription = this.courseOverviewTabDataService.loadExamsIfNeeded(courseId).subscribe({
            next: (exams) => {
                this.exams.set(exams);
                // The Map is used to store the boolean value, if the attempt-List for one Exam has been expanded or collapsed
                this.expandAttemptsMap = new Map(exams.filter((exam) => exam.testExam && this.isVisible(exam)).map((exam) => [exam.id, false]));
                this.updateExams();
                // updateExams only refreshes the sidebar once its own async student-exam fetch resolves; render what we
                // already have right away so the list is not empty until then
                this.prepareSidebarData();
                // If no exam is selected navigate to the last selected or upcoming Exam
                this.navigateToExam();
            },
            error: () => {
                this.exams.set([]);
                this.navigateToExam();
            },
        });
    }

    navigateToExam() {
        const upcomingExam = this.courseOverviewService.getUpcomingExam([...this.realExamsOfCourse, ...this.testExamsOfCourse]);
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

    private updateExams(): void {
        const loadedExams = this.exams();
        if (loadedExams) {
            const requestedCourseId = this.courseId();
            const exams = loadedExams.filter((exam) => this.isVisible(exam)).sort((se1, se2) => this.sortExamsByStartDate(se1, se2));
            // add new exams to the attempt map
            exams.filter((exam) => exam.testExam && !this.expandAttemptsMap.has(exam.id)).forEach((exam) => this.expandAttemptsMap.set(exam.id, false));

            this.realExamsOfCourse = exams.filter((exam) => !exam.testExam);
            this.testExamsOfCourse = exams.filter((exam) => exam.testExam);
            // get student exams for real exams
            void lastValueFrom(this.examParticipationService.getRealExamSidebarData(requestedCourseId))
                .then((studentExams) => {
                    if (this.courseId() !== requestedCourseId) {
                        return;
                    }
                    studentExams.forEach((exam) => {
                        const studentExam = deepClone(exam) as StudentExam;
                        this.studentExamsForRealExams.set(studentExam.id!, studentExam);
                    });
                    this.prepareSidebarData();
                })
                .catch(() => {
                    // The exam list is already rendered; without their student exams the cards simply show no attempt state
                    if (this.courseId() === requestedCourseId) {
                        this.prepareSidebarData();
                    }
                });
        }
    }

    /**
     * unsubscribe from all subscriptions
     */
    ngOnDestroy(): void {
        this.tabReselectionSubscription?.unsubscribe();
        this.examsSubscription?.unsubscribe();
        if (this.parentParamSubscription) {
            this.parentParamSubscription.unsubscribe();
        }
        this.studentExamTestExamInitialFetchSubscription?.unsubscribe();
        this.studentExamTestExamUpdateSubscription?.unsubscribe();
        this.examStartedSubscription?.unsubscribe();
        this.unsubscribeFromExamStateSubscription();
    }

    /**
     * check for given exam if it is visible
     * @param {Exam} exam
     */
    isVisible(exam: Exam): boolean {
        return exam.visibleDate ? dayjs(exam.visibleDate).isBefore(this.serverDateService.now()) : false;
    }

    /**
     * Filters the studentExams for the examId and sorts them according to the studentExam.id in reverse order
     * @param examId the examId for which the StudentExams should be retrieved
     * @return a by id descending ordered list of studentExams
     */
    getStudentExamForExamIdOrderedByIdReverse(examId: number): StudentExamOrDTO[] {
        if (!this.studentExams) {
            return [];
        }
        return this.studentExams
            .filter(function (studentExam) {
                return studentExam.exam?.id && studentExam.startedDate && studentExam.exam.id === examId && studentExam.startedDate;
            })
            .sort((se1, se2) => se2.id! - se1.id!);
    }

    /**
     * Used to change the entry corresponding to the examId, if the user has expanded the list of attempts for this exam or not
     * @param examId the examId for which the boolean-value should be changed
     */
    changeExpandAttemptList(examId: number) {
        const newValue = !this.expandAttemptsMap.get(examId);
        this.expandAttemptsMap.set(examId, newValue);
    }

    /**
     * Used for the sort()-function to order the Exams according to their startDate.
     * @param exam1 exam1 for comparison
     * @param exam2 exam2 for comparison
     * @return value for sort()-function
     */
    sortExamsByStartDate(exam1: Exam, exam2: Exam): number {
        if (dayjs(exam1.startDate).isBefore(exam2.startDate)) {
            return -1;
        }
        if (dayjs(exam1.startDate).isAfter(exam2.startDate)) {
            return 1;
        }
        return 0;
    }

    groupExamsByRealOrTest(realExams: Exam[], testExams: Exam[]): AccordionGroups {
        const groupedExamGroups = deepClone(DEFAULT_UNIT_GROUPS);

        for (const realExam of realExams) {
            const examCardItem = this.courseOverviewService.mapExamToSidebarCardElement(realExam, this.studentExamsForRealExams.get(realExam.id!));
            groupedExamGroups['real'].entityData.push(examCardItem);
        }
        testExams.forEach((testExam) => {
            const examCardItem = this.courseOverviewService.mapExamToSidebarCardElement(
                testExam,
                this.studentExamsForRealExams.get(testExam.id!),
                this.getNumberOfAttemptsForTestExam(testExam),
            );
            groupedExamGroups['test'].entityData.push(examCardItem);
            const testExamAttempts = this.testExamMap.get(testExam.id!);
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

    getLastSelectedExam(): number | undefined {
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

    updateSidebarData() {
        this.sidebarData.set({
            groupByCategory: true,
            sidebarType: 'exam',
            storageId: 'exam',
            groupedData: this.accordionExamGroups,
            ungroupedData: this.sidebarExams,
        });
    }

    prepareSidebarData() {
        if (!this.exams()) {
            return;
        }

        this.sortedRealExams = this.realExamsOfCourse.sort((a, b) => this.sortExamsByStartDate(a, b));
        this.sortedTestExams = this.testExamsOfCourse.sort((a, b) => this.sortExamsByStartDate(a, b));
        for (const testExam of this.sortedTestExams) {
            const orderedTestExamAttempts = this.getStudentExamForExamIdOrderedByIdReverse(testExam.id);
            orderedTestExamAttempts.forEach((attempt, index) => {
                this.calculateIndividualWorkingTimeForTestExams(attempt, index === 0);
            });
            const submittedAttempts = orderedTestExamAttempts.filter((attempt) => attempt.submitted);
            this.testExamMap.set(testExam.id, submittedAttempts);
        }

        const sidebarRealExams = this.courseOverviewService.mapExamsToSidebarCardElements(this.sortedRealExams, this.getAllStudentExamsForRealExams());
        const sidebarTestExams = this.courseOverviewService.mapExamsToSidebarCardElements(this.sortedTestExams);
        const allStudentExams = this.getAllStudentExams();
        const sidebarTestExamAttempts = this.courseOverviewService.mapTestExamAttemptsToSidebarCardElements(
            allStudentExams,
            this.getIndicesForStudentExams(allStudentExams.length),
        );

        this.sidebarExams = [...sidebarRealExams, ...sidebarTestExams, ...(sidebarTestExamAttempts ?? [])];

        this.accordionExamGroups = this.groupExamsByRealOrTest(this.sortedRealExams, this.sortedTestExams);
        this.updateSidebarData();
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

    getAllStudentExamsForRealExams(): StudentExam[] {
        return [...this.studentExamsForRealExams.values()];
    }

    // Method to iterate through the map and get all student exams
    getAllStudentExams(): StudentExamOrDTO[] {
        const allStudentExams: StudentExamOrDTO[] = [];
        this.testExamMap.forEach((studentExams) => {
            studentExams.forEach((studentExam) => {
                allStudentExams.push(studentExam);
            });
        });
        return allStudentExams;
    }

    // Creating attempt indices for student exams
    getIndicesForStudentExams(numberOfStudentExams: number): number[] {
        const indices: number[] = [];
        for (let i = 1; i <= numberOfStudentExams; i++) {
            indices.push(i);
        }
        return indices;
    }

    getNumberOfAttemptsForTestExam(exam: Exam): number {
        const studentExams = this.testExamMap.get(exam.id!);
        return studentExams ? studentExams.length : 0;
    }

    /**
     * Calculate the individual working time for every submitted StudentExam. As the StudentExam needs to be submitted, the
     * working time cannot change.
     * For the latest StudentExam, which is still within the allowed working time, a subscription is used to periodically check this.
     */
    calculateIndividualWorkingTimeForTestExams(studentExam: StudentExamOrDTO, latestExam: boolean) {
        if (studentExam.started && studentExam.submitted && studentExam.startedDate && studentExam.submissionDate) {
            this.withinWorkingTime = false;
        } else if (latestExam) {
            // A subscription is used here to limit the number of calls for the countdown of the remaining workingTime.
            this.studentExamState = interval(1000).subscribe(() => {
                this.isWithinWorkingTime(studentExam, studentExam.exam!);
                // If the StudentExam is no longer within the working time, the subscription can be unsubscribed, as the state will not change anymore
                if (!this.withinWorkingTime) {
                    this.unsubscribeFromExamStateSubscription();
                }
            });
        } else {
            this.withinWorkingTime = false;
        }
    }

    /**
     * Used to unsubscribe from the studentExamState Subscriptions
     */
    unsubscribeFromExamStateSubscription() {
        this.studentExamState?.unsubscribe();
    }

    /**
     * Determines if the given StudentExam is (still) within the working time
     */
    isWithinWorkingTime(studentExam: StudentExamOrDTO, exam: Exam) {
        if (studentExam.started && !studentExam.submitted && studentExam.startedDate && exam.workingTime) {
            const endDate = dayjs(studentExam.startedDate).add(exam.workingTime, 'seconds');
            this.withinWorkingTime = dayjs(endDate).isAfter(dayjs());
        }
    }
}
