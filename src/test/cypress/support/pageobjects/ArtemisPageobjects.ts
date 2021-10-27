import { LoginPage } from './LoginPage';
import { ExamExerciseGroupCreationPage } from './exam/ExamExerciseGroupCreationPage';
import { ExamExerciseGroupsPage } from './exam/ExamExerciseGroupsPage';
import { ProgrammingExerciseFeedbackPage } from './exercises/programming/ProgrammingExerciseFeedbackPage';
import { ProgrammingExerciseAssessmentPage } from './assessment/ProgrammingExerciseAssessmentPage';
import { ExerciseResultPage } from './exercises/ExerciseResultPage';
import { ExerciseAssessmentDashboardPage } from './assessment/ExerciseAssessmentDashboardPage';
import { CourseAssessmentDashboardPage } from './assessment/CourseAssessmentDashboardPage';
import { ScaFeedbackModal } from './exercises/programming/ScaFeedbackModal';
import { CodeAnalysisGradingPage } from './exercises/programming/CodeAnalysisGradingPage';
import { TextEditorPage } from './exercises/text/TextEditorPage';
import { ExamNavigationBar } from './exam/ExamNavigationBar';
import { CourseOverviewPage } from './course/CourseOverviewPage';
import { CoursesPage } from './course/CoursesPage';
import { CourseManagementExercisesPage } from './course/CourseManagementExercisesPage';
import { ProgrammingExerciseCreationPage } from './exercises/programming/ProgrammingExerciseCreationPage';
import { ExamManagementPage } from './exam/ExamManagementPage';
import { ExamCreationPage } from './exam/ExamCreationPage';
import { CourseManagementPage } from './course/CourseManagementPage';
import { NavigationBar } from './NavigationBar';
import { OnlineEditorPage } from './exercises/programming/OnlineEditorPage';
import { CreateModelingExercisePage } from './exercises/modeling/CreateModelingExercisePage';
import { ModelingExerciseAssessmentEditor } from './assessment/ModelingExerciseAssessmentEditor';
import { MultipleChoiceQuiz } from './exercises/quiz/MultipleChoiceQuiz';
import { ModelingEditor } from './exercises/modeling/ModelingEditor';
import { ShortAnswerQuiz } from './exercises/quiz/ShortAnswerQuiz';
import { DragAndDropQuiz } from './exercises/quiz/DragAndDropQuiz';
import { TextExerciseAssessmentPage } from './assessment/TextExerciseAssessmentPage';
import { TextExerciseFeedbackPage } from './exercises/text/TextExerciseFeedbackPage';
import { ExamStartEndPage } from './exam/ExamStartEndPage';
import { QuizExerciseCreationPage } from './exercises/quiz/QuizExerciseCreationPage';
import { TextExerciseCreationPage } from './exercises/text/TextExerciseCreationPage';
import { TextExerciseExampleSubmissionsPage } from './exercises/text/TextExerciseExampleSubmissionsPage';
import { TextExerciseExampleSubmissionCreationPage } from './exercises/text/TextExerciseExampleSubmissionCreationPage';
import { ExamAssessmentPage } from './assessment/ExamAssessmentPage';

/**
 * A class which encapsulates all pageobjects, which can be used to automate the Artemis UI.
 */
export class ArtemisPageobjects {
    login = new LoginPage();
    courseManagement = new CourseManagementPage();
    courses = new CoursesPage();
    courseOverview = new CourseOverviewPage();
    courseManagementExercises = new CourseManagementExercisesPage();
    navigationBar = new NavigationBar();
    examCreation = new ExamCreationPage();
    examManagement = new ExamManagementPage();
    examStartEnd = new ExamStartEndPage();
    examNavigationBar = new ExamNavigationBar();
    exerciseResult = new ExerciseResultPage();
    examExerciseGroups = new ExamExerciseGroupsPage();
    examExerciseGroupCreation = new ExamExerciseGroupCreationPage();
    programmingExercise = {
        editor: new OnlineEditorPage(),
        creation: new ProgrammingExerciseCreationPage(),
        feedback: new ProgrammingExerciseFeedbackPage(),
        scaConfiguration: new CodeAnalysisGradingPage(),
        scaFeedback: new ScaFeedbackModal(),
    };
    textExercise = {
        creation: new TextExerciseCreationPage(),
        exampleSubmissions: new TextExerciseExampleSubmissionsPage(),
        exampleSubmissionCreation: new TextExerciseExampleSubmissionCreationPage(),
        editor: new TextEditorPage(),
        feedback: new TextExerciseFeedbackPage(),
    };
    assessment = {
        exam: new ExamAssessmentPage(),
        course: new CourseAssessmentDashboardPage(),
        exercise: new ExerciseAssessmentDashboardPage(),
        text: new TextExerciseAssessmentPage(),
        programming: new ProgrammingExerciseAssessmentPage(),
    };
    modelingExercise = {
        creation: new CreateModelingExercisePage(),
        assessmentEditor: new ModelingExerciseAssessmentEditor(),
        editor: new ModelingEditor(),
    };
    quizExercise = {
        creation: new QuizExerciseCreationPage(),
        multipleChoice: new MultipleChoiceQuiz(),
        shortAnswer: new ShortAnswerQuiz(),
        dragAndDrop: new DragAndDropQuiz(),
    };
}
