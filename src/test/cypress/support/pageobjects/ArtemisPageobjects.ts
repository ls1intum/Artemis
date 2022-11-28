import { ExamDetailsPage } from './exam/ExamDetailsPage';
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
import { ModelingExerciseFeedbackPage } from './exercises/modeling/ModelingExerciseFeedbackPage';
import { LectureManagementPage } from './lecture/LectureManagementPage';
import { LectureCreationPage } from './lecture/LectureCreationPage';
import { StudentExamManagementPage } from './exam/StudentExamManagementPage';
import CourseExercisePage from './course/CourseExercisePage';

/**
 * A class which encapsulates all pageobjects, which can be used to automate the Artemis UI.
 */
export class ArtemisPageobjects {
    login = new LoginPage();
    navigationBar = new NavigationBar();
    course = {
        management: new CourseManagementPage(),
        managementExercises: new CourseManagementExercisesPage(),
        list: new CoursesPage(),
        overview: new CourseOverviewPage(),
        exercise: new CourseExercisePage(),
    };
    exam = {
        details: new ExamDetailsPage(),
        creation: new ExamCreationPage(),
        management: new ExamManagementPage(),
        startEnd: new ExamStartEndPage(),
        navigationBar: new ExamNavigationBar(),
        exerciseGroups: new ExamExerciseGroupsPage(),
        exerciseGroupCreation: new ExamExerciseGroupCreationPage(),
        studentExamManagement: new StudentExamManagementPage(),
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
    };
    assessment = {
        exam: new ExamAssessmentPage(),
        course: new CourseAssessmentDashboardPage(),
        exercise: new ExerciseAssessmentDashboardPage(),
        text: new TextExerciseAssessmentPage(),
        programming: new ProgrammingExerciseAssessmentPage(),
        modeling: new ModelingExerciseAssessmentEditor(),
    };
    lecture = {
        management: new LectureManagementPage(),
        creation: new LectureCreationPage(),
    };
}
