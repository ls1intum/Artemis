import { test as base } from '@playwright/test';
import { LoginPage } from './pageobjects/LoginPage';
import { UserCredentials } from './users';
import { NavigationBar } from './pageobjects/NavigationBar';
import { CourseManagementAPIRequests } from './requests/CourseManagementAPIRequests';
import { CourseManagementPage } from './pageobjects/course/CourseManagementPage';
import { CourseCreationPage } from './pageobjects/course/CourseCreationPage';
import { UserManagementAPIRequests } from './requests/UserManagementAPIRequests';
import { Commands } from './commands';
import { ExerciseAPIRequests } from './requests/ExerciseAPIRequests';
import { CourseOverviewPage } from './pageobjects/course/CourseOverviewPage';
import { CourseMessagesPage } from './pageobjects/course/CourseMessagesPage';
import { ExamAPIRequests } from './requests/ExamAPIRequests';
import { CommunicationAPIRequests } from './requests/CommunicationAPIRequests';
import { CourseCommunicationPage } from './pageobjects/course/CourseCommunicationPage';
import { LectureManagementPage } from './pageobjects/lecture/LectureManagementPage';
import { LectureCreationPage } from './pageobjects/lecture/LectureCreationPage';
import { ExamCreationPage } from './pageobjects/exam/ExamCreationPage';
import { ExamDetailsPage } from './pageobjects/exam/ExamDetailsPage';
import { ExamManagementPage } from './pageobjects/exam/ExamManagementPage';
import { ExamExerciseGroupCreationPage } from './pageobjects/exam/ExamExerciseGroupCreationPage';
import { ExamNavigationBar } from './pageobjects/exam/ExamNavigationBar';
import { ExamParticipation } from './pageobjects/exam/ExamParticipation';
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
import { CreateModelingExercisePage } from './pageobjects/exercises/modeling/CreateModelingExercisePage';
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
import { ExamGradingPage } from './pageobjects/exam/ExamGradingPage';
import { ExamScoresPage } from './pageobjects/exam/ExamScoresPage';

/*
 * Define custom types for fixtures
 */
export type ArtemisCommands = {
    login: (credentials: UserCredentials, url?: string) => Promise<void>;
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
    courseManagementExercises: CourseManagementExercisesPage;
    courseCreation: CourseCreationPage;
    courseList: CoursesPage;
    courseOverview: CourseOverviewPage;
    courseMessages: CourseMessagesPage;
    courseCommunication: CourseCommunicationPage;
    lectureManagement: LectureManagementPage;
    lectureCreation: LectureCreationPage;
    examCreation: ExamCreationPage;
    examDetails: ExamDetailsPage;
    examExerciseGroupCreation: ExamExerciseGroupCreationPage;
    examExerciseGroups: ExamExerciseGroupsPage;
    examGrading: ExamGradingPage;
    examNavigation: ExamNavigationBar;
    examManagement: ExamManagementPage;
    examParticipation: ExamParticipation;
    examScores: ExamScoresPage;
    examStartEnd: ExamStartEndPage;
    examTestRun: ExamTestRunPage;
    studentExamManagement: StudentExamManagementPage;
    fileUploadExerciseCreation: FileUploadExerciseCreationPage;
    fileUploadExerciseEditor: FileUploadEditorPage;
    fileUploadExerciseFeedback: FileUploadExerciseFeedbackPage;
    modelingExerciseCreation: CreateModelingExercisePage;
    modelingExerciseEditor: ModelingEditor;
    modelingExerciseFeedback: ModelingExerciseFeedbackPage;
    programmingExerciseCreation: ProgrammingExerciseCreationPage;
    programmingExerciseEditor: OnlineEditorPage;
    programmingExerciseFeedback: ProgrammingExerciseFeedbackPage;
    programmingExercisesScaConfig: CodeAnalysisGradingPage;
    programmingExerciseScaFeedback: ScaFeedbackModal;
    quizExerciseCreation: QuizExerciseCreationPage;
    quizExerciseDragAndDropQuiz: DragAndDropQuiz;
    quizExerciseMultipleChoice: MultipleChoiceQuiz;
    quizExerciseShortAnswerQuiz: ShortAnswerQuiz;
    textExerciseCreation: TextExerciseCreationPage;
    textExerciseEditor: TextEditorPage;
    textExerciseExampleSubmissions: TextExerciseExampleSubmissionsPage;
    textExerciseExampleSubmissionCreation: TextExerciseExampleSubmissionCreationPage;
    textExerciseFeedback: TextExerciseFeedbackPage;
    exerciseResult: ExerciseResultPage;
};

export type ArtemisRequests = {
    courseManagementAPIRequests: CourseManagementAPIRequests;
    userManagementAPIRequests: UserManagementAPIRequests;
    exerciseAPIRequests: ExerciseAPIRequests;
    examAPIRequests: ExamAPIRequests;
    communicationAPIRequests: CommunicationAPIRequests;
};

/**
 * Custom test object extended to use Artemis related fixtures.
 */
export const test = base.extend<ArtemisPageObjects & ArtemisCommands & ArtemisRequests>({
    loginPage: async ({ page }, use) => {
        await use(new LoginPage(page));
    },
    login: async ({ page }, use) => {
        await use(async (credentials: UserCredentials, url?: string) => {
            await Commands.login(page, credentials, url);
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
    examParticipation: async (
        { page, courseList, courseOverview, examNavigation, examStartEnd, modelingExerciseEditor, programmingExerciseEditor, quizExerciseMultipleChoice, textExerciseEditor },
        use,
    ) => {
        await use(
            new ExamParticipation(
                courseList,
                courseOverview,
                examNavigation,
                examStartEnd,
                modelingExerciseEditor,
                programmingExerciseEditor,
                quizExerciseMultipleChoice,
                textExerciseEditor,
                page,
            ),
        );
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
        await use(new CreateModelingExercisePage(page));
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
    programmingExerciseEditor: async ({ page, courseList, courseOverview }, use) => {
        await use(new OnlineEditorPage(page, courseList, courseOverview));
    },
    programmingExerciseFeedback: async ({ page }, use) => {
        await use(new ProgrammingExerciseFeedbackPage(page));
    },
    programmingExercisesScaConfig: async ({ page }, use) => {
        await use(new CodeAnalysisGradingPage(page));
    },
    programmingExerciseScaFeedback: async ({ page }, use) => {
        await use(new ScaFeedbackModal(page));
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
});
