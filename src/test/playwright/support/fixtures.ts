import { CDPSession } from '@playwright/test';
import { test as base } from './baseFixtures';
import { LoginPage } from './pageobjects/LoginPage';
import { UserCredentials } from './users';
import { NavigationBar } from './pageobjects/NavigationBar';
import { CourseManagementAPIRequests } from './requests/CourseManagementAPIRequests';
import { CourseManagementPage } from './pageobjects/course/CourseManagementPage';
import { CompetencyManagementPage } from './pageobjects/course/CompetencyManagementPage';
import { CourseCreationPage } from './pageobjects/course/CourseCreationPage';
import { UserManagementAPIRequests } from './requests/UserManagementAPIRequests';
import { Commands } from './commands';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';
import { CourseOverviewPage } from './pageobjects/course/CourseOverviewPage';
import { CourseMessagesPage } from './pageobjects/course/CourseMessagesPage';
import { ExamAPIRequests } from './requests/ExamAPIRequests';
import { CommunicationAPIRequests } from './requests/CommunicationAPIRequests';
import { CourseCommunicationPage } from './pageobjects/course/CourseCommunicationPage';
import { CourseOnboardingPage } from './pageobjects/course/CourseOnboardingPage';
import { LectureManagementPage } from './pageobjects/lecture/LectureManagementPage';
import { LectureCreationPage } from './pageobjects/lecture/LectureCreationPage';
import { ExamCreationPage } from './pageobjects/exam/ExamCreationPage';
import { ExamDetailsPage } from './pageobjects/exam/ExamDetailsPage';
import { ExamManagementPage } from './pageobjects/exam/ExamManagementPage';
import { ExamExerciseGroupCreationPage } from './pageobjects/exam/ExamExerciseGroupCreationPage';
import { ExamNavigationBar } from './pageobjects/exam/ExamNavigationBar';
import { ExamParticipationPage } from './pageobjects/exam/ExamParticipationPage';
import { ExamStartEndPage } from './pageobjects/exam/ExamStartEndPage';
import { CoursesPage } from './pageobjects/course/CoursesPage';
import { ExamAssessmentPage } from './pageobjects/assessment/ExamAssessmentPage';
import { CourseAssessmentDashboardPage } from './pageobjects/assessment/CourseAssessmentDashboardPage';
import { ExerciseAssessmentDashboardPage } from './pageobjects/assessment/ExerciseAssessmentDashboardPage';
import { ModelingExerciseAssessmentEditor } from './pageobjects/assessment/ModelingExerciseAssessmentEditor';
import { StudentAssessmentPage } from './pageobjects/assessment/StudentAssessmentPage';
import { ModelingEditor } from './pageobjects/exercises/modeling/ModelingEditor';
import { OnlineEditorPage } from './pageobjects/exercises/programming/OnlineEditorPage';
import { MultipleChoiceQuiz } from './pageobjects/exercises/quiz/MultipleChoiceQuiz';
import { TextEditorPage } from './pageobjects/exercises/text/TextEditorPage';
import { ExamExerciseGroupsPage } from './pageobjects/exam/ExamExerciseGroupsPage';
import { ProgrammingExerciseCreationPage } from './pageobjects/exercises/programming/ProgrammingExerciseCreationPage';
import { QuizExerciseCreationPage } from './pageobjects/exercises/quiz/QuizExerciseCreationPage';
import { StudentExamManagementPage } from './pageobjects/exam/StudentExamManagementPage';
import { TextExerciseCreationPage } from './pageobjects/exercises/text/TextExerciseCreationPage';
import { ModelingExerciseCreationPage } from './pageobjects/exercises/modeling/ModelingExerciseCreationPage';
import { ExamTestRunPage } from './pageobjects/exam/ExamTestRunPage';
import { CourseManagementExercisesPage } from './pageobjects/course/CourseManagementExercisesPage';
import { TextExerciseExampleSubmissionsPage } from './pageobjects/exercises/text/TextExerciseExampleSubmissionsPage';
import { TextExerciseExampleSubmissionCreationPage } from './pageobjects/exercises/text/TextExerciseExampleSubmissionCreationPage';
import { TextExerciseAssessmentPage } from './pageobjects/assessment/TextExerciseAssessmentPage';
import { ExerciseResultPage } from './pageobjects/exercises/ExerciseResultPage';
import { TextExerciseFeedbackPage } from './pageobjects/exercises/text/TextExerciseFeedbackPage';
import { ShortAnswerQuiz } from './pageobjects/exercises/quiz/ShortAnswerQuiz';
import { DragAndDropQuiz } from './pageobjects/exercises/quiz/DragAndDropQuiz';
import { ModelingExerciseFeedbackPage } from './pageobjects/exercises/modeling/ModelingExerciseFeedbackPage';
import { ProgrammingExerciseAssessmentPage } from './pageobjects/assessment/ProgrammingExerciseAssessmentPage';
import { ProgrammingExerciseFeedbackPage } from './pageobjects/exercises/programming/ProgrammingExerciseFeedbackPage';
import { CodeAnalysisGradingPage } from './pageobjects/exercises/programming/CodeAnalysisGradingPage';
import { ScaFeedbackModal } from './pageobjects/exercises/programming/ScaFeedbackModal';
import { FileUploadExerciseCreationPage } from './pageobjects/exercises/file-upload/FileUploadExerciseCreationPage';
import { FileUploadEditorPage } from './pageobjects/exercises/file-upload/FileUploadEditorPage';
import { FileUploadExerciseAssessmentPage } from './pageobjects/assessment/FileUploadExerciseAssessmentPage';
import { FileUploadExerciseFeedbackPage } from './pageobjects/exercises/file-upload/FileUploadExerciseFeedbackPage';
import { ProgrammingExerciseOverviewPage } from './pageobjects/exercises/programming/ProgrammingExerciseOverviewPage';
import { RepositoryPage } from './pageobjects/exercises/programming/RepositoryPage';
import { ExamGradingPage } from './pageobjects/exam/ExamGradingPage';
import { ExamScoresPage } from './pageobjects/exam/ExamScoresPage';
import { ProgrammingExerciseParticipationsPage } from './pageobjects/exercises/programming/ProgrammingExerciseParticipationsPage';
import { ExamResultsPage } from './pageobjects/exam/ExamResultsPage';
import { ExerciseTeamsPage } from './pageobjects/exercises/ExerciseTeamsPage';
import { QuizExerciseOverviewPage } from './pageobjects/exercises/quiz/QuizExerciseOverviewPage';
import { QuizExerciseParticipationPage } from './pageobjects/exercises/quiz/QuizExerciseParticipationPage';
import { ModalDialogBox } from './pageobjects/exam/ModalDialogBox';
import { ExamParticipationActions } from './pageobjects/exam/ExamParticipationActions';
import { AccountManagementAPIRequests } from './requests/AccountManagementAPIRequests';
import { ProgrammingExerciseSubmissionsPage } from './pageobjects/exercises/programming/ProgrammingExercisesSubmissionsPage';

// Define custom types for fixtures
export type ArtemisCommands = {
    login: (credentials: UserCredentials, url?: string) => Promise<void>;
    waitForExerciseBuildToFinish: (exerciseId: number, interval?: number, timeout?: number, minResults?: number) => Promise<void>;
    waitForParticipationBuildToFinish: (participationId: number, interval?: number, timeout?: number) => Promise<StudentParticipation>;
    toggleSidebar: () => Promise<void>;
    createCompetency: (
        courseId: number,
        options: { title: string; description: string; taxonomy?: string; softDueDate?: Date | string; returnToPrevious?: boolean } & Record<string, unknown>,
    ) => Promise<void>;
    createPrerequisite: (
        courseId: number,
        options: { title: string; description: string; taxonomy?: string; softDueDate?: Date | string; returnToPrevious?: boolean } & Record<string, unknown>,
    ) => Promise<void>;
};

export type ArtemisPageObjects = {
    loginPage: LoginPage;
    navigationBar: NavigationBar;
    courseAssessment: CourseAssessmentDashboardPage;
    examAssessment: ExamAssessmentPage;
    exerciseAssessment: ExerciseAssessmentDashboardPage;
    fileUploadExerciseAssessment: FileUploadExerciseAssessmentPage;
    modelingExerciseAssessment: ModelingExerciseAssessmentEditor;
    programmingExerciseAssessment: ProgrammingExerciseAssessmentPage;
    studentAssessment: StudentAssessmentPage;
    textExerciseAssessment: TextExerciseAssessmentPage;
    courseManagement: CourseManagementPage;
    competencyManagement: CompetencyManagementPage;
    courseManagementExercises: CourseManagementExercisesPage;
    courseCreation: CourseCreationPage;
    courseList: CoursesPage;
    courseOverview: CourseOverviewPage;
    courseMessages: CourseMessagesPage;
    courseCommunication: CourseCommunicationPage;
    courseOnboarding: CourseOnboardingPage;
    lectureManagement: LectureManagementPage;
    lectureCreation: LectureCreationPage;
    examCreation: ExamCreationPage;
    examDetails: ExamDetailsPage;
    examExerciseGroupCreation: ExamExerciseGroupCreationPage;
    examExerciseGroups: ExamExerciseGroupsPage;
    examGrading: ExamGradingPage;
    examNavigation: ExamNavigationBar;
    examManagement: ExamManagementPage;
    examParticipation: ExamParticipationPage;
    examParticipationActions: ExamParticipationActions;
    examResultsPage: ExamResultsPage;
    examScores: ExamScoresPage;
    examStartEnd: ExamStartEndPage;
    examTestRun: ExamTestRunPage;
    modalDialog: ModalDialogBox;
    studentExamManagement: StudentExamManagementPage;
    fileUploadExerciseCreation: FileUploadExerciseCreationPage;
    fileUploadExerciseEditor: FileUploadEditorPage;
    fileUploadExerciseFeedback: FileUploadExerciseFeedbackPage;
    modelingExerciseCreation: ModelingExerciseCreationPage;
    modelingExerciseEditor: ModelingEditor;
    modelingExerciseFeedback: ModelingExerciseFeedbackPage;
    programmingExerciseCreation: ProgrammingExerciseCreationPage;
    programmingExerciseEditor: OnlineEditorPage;
    programmingExerciseFeedback: ProgrammingExerciseFeedbackPage;
    programmingExerciseOverview: ProgrammingExerciseOverviewPage;
    programmingExerciseParticipations: ProgrammingExerciseParticipationsPage;
    programmingExerciseRepository: RepositoryPage;
    programmingExercisesScaConfig: CodeAnalysisGradingPage;
    programmingExerciseScaFeedback: ScaFeedbackModal;
    programmingExerciseSubmissions: ProgrammingExerciseSubmissionsPage;
    quizExerciseCreation: QuizExerciseCreationPage;
    quizExerciseDragAndDropQuiz: DragAndDropQuiz;
    quizExerciseMultipleChoice: MultipleChoiceQuiz;
    quizExerciseOverview: QuizExerciseOverviewPage;
    quizExerciseParticipation: QuizExerciseParticipationPage;
    quizExerciseShortAnswerQuiz: ShortAnswerQuiz;
    textExerciseCreation: TextExerciseCreationPage;
    textExerciseEditor: TextEditorPage;
    textExerciseExampleSubmissions: TextExerciseExampleSubmissionsPage;
    textExerciseExampleSubmissionCreation: TextExerciseExampleSubmissionCreationPage;
    textExerciseFeedback: TextExerciseFeedbackPage;
    exerciseResult: ExerciseResultPage;
    exerciseTeams: ExerciseTeamsPage;
};

export type ArtemisInfrastructure = {
    virtualAuthenticator: CDPSession;
};

export type ArtemisRequests = {
    accountManagementAPIRequests: AccountManagementAPIRequests;
    courseManagementAPIRequests: CourseManagementAPIRequests;
    userManagementAPIRequests: UserManagementAPIRequests;
    exerciseAPIRequests: ExerciseAPIRequests;
    examAPIRequests: ExamAPIRequests;
    communicationAPIRequests: CommunicationAPIRequests;
};

/**
 * Custom test object extended to use Artemis related fixtures.
 */
export const test = base.extend<ArtemisPageObjects & ArtemisCommands & ArtemisRequests & ArtemisInfrastructure>({
    /**
     * Wrap `page.goto` with a lightweight Angular-render check. Under heavy parallel
     * multi-node load the SPA occasionally fails to bootstrap the route component on
     * the first navigation — only the static app shell + footer render, leaving every
     * subsequent locator call to time out against a blank page. The wrapper:
     *   - waits up to 1s for the navbar's `#account-menu` (the universal post-bootstrap
     *     indicator on routes that include the navbar);
     *   - if not attached AND the URL is on a navbar-bearing route, reloads once with a
     *     10s grace window for a fresh chunk fetch to recover;
     *   - if the URL is on a known no-navbar route (exam participation, quiz live,
     *     problem-statement standalone, LTI), returns immediately so those tests pay no
     *     overhead.
     * Net cost on healthy pages: a single sub-100ms locator check. Net benefit on broken
     * pages: ~10s reload that turns a deterministic test failure into a passing test.
     */
    page: async ({ page }, use) => {
        // Unauthenticated routes (login / account / reset) do not render `#account-menu`
        // (the navbar template gates it on `isAuthenticated()`), so the render-check would
        // always go through the reload+10s grace path on those routes and burn budget for
        // nothing. Short-circuit them via a path prefix.
        const isUnauthenticatedRoute = (url: string) => /\/(sign-in|account|reset|register|forget-password)(\/|\?|$)/.test(url);

        const originalGoto = page.goto.bind(page);
        page.goto = async (url, options) => {
            const response = await originalGoto(url, options);
            try {
                const currentUrl = page.url();
                if (Commands.isNoNavbarRoute(currentUrl) || isUnauthenticatedRoute(currentUrl)) {
                    return response;
                }
                const attached = await page
                    .locator('#account-menu')
                    .waitFor({ state: 'attached', timeout: 1_000 })
                    .then(() => true)
                    .catch(() => false);
                if (!attached) {
                    // Before reloading, re-check the URL — Angular's auth guard may have
                    // redirected to /sign-in (or another unauthenticated route) during the
                    // 1s wait, in which case reloading would disrupt that redirect and pin
                    // the page back to a route the user cannot access. The reload-recovery
                    // is for "Angular lazy chunk failed to bootstrap", not "user is
                    // unauthenticated and Angular redirected them out".
                    const urlAfterWait = page.url();
                    if (Commands.isNoNavbarRoute(urlAfterWait) || isUnauthenticatedRoute(urlAfterWait)) {
                        return response;
                    }
                    await page.reload();
                    await page.waitForLoadState('load');
                    await page
                        .locator('#account-menu')
                        .waitFor({ state: 'attached', timeout: 10_000 })
                        .catch(() => undefined);
                }
                // Detect & recover from `/courses` lazy-chunk fall-back drift. The navbar
                // attaches fine on /courses too, so the attached-check above does not catch
                // this; we re-issue the original goto so the caller actually lands on the
                // URL they asked for. Reuses the single drift heuristic from `Commands`.
                if (typeof url === 'string' && Commands.driftedToCoursesFallback(url, page.url())) {
                    await originalGoto(url, options);
                }
            } catch {
                // Never let the render-check throw — it must be invisible to tests
                // whose post-goto assertions are perfectly capable of surfacing their own
                // errors.
            }
            return response;
        };
        await use(page);
    },
    loginPage: async ({ page }, use) => {
        await use(new LoginPage(page));
    },
    login: async ({ page }, use) => {
        await use(async (credentials: UserCredentials, url?: string) => {
            await Commands.login(page, credentials, url);
        });
    },
    toggleSidebar: async ({ page }, use) => {
        await use(async () => {
            await Commands.toggleSidebar(page);
        });
    },
    waitForExerciseBuildToFinish: async ({ page, exerciseAPIRequests }, use) => {
        await use(async (exerciseId: number, interval?, timeout?, minResults?) => {
            await Commands.waitForExerciseBuildToFinish(page, exerciseAPIRequests, exerciseId, interval, timeout, minResults);
        });
    },
    waitForParticipationBuildToFinish: async ({ exerciseAPIRequests }, use) => {
        await use(async (participationId: number, interval?, timeout?) => {
            return await Commands.waitForParticipationBuildToFinish(exerciseAPIRequests, participationId, interval, timeout);
        });
    },
    navigationBar: async ({ page }, use) => {
        await use(new NavigationBar(page));
    },
    courseAssessment: async ({ page }, use) => {
        await use(new CourseAssessmentDashboardPage(page));
    },
    examAssessment: async ({ page }, use) => {
        await use(new ExamAssessmentPage(page));
    },
    exerciseAssessment: async ({ page }, use) => {
        await use(new ExerciseAssessmentDashboardPage(page));
    },
    fileUploadExerciseAssessment: async ({ page }, use) => {
        await use(new FileUploadExerciseAssessmentPage(page));
    },
    modelingExerciseAssessment: async ({ page }, use) => {
        await use(new ModelingExerciseAssessmentEditor(page));
    },
    programmingExerciseAssessment: async ({ page }, use) => {
        await use(new ProgrammingExerciseAssessmentPage(page));
    },
    studentAssessment: async ({ page }, use) => {
        await use(new StudentAssessmentPage(page));
    },
    textExerciseAssessment: async ({ page }, use) => {
        await use(new TextExerciseAssessmentPage(page));
    },
    courseManagement: async ({ page }, use) => {
        await use(new CourseManagementPage(page));
    },
    competencyManagement: async ({ page }, use) => {
        await use(new CompetencyManagementPage(page));
    },
    courseManagementExercises: async ({ page }, use) => {
        await use(new CourseManagementExercisesPage(page));
    },
    courseCreation: async ({ page }, use) => {
        await use(new CourseCreationPage(page));
    },
    courseList: async ({ page }, use) => {
        await use(new CoursesPage(page));
    },
    courseOverview: async ({ page }, use) => {
        await use(new CourseOverviewPage(page));
    },
    courseMessages: async ({ page }, use) => {
        await use(new CourseMessagesPage(page));
    },
    courseCommunication: async ({ page }, use) => {
        await use(new CourseCommunicationPage(page));
    },
    courseOnboarding: async ({ page }, use) => {
        await use(new CourseOnboardingPage(page));
    },
    lectureManagement: async ({ page }, use) => {
        await use(new LectureManagementPage(page));
    },
    lectureCreation: async ({ page }, use) => {
        await use(new LectureCreationPage(page));
    },
    examCreation: async ({ page }, use) => {
        await use(new ExamCreationPage(page));
    },
    examDetails: async ({ page }, use) => {
        await use(new ExamDetailsPage(page));
    },
    examExerciseGroupCreation: async ({ page, examAPIRequests, exerciseAPIRequests }, use) => {
        await use(new ExamExerciseGroupCreationPage(page, examAPIRequests, exerciseAPIRequests));
    },
    examExerciseGroups: async ({ page }, use) => {
        await use(new ExamExerciseGroupsPage(page));
    },
    examGrading: async ({ page }, use) => {
        await use(new ExamGradingPage(page));
    },
    examManagement: async ({ page }, use) => {
        await use(new ExamManagementPage(page));
    },
    examNavigation: async ({ page }, use) => {
        await use(new ExamNavigationBar(page));
    },
    examParticipation: async ({ page, examNavigation, examStartEnd, modelingExerciseEditor, programmingExerciseEditor, quizExerciseMultipleChoice, textExerciseEditor }, use) => {
        await use(new ExamParticipationPage(examNavigation, examStartEnd, modelingExerciseEditor, programmingExerciseEditor, quizExerciseMultipleChoice, textExerciseEditor, page));
    },
    examParticipationActions: async ({ page }, use) => {
        await use(new ExamParticipationActions(page));
    },
    examResultsPage: async ({ page }, use) => {
        await use(new ExamResultsPage(page));
    },
    examScores: async ({ page }, use) => {
        await use(new ExamScoresPage(page));
    },
    examStartEnd: async ({ page }, use) => {
        await use(new ExamStartEndPage(page));
    },

    examTestRun: async ({ page, examStartEnd }, use) => {
        await use(new ExamTestRunPage(page, examStartEnd));
    },
    modalDialog: async ({ page }, use) => {
        await use(new ModalDialogBox(page));
    },
    studentExamManagement: async ({ page }, use) => {
        await use(new StudentExamManagementPage(page));
    },
    fileUploadExerciseCreation: async ({ page }, use) => {
        await use(new FileUploadExerciseCreationPage(page));
    },
    fileUploadExerciseEditor: async ({ page }, use) => {
        await use(new FileUploadEditorPage(page));
    },
    fileUploadExerciseFeedback: async ({ page }, use) => {
        await use(new FileUploadExerciseFeedbackPage(page));
    },
    modelingExerciseCreation: async ({ page }, use) => {
        await use(new ModelingExerciseCreationPage(page));
    },
    modelingExerciseEditor: async ({ page }, use) => {
        await use(new ModelingEditor(page));
    },
    modelingExerciseFeedback: async ({ page }, use) => {
        await use(new ModelingExerciseFeedbackPage(page));
    },
    programmingExerciseCreation: async ({ page }, use) => {
        await use(new ProgrammingExerciseCreationPage(page));
    },
    programmingExerciseEditor: async ({ page }, use) => {
        await use(new OnlineEditorPage(page));
    },
    programmingExerciseFeedback: async ({ page }, use) => {
        await use(new ProgrammingExerciseFeedbackPage(page));
    },
    programmingExerciseOverview: async ({ page, courseOverview }, use) => {
        await use(new ProgrammingExerciseOverviewPage(page, courseOverview));
    },
    programmingExerciseParticipations: async ({ page }, use) => {
        await use(new ProgrammingExerciseParticipationsPage(page));
    },
    programmingExerciseRepository: async ({ page }, use) => {
        await use(new RepositoryPage(page));
    },
    programmingExercisesScaConfig: async ({ page }, use) => {
        await use(new CodeAnalysisGradingPage(page));
    },
    programmingExerciseScaFeedback: async ({ page }, use) => {
        await use(new ScaFeedbackModal(page));
    },
    programmingExerciseSubmissions: async ({ page }, use) => {
        await use(new ProgrammingExerciseSubmissionsPage(page));
    },
    quizExerciseCreation: async ({ page }, use) => {
        await use(new QuizExerciseCreationPage(page));
    },
    quizExerciseDragAndDropQuiz: async ({ page }, use) => {
        await use(new DragAndDropQuiz(page));
    },
    quizExerciseMultipleChoice: async ({ page }, use) => {
        await use(new MultipleChoiceQuiz(page));
    },
    quizExerciseOverview: async ({ page }, use) => {
        await use(new QuizExerciseOverviewPage(page));
    },
    quizExerciseParticipation: async ({ page }, use) => {
        await use(new QuizExerciseParticipationPage(page));
    },
    quizExerciseShortAnswerQuiz: async ({ page }, use) => {
        await use(new ShortAnswerQuiz(page));
    },
    textExerciseCreation: async ({ page }, use) => {
        await use(new TextExerciseCreationPage(page));
    },
    textExerciseEditor: async ({ page }, use) => {
        await use(new TextEditorPage(page));
    },
    textExerciseExampleSubmissions: async ({ page }, use) => {
        await use(new TextExerciseExampleSubmissionsPage(page));
    },
    textExerciseExampleSubmissionCreation: async ({ page }, use) => {
        await use(new TextExerciseExampleSubmissionCreationPage(page));
    },
    textExerciseFeedback: async ({ page }, use) => {
        await use(new TextExerciseFeedbackPage(page));
    },
    exerciseResult: async ({ page }, use) => {
        await use(new ExerciseResultPage(page));
    },
    exerciseTeams: async ({ page }, use) => {
        await use(new ExerciseTeamsPage(page));
    },
    accountManagementAPIRequests: async ({ page }, use) => {
        await use(new AccountManagementAPIRequests(page));
    },
    courseManagementAPIRequests: async ({ page }, use) => {
        await use(new CourseManagementAPIRequests(page));
    },
    userManagementAPIRequests: async ({ page }, use) => {
        await use(new UserManagementAPIRequests(page));
    },
    exerciseAPIRequests: async ({ page }, use) => {
        await use(new ExerciseAPIRequests(page));
    },
    examAPIRequests: async ({ page }, use) => {
        await use(new ExamAPIRequests(page));
    },
    communicationAPIRequests: async ({ page }, use) => {
        await use(new CommunicationAPIRequests(page));
    },
    virtualAuthenticator: async ({ virtualAuthenticator }, use) => {
        await use(virtualAuthenticator);
    },
});
