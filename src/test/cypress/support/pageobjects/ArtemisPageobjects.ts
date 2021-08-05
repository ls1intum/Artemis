import { ExamManagementPage } from './ExamManagementPage';
import { ExamCreationPage } from './ExamCreationPage';
import { CourseManagementPage } from './CourseManagementPage';
import { NavigationBar } from './NavigationBar';
import { OnlineEditorPage } from './OnlineEditorPage';
import { CreateModelingExercisePage } from './CreateModelingExercisePage';
import { ModelingExerciseExampleSubmissionPage } from './ModelingExerciseExampleSubmissionPage';
import { ModelingEditor } from './ModelingEditor';

/**
 * A class which encapsulates all pageobjects, which can be used to automate the Artemis UI.
 */
export class ArtemisPageobjects {
    courseManagement = new CourseManagementPage();
    navigationBar = new NavigationBar();
    onlineEditor = new OnlineEditorPage();
    createModelingExercise = new CreateModelingExercisePage();
    modelingExerciseExampleSubmission = new ModelingExerciseExampleSubmissionPage();
    modelingEditor = new ModelingEditor();
    examCreation = new ExamCreationPage();
    examManagement = new ExamManagementPage();
}
