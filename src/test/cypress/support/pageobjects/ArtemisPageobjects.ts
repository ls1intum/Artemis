import { CourseManagementPage } from './CourseManagementPage';
import { NavigationBar } from './NavigationBar';
import { OnlineEditorPage } from './OnlineEditorPage';

/**
 * A class which encapsulates all pageobjects, which can be used to automate the Artemis UI.
 */
export class ArtemisPageobjects {
    courseManagement = new CourseManagementPage();
    navigationBar = new NavigationBar();
    onlineEditor = new OnlineEditorPage();
}
