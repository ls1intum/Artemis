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
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ApollonDiagramService } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.service';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';

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
    breadcrumbs: Breadcrumb[];

    private authStateSubscription: Subscription;
    private routerEventSubscription: Subscription;
    private exam?: Exam;
    private examId?: number;

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
        tutor_dashboard: 'artemisApp.assessmentDashboard.home.title',
        test_run_exercise_assessment_dashboard: 'artemisApp.exerciseAssessmentDashboard.home.title',
        lti_configuration: 'artemisApp.programmingExercise.home.title',
        teams: 'artemisApp.team.home.title',
        hints: 'artemisApp.exerciseHint.home.title',
        ratings: 'artemisApp.ratingList.pageTitle',
        goal_management: 'artemisApp.learningGoal.manageLearningGoals.title',
        assessment_locks: 'artemisApp.assessment.locks.home.title',
        apollon_diagrams: 'artemisApp.apollonDiagram.home.title',
        questions: 'artemisApp.studentQuestion.overview.title',
        scores: 'entity.action.scores',
        assessment: 'artemisApp.assessment.assessment',
        export: 'artemisApp.quizExercise.export.title',
        re_evaluate: 'entity.action.re-evaluate',
        solution: 'artemisApp.quizExercise.solution',
        preview: 'artemisApp.quizExercise.previewMode',
        quiz_statistic: 'artemisApp.quizExercise.statistics',
        quiz_point_statistic: 'artemisApp.quizExercise.statistics',
        import: 'artemisApp.exercise.import.table.doImport',
        plagiarism: 'artemisApp.plagiarism.plagiarism-detection',
        example_solution: 'artemisApp.modelingExercise.exampleSolution',
        example_submissions: 'artemisApp.exampleSubmission.home.title',
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
        tutor_exam_dashboard: 'artemisApp.examManagement.assessmentDashboard',
    };

    buildBreadcrumbs(fullURI: string) {
        this.breadcrumbs = [];

        // Temporarily restrict routes
        if (!fullURI.startsWith('/admin') && !fullURI.startsWith('/course-management')) {
            return;
        }

        // Go through all parts (children) of the route starting from the root
        let path = '';
        let previousPart = '';
        let breadcrumbIndex = 0;
        let courseId = 0;
        let examId = 0;
        let child = this.route.root.firstChild;
        while (child) {
            if (!child.snapshot.url || child.snapshot.url.length === 0) {
                // This child is not part of the route, skip to the next
                child = child.firstChild;
                continue;
            }

            for (const urlSegment of child.snapshot.url) {
                const part = urlSegment.toString();
                path += part + '/';

                // If we parse an entity ID we need to check the previous segment which entity the ID refers to
                if (!isNaN(Number(part))) {
                    switch (previousPart) {
                        // Displays the path segment as breadcrumb (no other title exists)
                        case 'system-notification-management':
                        case 'teams':
                        case 'code-editor':
                            this.addBreadcrumb(path, part, breadcrumbIndex++, false);
                            break;
                        case 'course-management':
                            courseId = Number(part);
                            this.addResolvedTitleAsCrumb<Course>(this.courseManagementService.find(courseId), path, part, breadcrumbIndex++);
                            break;
                        case 'exercises':
                        case 'text-exercises':
                        case 'modeling-exercises':
                        case 'file-upload-exercises':
                        case 'programming-exercises':
                        case 'quiz-exercises':
                            this.addResolvedTitleAsCrumb<Exercise>(this.exerciseService.find(Number(part)), path, part, breadcrumbIndex++);
                            break;
                        case 'hints':
                            this.addResolvedTitleAsCrumb<ExerciseHint>(this.hintService.find(Number(part)), path, part, breadcrumbIndex++);
                            break;
                        case 'apollon-diagrams':
                            this.addResolvedTitleAsCrumb<ApollonDiagram>(this.apollonDiagramService.find(Number(part), courseId), path, part, breadcrumbIndex++);
                            break;
                        case 'lectures':
                            this.addResolvedTitleAsCrumb<Lecture>(this.lectureService.find(Number(part)), path, part, breadcrumbIndex++);
                            break;
                        case 'exams':
                            examId = Number(part);
                            this.addResolvedTitleAsCrumb<Exam>(this.examService.find(courseId, examId), path, part, breadcrumbIndex++);
                            break;
                        case 'import':
                            // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                            // This has to go in the future
                            this.addTranslationAsCrumb('import', path, breadcrumbIndex++);
                            break;
                        case 'example-submissions':
                            // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                            this.addTranslationAsCrumb('example-submissions', path, breadcrumbIndex++);
                            break;
                        case 'text-feedback-conflict':
                            // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                            this.addTranslationAsCrumb('text-feedback-conflict', path, breadcrumbIndex++);
                            break;
                        // No breadcrumbs for those segments
                        case 'goal-management':
                        case 'unit-management':
                        case 'exercise-groups':
                        case 'student-exams':
                        case 'test-runs':
                        default:
                            break;
                    }
                } else {
                    // When we're not dealing with an ID we need to translate the current part
                    // The translation might still depend on the previous parts
                    switch (part) {
                        // No breadcrumbs for those segments
                        case 'reset':
                        case 'groups':
                        case 'code-editor':
                        case 'admin':
                        case 'ide':
                        case 'example-submissions':
                        case 'text-units':
                        case 'exercise-units':
                        case 'attachment-units':
                        case 'video-units':
                        case 'text-feedback-conflict':
                        case 'grading':
                            break;
                        default:
                            // Special cases:
                            if (previousPart === 'user-management') {
                                // - Users display their login name directly as crumb
                                this.addBreadcrumb(path, part, breadcrumbIndex++, false);
                                break;
                            } else if (previousPart === 'example-submissions') {
                                // - Creating a new example submission should display the text for example submissions
                                this.addTranslationAsCrumb('example-submissions', path, breadcrumbIndex++);
                                break;
                            } else if (previousPart === 'grading') {
                                // - Opening a grading tab should only display the text for grading
                                this.addTranslationAsCrumb('grading', path, breadcrumbIndex++);
                                break;
                            } else if (previousPart === 'code-editor' && part === 'new') {
                                // - This route is bogus an needs to be replaced in the future, display no crumb
                                break;
                            } else if (previousPart === 'programming-exercises' && part === 'import') {
                                // - This route is bogus an needs to be replaced in the future, display no crumb
                                break;
                            }

                            this.addTranslationAsCrumb(part, path, breadcrumbIndex++);
                            break;
                    }

                    // Special case: Don't add invalid breadcrumbs for the exercise group segments
                    if ('exercise-groups' === part) {
                        return;
                    }

                    previousPart = part;
                }
            }

            child = child.firstChild;
        }
    }

    addBreadcrumb(uri: string, label: string, index: number, translate: boolean) {
        const crumb = new Breadcrumb();
        crumb.label = label;
        crumb.translate = translate;
        crumb.uri = uri;
        this.breadcrumbs[index] = crumb;
    }

    addResolvedTitleAsCrumb<T>(observable: Observable<HttpResponse<T>>, path: string, part: string, index: number) {
        // Insert the part until we fetched a title from the server
        this.addBreadcrumb(path, part, index, false);
        observable.subscribe(
            (response: HttpResponse<T>) => {
                const title = !!response.body ? response.body!['title'] : part;
                this.addBreadcrumb(path, title, index, false);
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
    }

    addTranslationAsCrumb(part: string, path: string, index: number) {
        let label = '';
        let translate = false;
        const key = part.split('-').join('_');
        if (this.breadcrumbTranslation[key]) {
            label = this.breadcrumbTranslation[key];
            translate = true;
        } else {
            label = part;
        }

        this.addBreadcrumb(path, label, index, translate);
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
        this.loginService.logout();
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
