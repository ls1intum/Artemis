import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Params, Router, RouterOutlet } from '@angular/router';
import { combineLatest, filter, map, of } from 'rxjs';
import { Exam, isTestExam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { cloneDeep } from 'lodash-es';
import { NgClass } from '@angular/common';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
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
export class CourseExamsComponent {
    private route = inject(ActivatedRoute);
    private courseStorageService = inject(CourseStorageService);
    private serverDateService = inject(ArtemisServerDateService);
    private examParticipationService = inject(ExamParticipationService);
    private courseOverviewService = inject(CourseOverviewService);
    private sessionStorageService = inject(SessionStorageService);
    private router = inject(Router);

    private readonly parentParams = toSignal(this.route.parent?.params ?? of<Params>({}), { initialValue: this.route.parent?.snapshot?.params ?? {} });
    readonly courseId = computed(() => Number(this.parentParams()['courseId'] ?? 0));
    readonly course = computed(() => this.courseStorageService.getCourse(this.courseId()));

    private readonly visibleExams = computed(
        () =>
            this.course()
                ?.exams?.filter((exam) => this.isVisible(exam))
                .sort((exam1, exam2) => this.sortExamsByStartDate(exam1, exam2)) ?? [],
    );

    protected readonly realExamsOfCourse = computed(() => this.visibleExams().filter((exam) => !isTestExam(exam)));
    protected readonly studentExamByRealExamId = signal<Map<number, StudentExam>>(new Map());

    protected readonly testExamsOfCourse = computed(() => this.visibleExams().filter((exam) => isTestExam(exam)));
    private readonly studentExamsOfTestExams = signal<StudentExam[]>([]);

    readonly examSelected = signal(true);
    readonly isCollapsed = signal(this.courseOverviewService.getSidebarCollapseStateFromStorage('exam'));
    readonly sidebarData = computed<SidebarData | undefined>(() => this.computeSidebarData());
    readonly isExamStarted = toSignal(this.examParticipationService.examIsStarted$, { initialValue: false });

    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    /**
     * subscribe to changes in the course and fetch course by the path parameter
     */
    constructor() {
        this.handleStudentExams();

        // If no exam is selected navigate to the last selected or upcoming Exam
        this.navigateToExam();
    }

    private handleStudentExams() {
        // load the baseline of studentExams without overwriting newer local updates.
        this.examParticipationService
            .loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(this.courseId())
            .pipe(takeUntilDestroyed())
            .subscribe((loadedStudentExams) => {
                this.studentExamsOfTestExams.update((currentStudentExams) => [
                    ...(loadedStudentExams ?? []).filter((loadedStudentExam) => !currentStudentExams.some((studentExam) => studentExam.id === loadedStudentExam.id)),
                    ...currentStudentExams,
                ]);
            });

        // watch for new student exams
        combineLatest([this.examParticipationService.shouldUpdateTestExamsObservable, this.examParticipationService.currentlyLoadedStudentExam])
            .pipe(
                filter(([shouldUpdate, studentExam]) => shouldUpdate && !!studentExam && studentExam.exam?.course?.id === this.courseId()),
                takeUntilDestroyed(),
            )
            .subscribe(([_, latestExam]) => {
                this.studentExamsOfTestExams.update((studentExams) => {
                    this.examParticipationService.setShouldUpdateTestExams(false);

                    const index = studentExams.findIndex((se) => se?.id === latestExam?.id);
                    if (index !== -1) {
                        const updated = [...studentExams];
                        updated[index] = latestExam;
                        return updated;
                    }
                    return [...studentExams, latestExam];
                });
            });

        // set the student exams for the real exams
        this.examParticipationService
            .getRealExamSidebarData(this.courseId())
            .pipe(
                map((realExamSidebarData) => new Map(realExamSidebarData.map((studentExam) => [studentExam.id!, studentExam as StudentExam]))),
                takeUntilDestroyed(),
            )
            .subscribe((studentExamByRealExamId) => this.studentExamByRealExamId.set(studentExamByRealExamId));
    }

    navigateToExam() {
        const upcomingExam = this.courseOverviewService.getUpcomingExam([...this.realExamsOfCourse(), ...this.testExamsOfCourse()]);
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
            this.examSelected.set(!!examId);
        }
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
     * @param studentExams the student exams to sort
     * @param examId the examId for which the StudentExams should be retrieved
     * @return a by id descending ordered list of studentExams
     */
    protected getStudentExamForExamIdOrderedByIdReverse(studentExams: StudentExam[], examId: number): StudentExam[] {
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
    sortExamsByStartDate(exam1: Exam, exam2: Exam): number {
        if (dayjs(exam1.startDate!).isBefore(exam2.startDate!)) {
            return -1;
        }
        if (dayjs(exam1.startDate!).isAfter(exam2.startDate!)) {
            return 1;
        }
        return 0;
    }

    groupExamsByRealOrTest(realExams: Exam[], testExams: Exam[], testExamAttemptsMap: Map<number, StudentExam[]>): AccordionGroups {
        const groupedExamGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as AccordionGroups;
        const realStudentExamById = this.studentExamByRealExamId();

        for (const realExam of realExams) {
            const examCardItem = this.courseOverviewService.mapExamToSidebarCardElement(realExam, realStudentExamById.get(realExam.id!));
            groupedExamGroups['real'].entityData.push(examCardItem);
        }
        testExams.forEach((testExam) => {
            const examCardItem = this.courseOverviewService.mapExamToSidebarCardElement(testExam, undefined, this.getNumberOfAttemptsForTestExam(testExam, testExamAttemptsMap));
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

    getLastSelectedExam(): number | undefined {
        return this.sessionStorageService.retrieve<number>('sidebar.lastSelectedItem.exam.byCourse.' + this.courseId());
    }

    toggleSidebar() {
        const newState = !this.isCollapsed();
        this.isCollapsed.set(newState);
        this.courseOverviewService.setSidebarCollapseState('exam', newState);
    }

    computeSidebarData(): SidebarData | undefined {
        if (!this.course()?.exams) {
            return;
        }

        const sortedRealExams: Exam[] = [...this.realExamsOfCourse()].sort((a, b) => this.sortExamsByStartDate(a, b));
        const sortedTestExams: Exam[] = [...this.testExamsOfCourse()].sort((a, b) => this.sortExamsByStartDate(a, b));

        const testExamAttempts = this.studentExamsOfTestExams();
        const testExamAttemptsMap: Map<number, StudentExam[]> = new Map();
        for (const testExam of sortedTestExams) {
            const orderedTestExamAttempts = this.getStudentExamForExamIdOrderedByIdReverse(testExamAttempts, testExam.id!);
            const submittedAttempts = orderedTestExamAttempts.filter((attempt) => attempt.submitted);
            testExamAttemptsMap.set(testExam.id!, submittedAttempts);
        }

        const accordionExamGroups = this.groupExamsByRealOrTest(sortedRealExams, sortedTestExams, testExamAttemptsMap);
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

    onSubRouteDeactivate() {
        if (this.route.firstChild) {
            return;
        }
        this.navigateToExam();
    }

    getNumberOfAttemptsForTestExam(exam: Exam, testExamAttemptsMap: Map<number, StudentExam[]>): number {
        const studentExams = testExamAttemptsMap.get(exam.id!);
        return studentExams ? studentExams.length : 0;
    }
}
