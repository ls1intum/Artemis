import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { Exam } from 'app/entities/exam/exam.model';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { faAngleDown, faAngleUp, faListAlt } from '@fortawesome/free-solid-svg-icons';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { CourseOverviewService } from '../course-overview.service';
import { cloneDeep } from 'lodash-es';
import { lastValueFrom } from 'rxjs';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    real: { entityData: [] },
    test: { entityData: [] },
};

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    real: false,
    test: false,
};

@Component({
    selector: 'jhi-course-exams',
    templateUrl: './course-exams.component.html',
    styleUrls: ['./course-exams.component.scss'],
})
export class CourseExamsComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private courseStorageService = inject(CourseStorageService);
    private serverDateService = inject(ArtemisServerDateService);
    private examParticipationService = inject(ExamParticipationService);
    private courseOverviewService = inject(CourseOverviewService);
    private router = inject(Router);

    courseId: number;
    public course?: Course;
    private parentParamSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private studentExamTestExamUpdateSubscription?: Subscription;
    private examStartedSubscription?: Subscription;
    private studentExams: StudentExam[];
    private studentExamsForRealExams = new Map<number, StudentExam>();
    public expandAttemptsMap = new Map<number, boolean>();
    public realExamsOfCourse: Exam[] = [];
    public testExamsOfCourse: Exam[] = [];

    // Icons
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;
    faListAlt = faListAlt;

    sortedRealExams?: Exam[];
    sortedTestExams?: Exam[];
    examSelected = true;
    accordionExamGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sidebarData: SidebarData;
    sidebarExams: SidebarCardElement[] = [];
    isCollapsed = false;
    isExamStarted = false;

    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;

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

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.updateExams();
            this.prepareSidebarData();
        });

        this.studentExamTestExamUpdateSubscription = this.examParticipationService
            .loadStudentExamsForTestExamsPerCourseAndPerUserForOverviewPage(this.courseId)
            .subscribe((response: StudentExam[]) => {
                this.studentExams = response!;
            });

        if (this.course?.exams) {
            // The Map is ued to store the boolean value, if the attempt-List for one Exam has been expanded or collapsed
            this.expandAttemptsMap = new Map(this.course.exams.filter((exam) => exam.testExam && this.isVisible(exam)).map((exam) => [exam.id!, false]));
            this.updateExams();
            this.prepareSidebarData();
        }

        // If no exam is selected navigate to the last selected or upcoming Exam
        this.navigateToExam();
    }

    navigateToExam() {
        const upcomingExam = this.courseOverviewService.getUpcomingExam([...this.realExamsOfCourse, ...this.testExamsOfCourse]);
        const lastSelectedExam = this.getLastSelectedExam();
        const examId = this.route.firstChild?.snapshot.params.examId;
        if (!examId && lastSelectedExam) {
            this.router.navigate([lastSelectedExam], { relativeTo: this.route, replaceUrl: true });
        } else if (!examId && upcomingExam) {
            this.router.navigate([upcomingExam.id], { relativeTo: this.route, replaceUrl: true });
        } else {
            this.examSelected = examId ? true : false;
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
            const studentExamPromisesForRealExams = this.realExamsOfCourse.map((realExam) =>
                lastValueFrom(this.examParticipationService.getOwnStudentExam(this.courseId, realExam.id!)).then((studentExam) => {
                    this.studentExamsForRealExams.set(realExam.id!, studentExam);
                }),
            );
            // Ensure that we prepare sidebardata after all studentexams are loaded
            Promise.all(studentExamPromisesForRealExams).then(() => {
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
        this.studentExamTestExamUpdateSubscription?.unsubscribe();
        this.examStartedSubscription?.unsubscribe();
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
        for (const testExam of testExams) {
            const examCardItem = this.courseOverviewService.mapExamToSidebarCardElement(testExam);
            groupedExamGroups['test'].entityData.push(examCardItem);
        }

        return groupedExamGroups;
    }

    getLastSelectedExam(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedItem.exam.byCourse.' + this.courseId);
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

        const sidebarRealExams = this.courseOverviewService.mapExamsToSidebarCardElements(this.sortedRealExams, this.getAllStudentExamsForRealExams());
        const sidebarTestExams = this.courseOverviewService.mapExamsToSidebarCardElements(this.sortedTestExams);

        this.sidebarExams = [...sidebarRealExams, ...sidebarTestExams];

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
}
