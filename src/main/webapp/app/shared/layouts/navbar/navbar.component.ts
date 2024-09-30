import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Subscription } from 'rxjs';
import { filter, map, tap } from 'rxjs/operators';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core/user/user.model';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { PROFILE_IRIS, PROFILE_LOCALCI, PROFILE_LTI, VERSION } from 'app/app.constants';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LoginService } from 'app/core/login/login.service';
import { ActivatedRoute, Event, NavigationEnd, Router } from '@angular/router';
import { ExamParticipationService } from 'app/exam/participate/exam-participation.service';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { LANGUAGES } from 'app/core/language/language.constants';
import {
    faBars,
    faBell,
    faBook,
    faBookOpen,
    faBroom,
    faChevronRight,
    faCog,
    faEye,
    faFlag,
    faGears,
    faHeart,
    faList,
    faLock,
    faPuzzlePiece,
    faRobot,
    faSignOutAlt,
    faStamp,
    faTachometerAlt,
    faTasks,
    faThLarge,
    faThList,
    faToggleOn,
    faUniversity,
    faUser,
    faUserPlus,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/entities/exercise.model';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { onError } from 'app/shared/util/global.utils';
import { StudentExam } from 'app/entities/student-exam.model';
import { Title } from '@angular/platform-browser';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

@Component({
    selector: 'jhi-navbar',
    templateUrl: './navbar.component.html',
    styleUrls: ['navbar.scss'],
})
export class NavbarComponent implements OnInit, OnDestroy {
    inProduction: boolean;
    testServer: boolean;
    isNavbarCollapsed: boolean;
    isTourAvailable: boolean;
    gitCommitId: string;
    gitBranchName: string;
    gitTimestamp: string;
    gitUsername: string;
    isBuildAgentDetails = false;
    languages = LANGUAGES;
    openApiEnabled?: boolean;
    modalRef: NgbModalRef;
    version: string;
    currAccount?: User;
    isRegistrationEnabled = false;
    passwordResetEnabled = false;
    breadcrumbs: Breadcrumb[];
    breadcrumbSubscriptions: Subscription[];
    isCollapsed: boolean;
    iconsMovedToMenu: boolean;
    isNavbarNavVertical: boolean;
    isExamActive = false;
    examActiveCheckFuture?: ReturnType<typeof setTimeout>;
    irisEnabled: boolean;
    localCIActive: boolean = false;
    ltiEnabled: boolean;
    standardizedCompetenciesEnabled = false;
    agentName?: string;
    isExamStarted = false;

    courseTitle?: string;
    exerciseTitle?: string;
    lectureTitle?: string;
    examTitle?: string;

    // Icons
    faBars = faBars;
    faThLarge = faThLarge;
    faThList = faThList;
    faUser = faUser;
    faBell = faBell;
    faUniversity = faUniversity;
    faEye = faEye;
    faCog = faCog;
    faWrench = faWrench;
    faLock = faLock;
    faStamp = faStamp;
    faFlag = faFlag;
    faBook = faBook;
    faTasks = faTasks;
    faList = faList;
    faRobot = faRobot;
    faHeart = faHeart;
    faTachometerAlt = faTachometerAlt;
    faToggleOn = faToggleOn;
    faBookOpen = faBookOpen;
    faUserPlus = faUserPlus;
    faSignOutAlt = faSignOutAlt;
    faGears = faGears;
    faPuzzlePiece = faPuzzlePiece;
    faChevronRight = faChevronRight;
    faBroom = faBroom;

    private standardizedCompetencySubscription: Subscription;
    private authStateSubscription: Subscription;
    private routerEventSubscription: Subscription;
    private queryParamsSubscription: Subscription;
    private examStartedSubscription: Subscription;
    private studentExam?: StudentExam;
    private examId?: number;
    private routeExamId = 0;
    private lastRouteUrlSegment?: string;

    constructor(
        public guidedTourService: GuidedTourService,
        private accountService: AccountService,
        private loginService: LoginService,
        private translateService: TranslateService,
        private profileService: ProfileService,
        private participationWebsocketService: ParticipationWebsocketService,
        private router: Router,
        private route: ActivatedRoute,
        private examParticipationService: ExamParticipationService,
        private serverDateService: ArtemisServerDateService,
        private alertService: AlertService,
        private exerciseService: ExerciseService,
        private entityTitleService: EntityTitleService,
        private titleService: Title,
        private featureToggleService: FeatureToggleService,
    ) {
        this.version = VERSION ? VERSION : '';
        this.isNavbarCollapsed = true;
        this.subscribeToNavigationEventsForExamId();
        this.onResize();
    }

    @HostListener('window:resize')
    onResize() {
        // Figure out breakpoints depending on available menu options and length of login
        let neededWidthToNotRequireCollapse: number;
        let neededWidthToDisplayCollapsedOptionsHorizontally = 150;
        let neededWidthForIconOptionsToBeInMainNavBar: number;
        if (this.currAccount) {
            const nameLength = (this.currAccount.login?.length ?? 0) * 8;
            neededWidthForIconOptionsToBeInMainNavBar = 580 + nameLength;
            neededWidthToNotRequireCollapse = 700 + nameLength;

            const hasServerAdminOption = this.accountService.hasAnyAuthorityDirect([Authority.ADMIN]);
            const hasCourseManageOption = this.accountService.hasAnyAuthorityDirect([Authority.TA, Authority.INSTRUCTOR, Authority.EDITOR, Authority.ADMIN]);
            if (hasCourseManageOption) {
                neededWidthToNotRequireCollapse += 200;
                neededWidthToDisplayCollapsedOptionsHorizontally += 200;
            }
            if (hasServerAdminOption) {
                neededWidthToNotRequireCollapse += 225;
                neededWidthToDisplayCollapsedOptionsHorizontally += 225;
            }
        } else {
            // For login screen, we only see language and theme selectors which are smaller
            neededWidthToNotRequireCollapse = 510;
            neededWidthForIconOptionsToBeInMainNavBar = 430;
        }

        this.isCollapsed = window.innerWidth < neededWidthToNotRequireCollapse;
        this.isNavbarNavVertical = window.innerWidth < Math.max(neededWidthToDisplayCollapsedOptionsHorizontally, 480);
        this.iconsMovedToMenu = window.innerWidth < neededWidthForIconOptionsToBeInMainNavBar;
    }

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProduction = profileInfo.inProduction;
                this.testServer = profileInfo.testServer ?? false;
                this.openApiEnabled = profileInfo.openApiEnabled;
                this.gitCommitId = profileInfo.git.commit.id.abbrev;
                this.gitBranchName = profileInfo.git.branch;
                this.gitTimestamp = new Date(profileInfo.git.commit.time).toUTCString();
                this.gitUsername = profileInfo.git.commit.user.name;
                this.irisEnabled = profileInfo.activeProfiles.includes(PROFILE_IRIS);
                this.localCIActive = profileInfo?.activeProfiles.includes(PROFILE_LOCALCI);
                this.ltiEnabled = profileInfo?.activeProfiles.includes(PROFILE_LTI);
            }
        });

        this.standardizedCompetencySubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.StandardizedCompetencies).subscribe((isActive) => {
            this.standardizedCompetenciesEnabled = isActive;
        });

        this.subscribeForGuidedTourAvailability();

        // The current user is needed to hide menu items for not logged-in users.
        this.authStateSubscription = this.accountService
            .getAuthenticationState()
            .pipe(
                tap((user: User) => {
                    this.currAccount = user;
                    this.passwordResetEnabled = user?.internal || false;
                    this.onResize();
                }),
            )
            .subscribe();

        this.examParticipationService.currentlyLoadedStudentExam.subscribe((studentExam) => {
            this.studentExam = studentExam;
            this.checkExamActive();
        });
        this.examStartedSubscription = this.examParticipationService.examIsStarted$.subscribe((isStarted) => {
            this.isExamStarted = isStarted;
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
        if (this.examActiveCheckFuture) {
            clearTimeout(this.examActiveCheckFuture);
        }
        if (this.standardizedCompetencySubscription) {
            this.standardizedCompetencySubscription.unsubscribe();
        }
        this.queryParamsSubscription?.unsubscribe();
        this.examStartedSubscription?.unsubscribe();
    }

    breadcrumbTranslation: { [key: string]: string } = {
        new: 'global.generic.create',
        process: 'artemisApp.attachmentUnit.createAttachmentUnits.pageTitle',
        verify_attendance: 'artemisApp.examManagement.examStudents.verifyChecks',
        create: 'global.generic.create',
        start: 'global.generic.start',
        edit: 'global.generic.edit',
        audits: 'audits.title',
        configuration: 'configuration.title',
        feature_toggles: 'featureToggles.title',
        health: 'health.title',
        logs: 'logs.title',
        docs: 'global.menu.admin.apidocs',
        metrics: 'metrics.title',
        user_statistics: 'statistics.title',
        user_management: 'artemisApp.userManagement.home.title',
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
        lti_configuration: 'artemisApp.lti.home.title',
        teams: 'artemisApp.team.home.title',
        exercise_hints: 'artemisApp.exerciseHint.home.title',
        ratings: 'artemisApp.ratingList.pageTitle',
        competency_management: 'artemisApp.competency.manage.title',
        prerequisite_management: 'artemisApp.prerequisite.manage.title',
        learning_path_management: 'artemisApp.learningPath.manageLearningPaths.title',
        assessment_locks: 'artemisApp.assessment.locks.home.title',
        apollon_diagrams: 'artemisApp.apollonDiagram.home.title',
        communication: 'artemisApp.metis.communication.label',
        scores: 'entity.action.scores',
        assessment: 'artemisApp.assessment.assessment',
        export: 'artemisApp.quizExercise.export.export',
        re_evaluate: 'entity.action.re-evaluate',
        solution: 'artemisApp.quizExercise.solution',
        preview: 'artemisApp.quizExercise.previewMode',
        quiz_statistic: 'artemisApp.quizExercise.statistics',
        quiz_point_statistic: 'artemisApp.quizExercise.statistics',
        import: 'artemisApp.exercise.import.table.doImport',
        import_from_file: 'artemisApp.programmingExercise.importFromFile.title',
        plagiarism: 'artemisApp.plagiarism.plagiarismDetection',
        example_solution: 'artemisApp.modelingExercise.exampleSolution',
        example_submissions: 'artemisApp.exampleSubmission.home.title',
        example_submission_editor: 'artemisApp.exampleSubmission.home.editor',
        grading: 'artemisApp.programmingExercise.configureGrading.shortTitle',
        test: 'artemisApp.editor.home.title',
        ide: 'artemisApp.editor.home.title',
        lectures: 'artemisApp.lecture.home.title',
        attachments: 'artemisApp.lecture.attachments.title',
        unit_management: 'artemisApp.lectureUnit.home.title',
        exams: 'artemisApp.examManagement.title',
        exercise_groups: 'artemisApp.examManagement.exerciseGroups',
        quiz_pool: 'artemisApp.examManagement.quizPool',
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
        organization_management: 'artemisApp.organizationManagement.title',
        participant_scores: 'artemisApp.participantScores.pageTitle',
        course_statistics: 'statistics.course_statistics_title',
        grading_system: 'artemisApp.gradingSystem.title',
        grading_key: 'artemisApp.gradingSystem.title',
        exercise_statistics: 'exercise-statistics.title',
        tutor_effort_statistics: 'artemisApp.textExercise.tutorEffortStatistics.title',
        user_settings: 'artemisApp.userSettings.title',
        detailed: 'artemisApp.gradingSystem.detailedTab.title',
        interval: 'artemisApp.gradingSystem.intervalTab.title',
        plagiarism_cases: 'artemisApp.plagiarism.cases.pageTitle',
        code_hint_management: 'artemisApp.codeHint.management.title',
        tutorial_groups_management: 'artemisApp.pages.tutorialGroupsManagement.title',
        tutorial_groups: 'artemisApp.breadcrumb.title',
        registered_students: 'artemisApp.pages.registeredStudents.title',
        sessions: 'artemisApp.pages.tutorialGroupSessionManagement.title',
        tutorial_free_days: 'artemisApp.pages.tutorialFreePeriodsManagement.title',
        tutorial_groups_checklist: 'artemisApp.pages.checklist.title',
        create_tutorial_groups_configuration: 'artemisApp.pages.createTutorialGroupsConfiguration.title',
        privacy_statement: 'artemisApp.legal.privacyStatement.title',
        imprint: 'artemisApp.legal.imprint.title',
        edit_build_plan: 'artemisApp.programmingExercise.buildPlanEditor',
        suspicious_behavior: 'artemisApp.examManagement.suspiciousBehavior.title',
        suspicious_sessions: 'artemisApp.examManagement.suspiciousBehavior.suspiciousSessions.title',
        exam_timeline: 'artemisApp.examTimeline.breadcrumb',
        iris_settings: 'artemisApp.iris.settings.title.breadcrumb',
        generate: 'entity.action.generate',
        build_queue: 'artemisApp.buildQueue.title',
        build_agents: 'artemisApp.buildAgents.title',
        commit_history: 'artemisApp.repository.commitHistory.title',
        commit_details: 'artemisApp.repository.commitHistory.commitDetails.title',
        repository: 'artemisApp.repository.title',
        standardized_competencies: 'artemisApp.standardizedCompetency.manage.title',
        prerequisites: 'artemisApp.prerequisite.title',
        import_standardized: 'artemisApp.standardizedCompetency.courseImport.title',
        cleanup_service: 'cleanupService.title',
    };

    studentPathBreadcrumbTranslations: { [key: string]: string } = {
        exams: 'artemisApp.courseOverview.menu.exams',
        test_exam: 'artemisApp.courseOverview.menu.testExam',
        exercises: 'artemisApp.courseOverview.menu.exercises',
        lectures: 'artemisApp.courseOverview.menu.lectures',
        dashboard: 'artemisApp.courseOverview.menu.dashboard',
        competencies: 'artemisApp.courseOverview.menu.competencies',
        learning_path: 'artemisApp.courseOverview.menu.learningPath',
        lecture_unit: 'artemisApp.learningPath.breadcrumbs.lectureUnit',
        exercise: 'artemisApp.learningPath.breadcrumbs.exercise',
        statistics: 'artemisApp.courseOverview.menu.statistics',
        discussion: 'artemisApp.metis.communication.label',
        messages: 'artemisApp.conversationsLayout.breadCrumbLabel',
        code_editor: 'artemisApp.editor.breadCrumbTitle',
        participate: 'artemisApp.submission.detail.title',
        live: 'artemisApp.submission.detail.title',
        courses: 'artemisApp.course.home.title',
        enroll: 'artemisApp.studentDashboard.enroll.title',
    };

    /**
     * Fills the breadcrumbs array with entries for admin and course-management routes
     */
    private buildBreadcrumbs(fullURI: string): void {
        this.breadcrumbs = [];
        this.breadcrumbSubscriptions?.forEach((subscription) => subscription.unsubscribe());
        this.breadcrumbSubscriptions = [];
        this.initTabTitles();

        if (!fullURI) {
            return;
        }

        // Temporarily restrict routes
        if (!fullURI.startsWith('/admin') && !fullURI.startsWith('/course-management') && !fullURI.startsWith('/courses')) {
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
        } catch (e) {
            /* empty */
        }
        this.buildTabTitles();
    }

    /**
     * Adds a breadcrumb depending on the given entityID as string
     *
     * @param currentPath the complete path up until the breadcrumb to add
     * @param segment the current url segment (string representation of an entityID) to add a crumb for
     */
    private addBreadcrumbForNumberSegment(currentPath: string, segment: string): void {
        const isStudentPath = currentPath.startsWith('/courses');
        if (isStudentPath) {
            switch (this.lastRouteUrlSegment) {
                case 'code-editor':
                case 'test-exam':
                case 'repository':
                case 'participate':
                    this.addTranslationAsCrumb(currentPath, this.lastRouteUrlSegment);
                    return;
                case 'exercises':
                    this.addResolvedTitleAsCrumb(EntityType.EXERCISE, [Number(segment)], currentPath, segment);
                    return;
                default:
                    const exercisesMatcher = this.lastRouteUrlSegment?.match(/.+-exercises/);
                    if (exercisesMatcher) {
                        this.addResolvedTitleAsCrumb(EntityType.EXERCISE, [Number(segment)], currentPath.replace(`exercises/${exercisesMatcher[0]}`, 'exercises'), 'exercises');
                        return;
                    }
                    break;
            }
        }
        switch (this.lastRouteUrlSegment) {
            // Displays the path segment as breadcrumb (no other title exists)
            case 'system-notification-management':
            case 'teams':
            case 'repository':
            case 'code-editor':
                this.addBreadcrumb(currentPath, segment, false);
                break;
            case 'course-management':
            case 'courses':
                this.addResolvedTitleAsCrumb(EntityType.COURSE, [Number(segment)], currentPath, segment);
                break;
            case 'exercises':
                // Special case: A raw /course-management/XXX/exercises/XXX doesn't work, we need to add the exercise type
                // For example /course-management/XXX/programming-exercises/XXX
                this.addExerciseCrumb(Number(segment), currentPath);
                break;
            case 'text-exercises':
            case 'modeling-exercises':
            case 'file-upload-exercises':
            case 'programming-exercises':
            case 'quiz-exercises':
            case 'assessment-dashboard':
                this.addResolvedTitleAsCrumb(EntityType.EXERCISE, [Number(segment)], currentPath, segment);
                break;
            case 'exercise-hints':
                // obtain the exerciseId of the current path
                // current path of form '/course-management/:courseId/exercises/:exerciseId/...

                const exerciseId = currentPath.split('/')[4];
                this.addResolvedTitleAsCrumb(EntityType.HINT, [Number(segment), Number(exerciseId)], currentPath, segment);
                break;
            case 'apollon-diagrams':
                this.addResolvedTitleAsCrumb(EntityType.DIAGRAM, [Number(segment)], currentPath, segment);
                break;
            case 'lectures':
                this.addResolvedTitleAsCrumb(EntityType.LECTURE, [Number(segment)], currentPath, segment);
                break;
            case 'competencies':
                this.addResolvedTitleAsCrumb(EntityType.COMPETENCY, [Number(segment)], currentPath, segment);
                break;
            case 'exams':
                this.routeExamId = Number(segment);
                this.addResolvedTitleAsCrumb(EntityType.EXAM, [this.routeExamId], currentPath, segment);
                break;
            case 'organization-management':
                this.addResolvedTitleAsCrumb(EntityType.ORGANIZATION, [Number(segment)], currentPath, segment);
                break;
            case 'tutorial-groups':
                this.addResolvedTitleAsCrumb(EntityType.TUTORIAL_GROUP, [Number(segment)], currentPath, segment);
                break;
            case 'import':
                // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                // This has to go in the future
                this.addTranslationAsCrumb(currentPath, 'import');
                break;
            case 'import-from-file':
                this.addTranslationAsCrumb(currentPath, 'import-from-file');
                break;
            case 'suspicious-behavior':
                this.addTranslationAsCrumb(currentPath, 'suspicious-behavior');
                break;
            case 'suspicious-sessions':
                this.addTranslationAsCrumb(currentPath, 'suspicious-sessions');
                break;
            case 'example-submissions':
                // Special case: Don't display the ID here but the name directly (clicking the ID wouldn't work)
                this.addTranslationAsCrumb(currentPath, 'example-submission-editor');
                break;
            case 'attachments':
                this.addBreadcrumb(currentPath, segment, false);
                break;
            // No breadcrumbs for those segments
            case 'competency-management':
            case 'prerequisite-management':
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
        const isStudentPath = currentPath.startsWith('/courses');
        this.isBuildAgentDetails = currentPath.startsWith('/admin/build-agents/') && segment == 'details';

        if (isStudentPath) {
            if (segment === 'repository') {
                return;
            }
            const exercisesMatcher = segment?.match(/.+-exercises/);
            if (exercisesMatcher) {
                return;
            }
        }

        if (this.isBuildAgentDetails) {
            this.queryParamsSubscription = this.route.queryParams.subscribe((params) => {
                this.agentName = params['agentName'];
                if (this.agentName) {
                    segment = decodeURIComponent(this.agentName);
                }
            });
        }
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
            case 'grading':
            case 'mc-question-statistic':
            case 'dnd-question-statistic':
            case 'sa-question-statistic':
            case 'test-exam':
            case 'participate':
            case 'overview':
                break;
            case 'example-submissions':
                // Hide example submission dashboard for non instructor users
                if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR])) {
                    this.addTranslationAsCrumb(currentPath, segment);
                }
                break;
            case 'submissions':
                // only a scores list exists, no special one for submissions
                const updatedLink = currentPath.replace('/submissions/', '/scores/');
                this.addTranslationAsCrumb(updatedLink, 'submissions');
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
                    // - This route is bogus and needs to be replaced in the future, display no crumb
                    break;
                } else if (this.lastRouteUrlSegment?.endsWith('-exercises') && segment === 'import') {
                    // - This route is bogus and needs to be replaced in the future, display no crumb
                    break;
                } else if (this.lastRouteUrlSegment === 'exercise-groups') {
                    // - Don't display '<type>-exercises' because it has no associated route
                    break;
                } else if (this.lastRouteUrlSegment === 'exams' && segment === 'import') {
                    // This route is only used internally when opening the exam import modal and therefore shouldn't be displayed.
                    // When opening the exam-update.component, the id of the to be imported exam is appended (-> case `import`).
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
     * @return the created breadcrumb object
     */
    private addBreadcrumb(uri: string, label: string, translate: boolean): Breadcrumb {
        return this.setBreadcrumb(uri, label, translate, this.breadcrumbs.length);
    }

    /**
     * Sets a breadcrumb in the list of breadcrumbs at the given index
     *
     * @param uri the uri/path for the breadcrumb
     * @param label the displayed label for the breadcrumb
     * @param translate if the label should be translated
     * @param index the index of the breadcrumbs array to set the breadcrumb at
     * @return the created breadcrumb object
     */
    private setBreadcrumb(uri: string, label: string, translate: boolean, index: number): Breadcrumb {
        const crumb = new Breadcrumb();
        crumb.label = label;
        crumb.translate = translate;
        crumb.uri = uri;
        this.breadcrumbs[index] = crumb;
        return crumb;
    }

    /**
     * Uses the server response to add a title for a breadcrumb
     * While waiting for the response or in case of an error the segment is displayed directly as fallback
     *
     * @param type the type of the entity
     * @param ids the ids of the entity
     * @param uri the uri/path for the breadcrumb
     * @param segment the current url segment to add a breadcrumb for
     */
    private addResolvedTitleAsCrumb(type: EntityType, ids: number[], uri: string, segment: string): void {
        // Insert the segment until we fetched a title from the server to insert at the correct index
        let crumb = this.addBreadcrumb(uri, segment, false);

        this.breadcrumbSubscriptions.push(
            this.entityTitleService.getTitle(type, ids).subscribe({
                next: (title: string) => {
                    crumb = this.setBreadcrumb(uri, title, false, this.breadcrumbs.indexOf(crumb));
                    this.setTabTitles(type, title);
                },
            }),
        );
    }

    /**
     * Adds a link to an exercise to the breadcrumbs array. The link depends on the type of the exercise, so we need to fetch it first
     * @param exerciseId the id of the exercise
     * @param currentPath the initial path for the breadcrumb
     */
    private addExerciseCrumb(exerciseId: number, currentPath: string): void {
        // Add dummy breadcrumb
        const crumb = this.addBreadcrumb('', '', false);
        const isStudentPath = currentPath.startsWith('/courses');
        this.exerciseService.find(exerciseId).subscribe({
            next: (response: HttpResponse<Exercise>) => {
                // If the response doesn't contain the needed data, remove the breadcrumb as we can not successfully link to it
                if (!response?.body?.title || !response?.body?.type) {
                    this.breadcrumbs.splice(this.breadcrumbs.indexOf(crumb), 1);
                } else {
                    // If all data is there, overwrite the breadcrumb with the correct link
                    const replaceValue = isStudentPath ? `/exercises/${response.body.type}-exercises/` : `/${response.body.type}-exercises/`;
                    this.setBreadcrumb(currentPath.replace('/exercises/', replaceValue), response.body.title, false, this.breadcrumbs.indexOf(crumb));
                }
            },
            // Same as if data isn't available
            error: () => this.breadcrumbs.splice(this.breadcrumbs.indexOf(crumb), 1),
        });
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
        if (uri.startsWith('/courses') && this.studentPathBreadcrumbTranslations[key]) {
            this.addBreadcrumb(uri, this.studentPathBreadcrumbTranslations[key], true);
        } else if (this.breadcrumbTranslation[key]) {
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
        if (this.currAccount) {
            this.accountService.updateLanguage(languageKey).subscribe({
                next: () => {
                    this.translateService.use(languageKey);
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        } else {
            this.translateService.use(languageKey);
        }
    }

    collapseNavbar() {
        this.isNavbarCollapsed = true;
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    logout() {
        this.collapseNavbar();
        this.router.navigate(['/']).then((res) => {
            if (res) {
                this.participationWebsocketService.resetLocalCache();
                this.loginService.logout(true);
            }
        });
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
     * Subscribes to navigation end events to look for an exam id in the URL which indicates that we're in the student view of an exam.
     */
    subscribeToNavigationEventsForExamId() {
        this.routerEventSubscription = this.router.events.pipe(filter((event: Event) => event instanceof NavigationEnd)).subscribe((event: NavigationEnd) => {
            if (event.url.includes('management')) {
                this.examId = undefined;
                return;
            }

            this.route.root.firstChild?.paramMap.pipe(map((params) => params.get('examId'))).subscribe((examId) => {
                this.examId = examId ? Number(examId) : undefined;
                this.checkExamActive();
            });
        });
    }

    /**
     * Check if the student is currently working on an active exam.
     * If yes, hide some elements like notifications and breadcrumbs.
     * Schedules a check for this at the next relevant timestamp (exam start or exam end, whichever comes next)
     */
    checkExamActive() {
        if (this.examActiveCheckFuture) {
            clearTimeout(this.examActiveCheckFuture);
            this.examActiveCheckFuture = undefined;
        }

        if (
            this.studentExam?.exam &&
            this.studentExam.exam.id === this.examId &&
            !this.studentExam.exam.testExam &&
            !this.studentExam.testRun &&
            this.studentExam.exam.startDate &&
            this.studentExam.exam.endDate &&
            !this.studentExam.submitted
        ) {
            const serverTime = this.serverDateService.now();
            // As end date, we use the working time of this student plus the grace period
            const workingTime = this.studentExam.workingTime ?? this.studentExam.exam.workingTime;
            const examEndWithGracePeriod = (workingTime ? this.studentExam.exam.startDate.add(workingTime, 'seconds') : this.studentExam.exam.endDate).add(
                this.studentExam.exam.gracePeriod ?? 0,
                'seconds',
            );
            this.isExamActive = serverTime.isBetween(this.studentExam.exam.startDate, examEndWithGracePeriod);

            const timeUntilStart = this.studentExam.exam.startDate.diff(serverTime);
            const timeUntilEnd = examEndWithGracePeriod.diff(serverTime);
            const timeUntilNextChange = timeUntilStart >= 0 ? timeUntilStart : timeUntilEnd;
            if (timeUntilNextChange > 0) {
                this.examActiveCheckFuture = setTimeout(this.checkExamActive.bind(this), timeUntilNextChange + 100);
            }
        } else {
            this.isExamActive = false;
        }
    }

    /**
     * Method to build the tab titles based on the breadcrumbs.
     * The tab titles are build from the most specific to the most general title.
     * If the tab title is not defined, the titles in the Router Modules are used instead.
     */
    buildTabTitles() {
        // Include the most specific title into the tab title, but only if the title is meant to be displayed to the user, i.e. should be translated.
        const generalTitle = this.breadcrumbs[this.breadcrumbs.length - 1].translate
            ? this.translateService.instant(this.breadcrumbs[this.breadcrumbs.length - 1].label)
            : undefined;
        const titles = [generalTitle, this.exerciseTitle, this.examTitle, this.lectureTitle, this.courseTitle].filter((title) => title !== undefined).join(' | ');
        // No need have a dynamic title on the start page -> use the title defined in the Router modules.
        if (titles && this.breadcrumbs.length > 1) {
            this.titleService.setTitle(titles);
        }
    }

    /**
     * Initialize the attributes for the tab titles to undefined, as they are defined during the building of the breadcrumbs
     */
    initTabTitles() {
        // The course title is not set to undefined, as there is a change detection in the setTabTitle Method
        this.exerciseTitle = undefined;
        this.lectureTitle = undefined;
        this.examTitle = undefined;
    }

    /**
     * Set the title of the respective attribute based on the response from the entityTitleService
     * @param type the type of the entity
     * @param title the title of the entity
     */
    setTabTitles(type: EntityType, title: string) {
        switch (type) {
            case EntityType.COURSE:
                if (this.courseTitle != title) {
                    this.courseTitle = title;
                    // If the courseTitle changes, we need to rebuild the tab titles
                    this.buildTabTitles();
                }
                break;
            case EntityType.EXERCISE:
                this.exerciseTitle = title;
                break;
            case EntityType.EXAM:
                this.examTitle = title;
                break;
            case EntityType.LECTURE:
                this.lectureTitle = title;
                break;
        }
    }
}

class Breadcrumb {
    label: string;
    uri: string;
    translate: boolean;
}
