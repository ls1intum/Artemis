import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { Subscription, combineLatest, filter, interval, lastValueFrom } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { faAngleDown, faAngleUp, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { cloneDeep } from 'lodash-es';
import { NgClass } from '@angular/common';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData } from 'app/shared/types/sidebar';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

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
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseExamsComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private courseStorageService = inject(CourseStorageService);
    private serverDateService = inject(ArtemisServerDateService);
    private examParticipationService = inject(ExamParticipationService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private router = inject(Router);

    courseId: number;
    public course?: Course;
    private parentParamSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private studentExamTestExamInitialFetchSubscription?: Subscription;
    private studentExamTestExamUpdateSubscription?: Subscription;
    private examStartedSubscription?: Subscription;
    private studentExams: StudentExam[];
    studentExamsForRealExams = new Map<number, StudentExam>();
    public expandAttemptsMap = new Map<number, boolean>();
    public realExamsOfCourse: Exam[] = [];
    public testExamsOfCourse: Exam[] = [];
    studentExamState: Subscription;

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;
    faListAlt = faListAlt;

    sortedRealExams?: Exam[];
    sortedTestExams?: Exam[];
    testExamMap: Map<number, StudentExam[]> = new Map();
    examSelected = true;
    accordionExamGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sidebarData: SidebarData;
    sidebarExams: SidebarCardElement[] = [];
    isCollapsed = false;
    isExamStarted = false;
    withinWorkingTime: boolean;

    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    /**
     * subscribe to changes in the course and fetch course by the path parameter
     */
    ngOnInit(): void {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('exam');
        this.parentParamSubscription = this.route.parent?.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
        });

        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted) => {
            this.isExamStarted = isStarted;
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.prepareSidebarData();
        this.studentExamTestExamInitialFetchSubscription = this.examParticipationService
            .loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(this.courseId)
            .subscribe((response: StudentExam[]) => {
                this.studentExams = response!;
                this.prepareSidebarData();
            });

        this.studentExamTestExamUpdateSubscription = combineLatest([
            this.examParticipationService.shouldUpdateTestExamsObservable,
            this.examParticipationService.currentlyLoadedStudentExam,
        ])
            .pipe(filter(([shouldUpdate, studentExam]) => shouldUpdate === true && !!studentExam && studentExam.exam?.course?.id === this.courseId))
            .subscribe(([_, latestExam]) => {
                const index = this.studentExams?.findIndex((se) => se?.id === latestExam?.id);
                if (index !== -1 && this.studentExams) {
                    this.studentExams[index] = latestExam;
                } else {
                    this.studentExams = [...(this.studentExams || []), latestExam];
                }
                this.prepareSidebarData();

                this.examParticipationService.setShouldUpdateTestExams(false);
            });

        if (this.course?.exams) {
            // The Map is ued to store the boolean value, if the attempt-List for one Exam has been expanded or collapsed
            this.expandAttemptsMap = new Map(this.course.exams.filter((exam) => exam.testExam && this.isVisible(exam)).map((exam) => [exam.id!, false]));
            this.updateExams();
        }

        // If no exam is selected navigate to the last selected or upcoming Exam
        this.navigateToExam();
    }

    navigateToExam() {
        const upcomingExam = this.courseOverviewService.getUpcomingExam([...this.realExamsOfCourse, ...this.testExamsOfCourse]);
        const lastSelectedExam = this.getLastSelectedExam();
        const examId = this.route.firstChild?.snapshot.params.examId;
        if (!examId && lastSelectedExam) {
            // First, try to navigate to the last selected exam
            this.router.navigate([lastSelectedExam], { relativeTo: this.route, replaceUrl: true });
        } else if (!examId && upcomingExam) {
            // Second, try to navigate to the upcoming exam
            this.router.navigate([upcomingExam.id], { relativeTo: this.route, replaceUrl: true });
        } else {
            // If both is not defined, do not navigate and only set examSelected to true when the examId was found in the client URL
            this.examSelected = !!examId;
        }
    }

    private updateExams(): void {
        if (this.course?.exams) {
            // Loading the exams from the course
            const exams = this.course.exams.filter((exam) => this.isVisible(exam)).sort((se1, se2) => this.sortExamsByStartDate(se1, se2));
            // add new exams to the attempt map
            exams.filter((exam) => exam.testExam && !this.expandAttemptsMap.has(exam.id!)).forEach((exam) => this.expandAttemptsMap.set(exam.id!, false));

            this.realExamsOfCourse = exams.filter((exam) => !exam.testExam);
            this.testExamsOfCourse = exams.filter((exam) => exam.testExam);
            // get student exams for real exams
            lastValueFrom(this.examParticipationService.getRealExamSidebarData(this.courseId)).then((studentExams) => {
                studentExams.forEach((exam) => {
                    const studentExam = cloneDeep(exam) as StudentExam;
                    this.studentExamsForRealExams.set(studentExam.id!, studentExam);
                });
                this.prepareSidebarData();
            });
        }
    }

    /**
     * unsubscribe from all subscriptions
     */
    ngOnDestroy(): void {
        if (this.parentParamSubscription) {
            this.parentParamSubscription.unsubscribe();
        }
        if (this.courseUpdatesSubscription) {
            this.courseUpdatesSubscription.unsubscribe();
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
    getStudentExamForExamIdOrderedByIdReverse(examId: number): StudentExam[] {
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
        if (dayjs(exam1.startDate!).isBefore(exam2.startDate!)) {
            return -1;
        }
        if (dayjs(exam1.startDate!).isAfter(exam2.startDate!)) {
            return 1;
        }
        return 0;
    }

    groupExamsByRealOrTest(realExams: Exam[], testExams: Exam[]): AccordionGroups {
        const groupedExamGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as AccordionGroups;

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
        return this.sessionStorageService.retrieve<number>('sidebar.lastSelectedItem.exam.byCourse.' + this.courseId);
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('exam', this.isCollapsed);
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'exam',
            storageId: 'exam',
            groupedData: this.accordionExamGroups,
            ungroupedData: this.sidebarExams,
        };
    }

    prepareSidebarData() {
        if (!this.course?.exams) {
            return;
        }

        this.sortedRealExams = this.realExamsOfCourse.sort((a, b) => this.sortExamsByStartDate(a, b));
        this.sortedTestExams = this.testExamsOfCourse.sort((a, b) => this.sortExamsByStartDate(a, b));
        for (const testExam of this.sortedTestExams) {
            const orderedTestExamAttempts = this.getStudentExamForExamIdOrderedByIdReverse(testExam.id!);
            orderedTestExamAttempts.forEach((attempt, index) => {
                this.calculateIndividualWorkingTimeForTestExams(attempt, index === 0);
            });
            const submittedAttempts = orderedTestExamAttempts.filter((attempt) => attempt.submitted);
            this.testExamMap.set(testExam.id!, submittedAttempts);
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
    getAllStudentExams(): StudentExam[] {
        const allStudentExams: StudentExam[] = [];
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
    calculateIndividualWorkingTimeForTestExams(studentExam: StudentExam, latestExam: boolean) {
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
    isWithinWorkingTime(studentExam: StudentExam, exam: Exam) {
        if (studentExam.started && !studentExam.submitted && studentExam.startedDate && exam.workingTime) {
            const endDate = dayjs(studentExam.startedDate).add(exam.workingTime, 'seconds');
            this.withinWorkingTime = dayjs(endDate).isAfter(dayjs());
        }
    }
}
