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
    modelingExerciseAssessment: ModelingExerciseAssessmentEditor;
    studentAssessment: StudentAssessmentPage;
    courseManagement: CourseManagementPage;
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
    examNavigation: ExamNavigationBar;
    examManagement: ExamManagementPage;
    examParticipation: ExamParticipation;
    examStartEnd: ExamStartEndPage;
    examTestRun: ExamTestRunPage;
    studentExamManagement: StudentExamManagementPage;
    modelingExerciseCreation: CreateModelingExercisePage;
    modelingExerciseEditor: ModelingEditor;
    programmingExerciseCreation: ProgrammingExerciseCreationPage;
    programmingExerciseEditor: OnlineEditorPage;
    quizExerciseCreation: QuizExerciseCreationPage;
    quizExerciseMultipleChoice: MultipleChoiceQuiz;
    textExerciseEditor: TextEditorPage;
    textExerciseCreation: TextExerciseCreationPage;
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
    modelingExerciseAssessment: async ({ page }, use) => {
        await use(new ModelingExerciseAssessmentEditor(page));
    },
    studentAssessment: async ({ page }, use) => {
        await use(new StudentAssessmentPage(page));
    },
    courseManagement: async ({ page }, use) => {
        await use(new CourseManagementPage(page));
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
    examStartEnd: async ({ page }, use) => {
        await use(new ExamStartEndPage(page));
    },
    examTestRun: async ({ page, examStartEnd }, use) => {
        await use(new ExamTestRunPage(page, examStartEnd));
    },
    studentExamManagement: async ({ page }, use) => {
        await use(new StudentExamManagementPage(page));
    },
    modelingExerciseCreation: async ({ page }, use) => {
        await use(new CreateModelingExercisePage(page));
    },
    modelingExerciseEditor: async ({ page }, use) => {
        await use(new ModelingEditor(page));
    },
    programmingExerciseCreation: async ({ page }, use) => {
        await use(new ProgrammingExerciseCreationPage(page));
    },
    programmingExerciseEditor: async ({ page, courseList, courseOverview }, use) => {
        await use(new OnlineEditorPage(page, courseList, courseOverview));
    },
    quizExerciseCreation: async ({ page }, use) => {
        await use(new QuizExerciseCreationPage(page));
    },
    quizExerciseMultipleChoice: async ({ page }, use) => {
        await use(new MultipleChoiceQuiz(page));
    },
    textExerciseEditor: async ({ page }, use) => {
        await use(new TextEditorPage(page));
    },
    textExerciseCreation: async ({ page }, use) => {
        await use(new TextExerciseCreationPage(page));
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
