import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription, of, Observable } from 'rxjs';
import { tap, map, switchMap, filter } from 'rxjs/operators';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiLanguageService } from 'ng-jhipster';
import { SessionStorageService } from 'ngx-webstorage';
import { User } from 'app/core/user/user.model';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { SERVER_API_URL, VERSION } from 'app/app.constants';
import * as moment from 'moment';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LoginService } from 'app/core/login/login.service';
import { Router, NavigationEnd, ActivatedRoute, RouterEvent } from '@angular/router';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { Exam } from 'app/entities/exam.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { LectureService } from 'app/lecture/lecture.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Authority } from 'app/shared/constants/authority.constants';

@Component({
    selector: 'jhi-navbar',
    templateUrl: './navbar.component.html',
    styleUrls: ['navbar.scss'],
})
export class NavbarComponent implements OnInit, OnDestroy {
    readonly SERVER_API_URL = SERVER_API_URL;

    inProduction: boolean;
    isNavbarCollapsed: boolean;
    isTourAvailable: boolean;
    languages: string[];
    openApiEnabled?: boolean;
    modalRef: NgbModalRef;
    version: string;
    currAccount?: User;
    isRegistrationEnabled = false;
    passwordResetEnabled = false;
    breadcrumbs: Breadcrumb[];

    private authStateSubscription: Subscription;
    private routerEventSubscription: Subscription;
    private exam?: Exam;
    private examId?: number;
    private routeExamId = 0;
    private lastRouteUrlSegment: string;

    constructor(
        private loginService: LoginService,
        private languageService: JhiLanguageService,
        private languageHelper: JhiLanguageHelper,
        private localeConversionService: LocaleConversionService,
        private sessionStorage: SessionStorageService,
        private accountService: AccountService,
        private profileService: ProfileService,
        private participationWebsocketService: ParticipationWebsocketService,
        public guidedTourService: GuidedTourService,
        private router: Router,
        private route: ActivatedRoute,
        private examParticipationService: ExamParticipationService,
        private serverDateService: ArtemisServerDateService,
        private jhiAlertService: JhiAlertService,
        private courseManagementService: CourseManagementService,
        private exerciseService: ExerciseService,
        private hintService: ExerciseHintService,
        private apollonDiagramService: ApollonDiagramService,
        private lectureService: LectureService,
        private examService: ExamManagementService,
    ) {
        this.version = VERSION ? VERSION : '';
        this.isNavbarCollapsed = true;
        this.getExamId();
    }

    ngOnInit() {
        this.languages = this.languageHelper.getAll();

        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProduction = profileInfo.inProduction;
                this.openApiEnabled = profileInfo.openApiEnabled;
                this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
                this.passwordResetEnabled = this.isRegistrationEnabled || profileInfo.saml2?.enablePassword || false;
            }
        });

        this.subscribeForGuidedTourAvailability();

        // The current user is needed to hide menu items for not logged in users.
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(tap((user: User) => (this.currAccount = user)))
            .subscribe();

        this.examParticipationService.currentlyLoadedStudentExam.subscribe((studentExam) => {
            this.exam = studentExam.exam;
        });

        this.buildBreadcrumbs(this.router.url);
        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe((event: NavigationEnd) => this.buildBreadcrumbs(event.url));
    }

    ngOnDestroy(): void {
        if (this.authStateSubscription) {
            this.authStateSubscription.unsubscribe();
        }
        if (this.routerEventSubscription) {
            this.routerEventSubscription.unsubscribe();
        }
    }

    breadcrumbTranslation = {
        new: 'global.generic.create',
        create: 'global.generic.create',
        edit: 'global.generic.edit',
        audits: 'audits.title',
        configuration: 'configuration.title',
        feature_toggles: 'featureToggles.title',
        health: 'health.title',
        logs: 'logs.title',
        docs: 'global.menu.admin.apidocs',
        metrics: 'metrics.title',
        user_statistics: 'statistics.title',
        user_management: 'userManagement.home.title',
        system_notification_management: 'artemisApp.systemNotification.systemNotifications',
        upcoming_exams_and_exercises: 'artemisApp.upcomingExamsAndExercises.upcomingExamsAndExercises',
        account: 'global.menu.account.main',
        activate: 'activate.title',
        password: 'global.menu.account.password',
        reset: 'global.menu.account.password',
        register: 'register.title',
        settings: 'global.menu.account.settings',
        course_management: 'global.menu.course',
        exercises: 'artemisApp.course.exercises',
        text_exercises: 'artemisApp.course.exercises',
        programming_exercises: 'artemisApp.course.exercises',
        modeling_exercises: 'artemisApp.course.exercises',
        file_upload_exercises: 'artemisApp.course.exercises',
        quiz_exercises: 'artemisApp.course.exercises',
        participations: 'artemisApp.participation.home.title',
        submissions: 'artemisApp.exercise.submissions',
        complaints: 'artemisApp.complaint.listOfComplaints.title',
        more_feedback_requests: 'artemisApp.moreFeedback.list.title',
        instructor_dashboard: 'entity.action.instructorDashboard',
        assessment_dashboard: 'artemisApp.assessmentDashboard.home.title',
        test_run_exercise_assessment_dashboard: 'artemisApp.exerciseAssessmentDashboard.home.title',
        lti_configuration: 'artemisApp.programmingExercise.home.title',
        teams: 'artemisApp.team.home.title',
        hints: 'artemisApp.exerciseHint.home.title',
        ratings: 'artemisApp.ratingList.pageTitle',
        goal_management: 'artemisApp.learningGoal.manageLearningGoals.title',
        assessment_locks: 'artemisApp.assessment.locks.home.title',
        apollon_diagrams: 'artemisApp.apollonDiagram.home.title',
        posts: 'artemisApp.metis.overview.title',
        scores: 'entity.action.scores',
        assessment: 'artemisApp.assessment.assessment',
        export: 'artemisApp.quizExercise.export.export',
        re_evaluate: 'entity.action.re-evaluate',
        solution: 'artemisApp.quizExercise.solution',
        preview: 'artemisApp.quizExercise.previewMode',
        quiz_statistic: 'artemisApp.quizExercise.statistics',
        quiz_point_statistic: 'artemisApp.quizExercise.statistics',
        import: 'artemisApp.exercise.import.table.doImport',
        plagiarism: 'artemisApp.plagiarism.plagiarism-detection',
        example_solution: 'artemisApp.modelingExercise.exampleSolution',
        example_submissions: 'artemisApp.exampleSubmission.home.title',
        example_submission_editor: 'artemisApp.exampleSubmission.home.editor',
        text_feedback_conflict: 'artemisApp.textAssessment.title',
        grading: 'artemisApp.programmingExercise.configureGrading.shortTitle',
        test: 'artemisApp.editor.home.title',
        ide: 'artemisApp.editor.home.title',
        lectures: 'artemisApp.lecture.home.title',
        attachments: 'artemisApp.lecture.attachments.title',
        unit_management: 'artemisApp.lectureUnit.home.title',
        exams: 'artemisApp.examManagement.title',
        exercise_groups: 'artemisApp.examManagement.exerciseGroups',
        students: 'artemisApp.course.students',
        tutors: 'artemisApp.course.tutors',
        instructors: 'artemisApp.course.instructors',
        test_runs: 'artemisApp.examManagement.testRun.testRun',
        assess: 'artemisApp.examManagement.assessmentDashboard',
        summary: 'artemisApp.exam.summary',
        conduction: 'artemisApp.exam.title',
        student_exams: 'artemisApp.studentExams.title',
        test_assessment_dashboard: 'artemisApp.examManagement.assessmentDashboard',
        tutor_exam_dashboard: 'artemisApp.examManagement.assessmentDashboard',
        organization_management: 'organizationManagement.title',
        participant_scores: 'artemisApp.participantScores.pageTitle',
        course_statistics: 'statistics.course_statistics_title',
        grading_system: 'artemisApp.gradingSystem.title',
        exercise_statistics: 'exercise-statistics.title',
    };

    /**
     * Fills the breadcrumbs array with entries for admin and course-management routes
     */
    private buildBreadcrumbs(fullURI: string): void {
        this.breadcrumbs = [];

        if (!fullURI) {
            return;
        }

        // Temporarily restrict routes
        if (!fullURI.startsWith('/admin') && !fullURI.startsWith('/course-management')) {
            return;
        }

        // try catch for extra safety measures
        try {
            let currentPath = '/';

            // Remove the leading slash
            let uri = fullURI.substring(1);

            // Remove any query parameters
            const questionMark = uri.indexOf('?');
            if (questionMark >= 0) {
                uri = uri.substring(0, questionMark);
            }

            // Go through all segments of the route starting from the root
            for (const segment of uri.split('/')) {
                currentPath += segment + '/';

                // If we parse an entity ID we need to check the previous segment which entity the ID refers to
                if (!isNaN(Number(segment))) {
                    this.addBreadcrumbForNumberSegment(currentPath, segment);
                } else {
                    this.addBreadcrumbForUrlSegment(currentPath, segment);
                    this.lastRouteUrlSegment = segment;
                }
            }
        } catch (e) {}
    }

    /**
     * Adds a breadcrumb depending on the given entityID as string
     *
     * @param currentPath the complete path up until the breadcrumb to add
     * @param segment the current url segment (string representation of an entityID) to add a crumb for
     */
    private addBreadcrumbForNumberSegment(currentPath: string, segment: string): void {
        switch (this.lastRouteUrlSegment) {
            // Displays the path segment as breadcrumb (no other title exists)
            case 'system-notification-management':
            case 'teams':
            case 'code-editor':
                this.addBreadcrumb(currentPath, segment, false);
                break;
            case 'course-management':
                this.addResolvedTitleAsCrumb(this.courseManagementService.getTitle(Number(segment)), currentPath, segment);
                break;
            case 'exercises':
            case 'text-exercises':
            case 'modeling-exercises':
            case 'file-upload-exercises':
            case 'programming-exercises':
            case 'quiz-exercises':
                this.addResolvedTitleAsCrumb(this.exerciseService.getTitle(Number(segment)), currentPath, segment);
                break;
            case 'hints':
                this.addResolvedTitleAsCrumb(this.hintService.getTitle(Number(segment)), currentPath, segment);
                break;
            case 'apollon-diagrams':
                this.addResolvedTitleAsCrumb(this.apollonDiagramService.getTitle(Number(segment)), currentPath, segment);
                break;
            case 'lectures':
                this.addResolvedTitleAsCrumb(this.lectureService.getTitle(Number(segment)), currentPath, segment);
                break;
            case 'exams':
                this.routeExamId = Number(segment);
                this.addResolvedTitleAsCrumb(this.examService.getTitle(this.routeExamId), currentPath, segment);
                break;
            case 'import':
                // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                // This has to go in the future
                this.addTranslationAsCrumb(currentPath, 'import');
                break;
            case 'example-submissions':
                // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                this.addTranslationAsCrumb(currentPath, 'example-submission-editor');
                break;
            case 'text-feedback-conflict':
                // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                this.addTranslationAsCrumb(currentPath, 'text-feedback-conflict');
                break;
            // No breadcrumbs for those segments
            case 'goal-management':
            case 'unit-management':
            case 'exercise-groups':
            case 'student-exams':
            case 'test-runs':
            case 'mc-question-statistic':
            case 'dnd-question-statistic':
            case 'sa-question-statistic':
            default:
                break;
        }
    }

    /**
     * Adds a breadcrumb for the given url segment
     *
     * @param currentPath the complete path up until the breadcrumb to add
     * @param segment the current url segment to add a (translated) crumb for
     */
    private addBreadcrumbForUrlSegment(currentPath: string, segment: string): void {
        // When we're not dealing with an ID we need to translate the current part
        // The translation might still depend on the previous parts
        switch (segment) {
            // No breadcrumbs for those segments
            case 'reset':
            case 'groups':
            case 'code-editor':
            case 'admin':
            case 'ide':
            case 'text-units':
            case 'exercise-units':
            case 'attachment-units':
            case 'video-units':
            case 'text-feedback-conflict':
            case 'grading':
            case 'mc-question-statistic':
            case 'dnd-question-statistic':
            case 'sa-question-statistic':
                break;
            case 'example-submissions':
                // Hide example submission dashboard for non instructor users
                if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR])) {
                    this.addTranslationAsCrumb(currentPath, segment);
                }
                break;
            default:
                // Special cases:
                if (this.lastRouteUrlSegment === 'user-management') {
                    // - Users display their login name directly as crumb
                    this.addBreadcrumb(currentPath, segment, false);
                    break;
                } else if (this.lastRouteUrlSegment === 'example-submissions') {
                    // - Creating a new example submission should display the text for example submissions
                    this.addTranslationAsCrumb(currentPath, 'example-submission-editor');
                    break;
                } else if (this.lastRouteUrlSegment === 'grading') {
                    // - Opening a grading tab should only display the text for grading
                    this.addTranslationAsCrumb(currentPath, 'grading');
                    break;
                } else if (this.lastRouteUrlSegment === 'code-editor' && segment === 'new') {
                    // - This route is bogus an needs to be replaced in the future, display no crumb
                    break;
                } else if (this.lastRouteUrlSegment === 'programming-exercises' && segment === 'import') {
                    // - This route is bogus an needs to be replaced in the future, display no crumb
                    break;
                } else if (this.lastRouteUrlSegment === 'exercise-groups') {
                    // - Don't display '<type>-exercises' because it has no associated route
                    break;
                }

                this.addTranslationAsCrumb(currentPath, segment);
                break;
        }
    }

    /**
     * Appends a breadcrumb to the list of breadcrumbs
     *
     * @param uri the uri/path for the breadcrumb
     * @param label the displayed label for the breadcrumb
     * @param translate if the label should be translated
     */
    private addBreadcrumb(uri: string, label: string, translate: boolean): void {
        this.setBreadcrumb(uri, label, translate, this.breadcrumbs.length);
    }

    /**
     * Sets a breadcrumb in the list of breadcrumbs at the given index
     *
     * @param uri the uri/path for the breadcrumb
     * @param label the displayed label for the breadcrumb
     * @param translate if the label should be translated
     * @param index the index of the breadcrumbs array to set the breadcrumb at
     */
    private setBreadcrumb(uri: string, label: string, translate: boolean, index: number): void {
        const crumb = new Breadcrumb();
        crumb.label = label;
        crumb.translate = translate;
        crumb.uri = uri;
        this.breadcrumbs[index] = crumb;
    }

    /**
     * Uses the server response to add a title for a breadcrumb
     * While waiting for the response or in case of an error the segment is displayed directly as fallback
     *
     * @param observable the observable returning an entity to display the title of
     * @param uri the uri/path for the breadcrumb
     * @param segment the current url segment to add a breadcrumb for
     */
    private addResolvedTitleAsCrumb(observable: Observable<HttpResponse<string>>, uri: string, segment: string): void {
        // Insert the segment until we fetched a title from the server to insert at the correct index
        const index = this.breadcrumbs.length;
        this.addBreadcrumb(uri, segment, false);

        observable.subscribe(
            (response: HttpResponse<string>) => {
                // Fall back to the segment in case there is no body returned
                const title = response.body ?? segment;
                this.setBreadcrumb(uri, title, false, index);
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
    }

    /**
     * Adds a breadcrumb with a translated label
     * If no translation can be found the key is displayed
     *
     * @param uri the uri/path for the breadcrumb
     * @param translationKey the string to index the breadcrumbTranslation table with
     */
    private addTranslationAsCrumb(uri: string, translationKey: string): void {
        const key = translationKey.split('-').join('_');
        if (this.breadcrumbTranslation[key]) {
            this.addBreadcrumb(uri, this.breadcrumbTranslation[key], true);
        } else {
            // If there is no valid entry in the mapping display the raw key instead of a "not found"
            this.addBreadcrumb(uri, translationKey, false);
        }
    }

    /**
     * Check if a guided tour is available for the current route to display the start tour button in the account menu
     */
    subscribeForGuidedTourAvailability(): void {
        // Check availability after first subscribe call since the router event been triggered already
        this.guidedTourService.getGuidedTourAvailabilityStream().subscribe((isAvailable) => {
            this.isTourAvailable = isAvailable;
        });
    }

    changeLanguage(languageKey: string) {
        this.sessionStorage.store('locale', languageKey);
        this.languageService.changeLanguage(languageKey);
        moment.locale(languageKey);
        this.localeConversionService.locale = languageKey;
    }

    collapseNavbar() {
        this.isNavbarCollapsed = true;
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    logout() {
        this.participationWebsocketService.resetLocalCache();
        this.collapseNavbar();
        this.loginService.logout(true);
    }

    toggleNavbar() {
        this.isNavbarCollapsed = !this.isNavbarCollapsed;
    }

    getImageUrl() {
        return this.accountService.getImageUrl();
    }

    /**
     * Determine the label for initiating the guided tour based on the last seen tour step
     */
    guidedTourInitLabel(): string {
        switch (this.guidedTourService.getLastSeenTourStepForInit()) {
            case -1: {
                return 'global.menu.restartTutorial';
            }
            case 0: {
                return 'global.menu.startTutorial';
            }
            default: {
                return 'global.menu.continueTutorial';
            }
        }
    }

    /**
     * get exam id from current route
     */
    getExamId() {
        this.routerEventSubscription = this.router.events.pipe(filter((event: RouterEvent) => event instanceof NavigationEnd)).subscribe((event) => {
            const examId = of(event).pipe(
                map(() => this.route.root),
                map((root) => root.firstChild),
                switchMap((firstChild) => {
                    if (firstChild) {
                        return firstChild?.paramMap.pipe(map((paramMap) => paramMap.get('examId')));
                    } else {
                        return of(null);
                    }
                }),
            );
            examId.subscribe((id) => {
                if (id !== null && !event.url.includes('management')) {
                    this.examId = +id;
                } else {
                    this.examId = undefined;
                }
            });
        });
    }

    /**
     * check if exam mode is active
     */
    examModeActive(): boolean {
        if (this.exam && this.exam.id === this.examId && this.exam.startDate && this.exam.endDate) {
            return this.serverDateService.now().isBetween(this.exam.startDate, this.exam.endDate);
        }
        return false;
    }
}

class Breadcrumb {
    label: string;
    uri: string;
    translate: boolean;
}
