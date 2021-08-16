import { TextEditor } from './TextEditor';
import { ExamNavigationBar } from './exam/ExamNavigationBar';
import { CourseOverviewPage } from './course/CourseOverviewPage';
import { CoursesPage } from './course/CoursesPage';
import { ProgrammingExerciseCreationPage } from './ProgrammingExerciseCreationPage';
import { ExamManagementPage } from './exam/ExamManagementPage';
import { ExamCreationPage } from './exam/ExamCreationPage';
import { CourseManagementPage } from './course/CourseManagementPage';
import { NavigationBar } from './NavigationBar';
import { OnlineEditorPage } from './OnlineEditorPage';
import { CreateModelingExercisePage } from './CreateModelingExercisePage';
import { ModelingExerciseExampleSubmissionPage } from './ModelingExerciseExampleSubmissionPage';
import { ModelingEditor } from './ModelingEditor';
import { ExamStartEndPage } from './exam/ExamStartEndPage';

/**
 * A class which encapsulates all pageobjects, which can be used to automate the Artemis UI.
 */
export class ArtemisPageobjects {
    courseManagement = new CourseManagementPage();
    courses = new CoursesPage();
    courseOverview = new CourseOverviewPage();
    navigationBar = new NavigationBar();
    onlineEditor = new OnlineEditorPage();
    examCreation = new ExamCreationPage();
    examManagement = new ExamManagementPage();
    examStartEnd = new ExamStartEndPage();
    examNavigationBar = new ExamNavigationBar();
    programmingExerciseCreation = new ProgrammingExerciseCreationPage();
    createModelingExercise = new CreateModelingExercisePage();
    modelingExerciseExampleSubmission = new ModelingExerciseExampleSubmissionPage();
    modelingEditor = new ModelingEditor();
    textEditor = new TextEditor();
}
