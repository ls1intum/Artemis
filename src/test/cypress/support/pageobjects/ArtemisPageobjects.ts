import { LoginPage } from './LoginPage';
import { NavigationBar } from './NavigationBar';
import { CourseAssessmentDashboardPage } from './assessment/CourseAssessmentDashboardPage';
import { ExamAssessmentPage } from './assessment/ExamAssessmentPage';
import { ExerciseAssessmentDashboardPage } from './assessment/ExerciseAssessmentDashboardPage';
import { FileUploadExerciseAssessmentPage } from './assessment/FileUploadExerciseAssessmentPage';
import { ModelingExerciseAssessmentEditor } from './assessment/ModelingExerciseAssessmentEditor';
import { ProgrammingExerciseAssessmentPage } from './assessment/ProgrammingExerciseAssessmentPage';
import { StudentAssessmentPage } from './assessment/StudentAssessmentPage';
import { TextExerciseAssessmentPage } from './assessment/TextExerciseAssessmentPage';
import { CourseCommunicationPage } from './course/CourseCommunication';
import { CourseCreationPage } from './course/CourseCreationPage';
import { CourseManagementExercisesPage } from './course/CourseManagementExercisesPage';
import { CourseManagementPage } from './course/CourseManagementPage';
import { CourseMessagesPage } from './course/CourseMessages';
import { CourseOverviewPage } from './course/CourseOverviewPage';
import { CoursesPage } from './course/CoursesPage';
import { ExamCreationPage } from './exam/ExamCreationPage';
import { ExamDetailsPage } from './exam/ExamDetailsPage';
import { ExamExerciseGroupCreationPage } from './exam/ExamExerciseGroupCreationPage';
import { ExamExerciseGroupsPage } from './exam/ExamExerciseGroupsPage';
import { ExamManagementPage } from './exam/ExamManagementPage';
import { ExamNavigationBar } from './exam/ExamNavigationBar';
import { ExamParticipation } from './exam/ExamParticipation';
import { ExamStartEndPage } from './exam/ExamStartEndPage';
import { ExamTestRunPage } from './exam/ExamTestRunPage';
import { StudentExamManagementPage } from './exam/StudentExamManagementPage';
import { ExerciseResultPage } from './exercises/ExerciseResultPage';
import { FileUploadEditorPage } from './exercises/file-upload/FileUploadEditorPage';
import { FileUploadExerciseCreationPage } from './exercises/file-upload/FileUploadExerciseCreationPage';
import { FileUploadExerciseFeedbackPage } from './exercises/file-upload/FileUploadExerciseFeedbackPage';
import { CreateModelingExercisePage } from './exercises/modeling/CreateModelingExercisePage';
import { ModelingEditor } from './exercises/modeling/ModelingEditor';
import { ModelingExerciseFeedbackPage } from './exercises/modeling/ModelingExerciseFeedbackPage';
import { CodeAnalysisGradingPage } from './exercises/programming/CodeAnalysisGradingPage';
import { OnlineEditorPage } from './exercises/programming/OnlineEditorPage';
import { ProgrammingExerciseCreationPage } from './exercises/programming/ProgrammingExerciseCreationPage';
import { ProgrammingExerciseFeedbackPage } from './exercises/programming/ProgrammingExerciseFeedbackPage';
import { ScaFeedbackModal } from './exercises/programming/ScaFeedbackModal';
import { DragAndDropQuiz } from './exercises/quiz/DragAndDropQuiz';
import { MultipleChoiceQuiz } from './exercises/quiz/MultipleChoiceQuiz';
import { QuizExerciseCreationPage } from './exercises/quiz/QuizExerciseCreationPage';
import { ShortAnswerQuiz } from './exercises/quiz/ShortAnswerQuiz';
import { TextEditorPage } from './exercises/text/TextEditorPage';
import { TextExerciseCreationPage } from './exercises/text/TextExerciseCreationPage';
import { TextExerciseExampleSubmissionCreationPage } from './exercises/text/TextExerciseExampleSubmissionCreationPage';
import { TextExerciseExampleSubmissionsPage } from './exercises/text/TextExerciseExampleSubmissionsPage';
import { TextExerciseFeedbackPage } from './exercises/text/TextExerciseFeedbackPage';
import { LectureCreationPage } from './lecture/LectureCreationPage';
import { LectureManagementPage } from './lecture/LectureManagementPage';

/**
 * A class which encapsulates all pageobjects, which can be used to automate the Artemis UI.
 */
export class ArtemisPageobjects {
    login = new LoginPage();
    navigationBar = new NavigationBar();
    course = {
        creation: new CourseCreationPage(),
        management: new CourseManagementPage(),
        managementExercises: new CourseManagementExercisesPage(),
        list: new CoursesPage(),
        overview: new CourseOverviewPage(),
        communication: new CourseCommunicationPage(),
        messages: new CourseMessagesPage(),
    };
    exam = {
        details: new ExamDetailsPage(),
        creation: new ExamCreationPage(),
        management: new ExamManagementPage(),
        participation: new ExamParticipation(),
        startEnd: new ExamStartEndPage(),
        navigationBar: new ExamNavigationBar(),
        exerciseGroups: new ExamExerciseGroupsPage(),
        exerciseGroupCreation: new ExamExerciseGroupCreationPage(),
        studentExamManagement: new StudentExamManagementPage(),
        testRun: new ExamTestRunPage(),
    };
    exercise = {
        result: new ExerciseResultPage(),
        programming: {
            editor: new OnlineEditorPage(),
            creation: new ProgrammingExerciseCreationPage(),
            feedback: new ProgrammingExerciseFeedbackPage(),
            scaConfiguration: new CodeAnalysisGradingPage(),
            scaFeedback: new ScaFeedbackModal(),
        },
        text: {
            creation: new TextExerciseCreationPage(),
            exampleSubmissions: new TextExerciseExampleSubmissionsPage(),
            exampleSubmissionCreation: new TextExerciseExampleSubmissionCreationPage(),
            editor: new TextEditorPage(),
            feedback: new TextExerciseFeedbackPage(),
        },
        modeling: {
            creation: new CreateModelingExercisePage(),
            editor: new ModelingEditor(),
            feedback: new ModelingExerciseFeedbackPage(),
        },
        quiz: {
            creation: new QuizExerciseCreationPage(),
            multipleChoice: new MultipleChoiceQuiz(),
            shortAnswer: new ShortAnswerQuiz(),
            dragAndDrop: new DragAndDropQuiz(),
        },
        fileUpload: {
            creation: new FileUploadExerciseCreationPage(),
            editor: new FileUploadEditorPage(),
            feedback: new FileUploadExerciseFeedbackPage(),
        },
    };
    assessment = {
        exam: new ExamAssessmentPage(),
        course: new CourseAssessmentDashboardPage(),
        exercise: new ExerciseAssessmentDashboardPage(),
        text: new TextExerciseAssessmentPage(),
        programming: new ProgrammingExerciseAssessmentPage(),
        modeling: new ModelingExerciseAssessmentEditor(),
        fileUpload: new FileUploadExerciseAssessmentPage(),
        student: new StudentAssessmentPage(),
    };
    lecture = {
        management: new LectureManagementPage(),
        creation: new LectureCreationPage(),
    };
}
