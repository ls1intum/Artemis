import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, Subscription, map, of } from 'rxjs';
import { Course, isCommunicationEnabled } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { EventManager } from 'app/core/util/event-manager.service';
import {
    faArrowUpRightFromSquare,
    faChartBar,
    faClipboard,
    faComments,
    faEye,
    faFilePdf,
    faFlag,
    faGraduationCap,
    faList,
    faListAlt,
    faNetworkWired,
    faPersonChalkboard,
    faPuzzlePiece,
    faQuestion,
    faRobot,
    faTable,
    faTrash,
    faUserCheck,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseAdminService } from 'app/course/manage/course-admin.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_IRIS, PROFILE_LOCALCI, PROFILE_LTI } from 'app/app.constants';
import { CourseAccessStorageService } from 'app/course/course-access-storage.service';
import { scrollToTopOfPage } from 'app/shared/util/utils';
import { ExerciseType } from 'app/entities/exercise.model';
import { EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';

@Component({
    selector: 'jhi-course-management-tab-bar',
    templateUrl: './course-management-tab-bar.component.html',
    styleUrls: ['./course-management-tab-bar.component.scss'],
})
export class CourseManagementTabBarComponent implements OnInit, OnDestroy, AfterViewInit {
    readonly FeatureToggle = FeatureToggle;
    readonly ButtonSize = ButtonSize;

    course?: Course;

    private paramSub?: Subscription;
    private courseSub?: Subscription;
    private eventSubscriber: Subscription;

    localCIActive: boolean = false;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faArrowUpRightFromSquare = faArrowUpRightFromSquare;
    faTrash = faTrash;
    faEye = faEye;
    faWrench = faWrench;
    faTable = faTable;
    faUserCheck = faUserCheck;
    faFlag = faFlag;
    faNetworkWired = faNetworkWired;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faFilePdf = faFilePdf;
    faComments = faComments;
    faClipboard = faClipboard;
    faGraduationCap = faGraduationCap;
    faPersonChalkboard = faPersonChalkboard;
    faRobot = faRobot;
    faPuzzlePiece = faPuzzlePiece;
    faList = faList;
    faQuestion = faQuestion;

    isCommunicationEnabled = false;

    irisEnabled = false;
    ltiEnabled = false;

    constructor(
        private eventManager: EventManager,
        private courseManagementService: CourseManagementService,
        private courseAdminService: CourseAdminService,
        private route: ActivatedRoute,
        private router: Router,
        private modalService: NgbModal,
        private profileService: ProfileService,
        private courseAccessStorageService: CourseAccessStorageService,
    ) {}

    /**
     * On init load the course information and subscribe to listen for changes in course.
     */
    ngOnInit() {
        let courseId = 0;
        this.paramSub = this.route.firstChild?.params.subscribe((params) => {
            courseId = params['courseId'];
            this.subscribeToCourseUpdates(courseId);
        });

        // Subscribe to course modifications and reload the course after a change.
        this.eventSubscriber = this.eventManager.subscribe('courseModification', () => {
            this.subscribeToCourseUpdates(courseId);
        });

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.irisEnabled = profileInfo.activeProfiles.includes(PROFILE_IRIS);
                this.ltiEnabled = profileInfo.activeProfiles.includes(PROFILE_LTI);
                this.localCIActive = profileInfo?.activeProfiles.includes(PROFILE_LOCALCI);
            }
        });

        // Notify the course access storage service that the course has been accessed
        this.courseAccessStorageService.onCourseAccessed(
            courseId,
            CourseAccessStorageService.STORAGE_KEY,
            CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_OVERVIEW,
        );
        this.courseAccessStorageService.onCourseAccessed(
            courseId,
            CourseAccessStorageService.STORAGE_KEY_DROPDOWN,
            CourseAccessStorageService.MAX_DISPLAYED_RECENTLY_ACCESSED_COURSES_DROPDOWN,
        );
    }

    ngAfterViewInit() {
        scrollToTopOfPage();
    }
    /**
     * Subscribe to changes in course.
     */
    private subscribeToCourseUpdates(courseId: number) {
        this.courseSub = this.courseManagementService.find(courseId).subscribe((courseResponse) => {
            this.course = courseResponse.body!;
            this.isCommunicationEnabled = isCommunicationEnabled(this.course);
        });
    }

    /**
     * On destroy unsubscribe all subscriptions.
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        if (this.courseSub) {
            this.courseSub.unsubscribe();
        }
        this.eventManager.destroy(this.eventSubscriber);
    }

    /**
     * Deletes the course
     * @param courseId id the course that will be deleted
     */
    deleteCourse(courseId: number) {
        this.courseAdminService.delete(courseId).subscribe({
            next: () => {
                this.eventManager.broadcast({
                    name: 'courseListModification',
                    content: 'Deleted an course',
                });
                this.dialogErrorSource.next('');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
        this.router.navigate(['/course-management']);
    }

    /**
     * Checks if the current route contains 'tutorial-groups'.
     * @return true if the current route is part of the tutorial management
     */
    shouldHighlightTutorialsLink(): boolean {
        const tutorialsRegex = /tutorial-groups/;
        return tutorialsRegex.test(this.router.url);
    }

    /**
     * Checks if the current route contains 'grading-system' or 'plagiarism-cases' but not 'exams'.
     * @return true if the current route is part of the assessment management
     */
    shouldHighlightAssessmentLink(): boolean {
        // Exclude exam related links from the assessment link highlighting.
        // Example that should not highlight the assessment link: /course-management/{courseId}/exams/{examId}/grading-system/interval
        const assessmentLinkRegex = /^(?!.*exams).*(grading-system|plagiarism-cases|assessment-dashboard)/;
        return assessmentLinkRegex.test(this.router.url);
    }

    /**
     * Checks if the current route is 'course-management/{courseId}/edit' or 'course-management/{courseId}'.
     * @return true if the control buttons, e.g., delete & edit, should be shown
     */
    shouldShowControlButtons(): boolean {
        const courseManagementRegex = /course-management\/[0-9]+(\/edit)?$/;
        return courseManagementRegex.test(this.router.url);
    }

    private getExistingSummaryEntries(): EntitySummary {
        const numberRepositories =
            this.course?.exercises
                ?.filter((exercise) => exercise.type === 'programming')
                .map((exercise) => exercise?.numberOfParticipations ?? 0)
                .reduce((repositorySum, numberOfParticipationsForRepository) => repositorySum + numberOfParticipationsForRepository, 0) ?? 0;

        const numberOfExercisesPerType = new Map<ExerciseType, number>();
        this.course?.exercises?.forEach((exercise) => {
            if (exercise.type === undefined) {
                return;
            }
            const oldValue = numberOfExercisesPerType.get(exercise.type) ?? 0;
            numberOfExercisesPerType.set(exercise.type, oldValue + 1);
        });

        const numberExams = this.course?.numberOfExams ?? 0;
        const numberLectures = this.course?.lectures?.length ?? 0;
        const numberStudents = this.course?.numberOfStudents ?? 0;
        const numberTutors = this.course?.numberOfTeachingAssistants ?? 0;
        const numberEditors = this.course?.numberOfEditors ?? 0;
        const numberInstructors = this.course?.numberOfInstructors ?? 0;
        const isTestCourse = this.course?.testCourse;

        return {
            'artemisApp.course.delete.summary.numberRepositories': numberRepositories,
            'artemisApp.course.delete.summary.numberProgrammingExercises': numberOfExercisesPerType.get(ExerciseType.PROGRAMMING) ?? 0,
            'artemisApp.course.delete.summary.numberModelingExercises': numberOfExercisesPerType.get(ExerciseType.MODELING) ?? 0,
            'artemisApp.course.delete.summary.numberTextExercises': numberOfExercisesPerType.get(ExerciseType.TEXT) ?? 0,
            'artemisApp.course.delete.summary.numberFileUploadExercises': numberOfExercisesPerType.get(ExerciseType.FILE_UPLOAD) ?? 0,
            'artemisApp.course.delete.summary.numberQuizExercises': numberOfExercisesPerType.get(ExerciseType.QUIZ) ?? 0,
            'artemisApp.course.delete.summary.numberExams': numberExams,
            'artemisApp.course.delete.summary.numberLectures': numberLectures,
            'artemisApp.course.delete.summary.numberStudents': numberStudents,
            'artemisApp.course.delete.summary.numberTutors': numberTutors,
            'artemisApp.course.delete.summary.numberEditors': numberEditors,
            'artemisApp.course.delete.summary.numberInstructors': numberInstructors,
            'artemisApp.course.delete.summary.isTestCourse': isTestCourse,
        };
    }

    fetchCourseDeletionSummary(): Observable<EntitySummary> {
        if (this.course?.id === undefined) {
            return of({});
        }

        return this.courseAdminService.getDeletionSummary(this.course.id).pipe(
            map((response) => {
                const summary = response.body;

                if (summary === null) {
                    return {};
                }

                return {
                    ...this.getExistingSummaryEntries(),
                    'artemisApp.course.delete.summary.numberBuilds': summary.numberOfBuilds,
                    'artemisApp.course.delete.summary.numberCommunicationPosts': summary.numberOfCommunicationPosts,
                    'artemisApp.course.delete.summary.numberAnswerPosts': summary.numberOfAnswerPosts,
                };
            }),
        );
    }
}
