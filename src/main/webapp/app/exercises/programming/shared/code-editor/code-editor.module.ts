import { NgModule } from '@angular/core';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserDeleteComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-delete';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorResolveConflictModalComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-resolve-conflict-modal.component';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CodeEditorRepositoryIsLockedComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { CodeEditorConfirmRefreshModalComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-confirm-refresh-modal.component';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { ArtemisProgrammingManualAssessmentModule } from 'app/exercises/programming/assess/programming-manual-assessment.module';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TreeviewModule } from 'app/exercises/programming/shared/code-editor/treeview/treeview.module';
import { CodeEditorHeaderComponent } from 'app/exercises/programming/shared/code-editor/header/code-editor-header.component';
import { CodeEditorFileBrowserBadgeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-badge.component';
import { MonacoEditorModule } from 'app/shared/monaco-editor/monaco-editor.module';
import { CodeEditorMonacoComponent } from 'app/exercises/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { RequestFeedbackButtonComponent } from 'app/overview/exercise-details/request-feedback-button/request-feedback-button.component';

@NgModule({
    imports: [
        NgbModule,
        ArtemisSharedModule,
        FeatureToggleModule,
        TreeviewModule.forRoot(),
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingManualAssessmentModule,
        MonacoEditorModule,
        ArtemisSharedComponentModule,
        RequestFeedbackButtonComponent,
    ],
    declarations: [
        CodeEditorGridComponent,
        CodeEditorRepositoryIsLockedComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorFileBrowserDeleteComponent,
        CodeEditorFileBrowserFileComponent,
        CodeEditorFileBrowserFolderComponent,
        CodeEditorFileBrowserBadgeComponent,
        CodeEditorFileBrowserCreateNodeComponent,
        CodeEditorBuildOutputComponent,
        CodeEditorInstructionsComponent,
        CodeEditorStatusComponent,
        CodeEditorActionsComponent,
        CodeEditorResolveConflictModalComponent,
        CodeEditorConfirmRefreshModalComponent,
        CodeEditorContainerComponent,
        CodeEditorHeaderComponent,
        CodeEditorMonacoComponent,
    ],
    exports: [
        CodeEditorGridComponent,
        CodeEditorRepositoryIsLockedComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorActionsComponent,
        CodeEditorInstructionsComponent,
        CodeEditorBuildOutputComponent,
        CodeEditorContainerComponent,
        CodeEditorHeaderComponent,
        CodeEditorMonacoComponent,
    ],
    providers: [],
})
export class ArtemisCodeEditorModule {}
