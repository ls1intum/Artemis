import { ExerciseResultPage } from './exercises/ExerciseResultPage';
import { ExerciseAssessmentDashboardPage } from './assessment/ExerciseAssessmentDashboardPage';
import { CourseAssessmentDashboardPage } from './assessment/CourseAssessmentDashboardPage';
import { TextEditorPage } from './exercises/text/TextEditorPage';
import { CourseManagementExercisesPage } from './CourseManagementExercisesPage';
import { ProgrammingExerciseCreationPage } from './ProgrammingExerciseCreationPage';
import { ExamManagementPage } from './ExamManagementPage';
import { ExamCreationPage } from './ExamCreationPage';
import { CourseManagementPage } from './CourseManagementPage';
import { NavigationBar } from './NavigationBar';
import { OnlineEditorPage } from './OnlineEditorPage';
import { CreateModelingExercisePage } from './CreateModelingExercisePage';
import { ModelingExerciseAssessmentEditor } from './ModelingExerciseAssessmentEditor';
import { ModelingEditor } from './ModelingEditor';
import { ExamStartEndPage } from './ExamStartEndPage';
import { TextExerciseCreationPage } from './exercises/text/TextExerciseCreationPage';
import { TextExerciseExampleSubmissionsPage } from './exercises/text/TextExerciseExampleSubmissionsPage';
import { TextExerciseExampleSubmissionCreationPage } from './exercises/text/TextExerciseExampleSubmissionCreationPage';
import { TextExerciseAssessmentPage } from './assessment/TextExerciseAssessmentPage';
import { TextExerciseFeedbackPage } from './exercises/text/TextExerciseFeedbackPage';

/**
 * A class which encapsulates all pageobjects, which can be used to automate the Artemis UI.
 */
export class ArtemisPageobjects {
    courseManagement = new CourseManagementPage();
    courseManagementExercises = new CourseManagementExercisesPage();
    navigationBar = new NavigationBar();
    onlineEditor = new OnlineEditorPage();
    examCreation = new ExamCreationPage();
    examManagement = new ExamManagementPage();
    programmingExerciseCreation = new ProgrammingExerciseCreationPage();
    createModelingExercise = new CreateModelingExercisePage();
    modelingExerciseAssessmentEditor = new ModelingExerciseAssessmentEditor();
    modelingEditor = new ModelingEditor();
    examStartEnd = new ExamStartEndPage();
    textExercise = {
        creation: new TextExerciseCreationPage(),
        exampleSubmissions: new TextExerciseExampleSubmissionsPage(),
        exampleSubmissionCreation: new TextExerciseExampleSubmissionCreationPage(),
        editor: new TextEditorPage(),
        feedback: new TextExerciseFeedbackPage(),
    };
    assessment = {
        course: new CourseAssessmentDashboardPage(),
        exercise: new ExerciseAssessmentDashboardPage(),
        text: new TextExerciseAssessmentPage(),
    };
    exerciseResult = new ExerciseResultPage();
}
