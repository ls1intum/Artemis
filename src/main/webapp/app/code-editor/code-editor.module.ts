import { NgModule } from '@angular/core';
import { AlertService } from 'app/core/alert/alert.service';
import { RouterModule } from '@angular/router';
import { MomentModule } from 'ngx-moment';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { codeEditorRoute } from './code-editor.route';
import { CodeEditorBuildOutputComponent } from 'app/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorGridComponent } from 'app/code-editor/layout/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorAceComponent } from 'app/code-editor/ace/code-editor-ace.component';
import { CodeEditorFileBrowserDeleteComponent } from 'app/code-editor/file-browser/code-editor-file-browser-delete';
import { CodeEditorInstructionsComponent } from 'app/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorResolveConflictModalComponent } from 'app/code-editor/actions/code-editor-resolve-conflict-modal.component';
import { CodeEditorStudentContainerComponent } from 'app/code-editor/code-editor-student-container.component';
import { ExerciseHintStudentDialogComponent } from 'app/entities/exercise-hint/exercise-hint-student-dialog.component';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CodeEditorRepositoryIsLockedComponent } from 'app/code-editor/layout/code-editor-repository-is-locked.component';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status/programming-exercise-status.module';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorFileBrowserFileComponent } from 'app/code-editor/file-browser/code-editor-file-browser-file.component';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { ArtemisExerciseHintModule } from 'app/entities/exercise-hint/exercise-hint.module';
import { CodeEditorFileBrowserComponent } from 'app/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorStatusComponent } from 'app/code-editor/status/code-editor-status.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { CodeEditorInstructorIntellijContainerComponent } from 'app/code-editor/instructor/code-editor-instructor-intellij-container.component';
import { IntellijModule } from 'app/intellij/intellij.module';
import { CodeEditorInstructorContainerComponent } from 'app/code-editor/instructor/code-editor-instructor-container.component';

const ENTITY_STATES = [...codeEditorRoute];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        AceEditorModule,
        MomentModule,
        ArtemisSharedModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseActionsModule,
        TreeviewModule.forRoot(),
        ArtemisExerciseHintModule,
        FeatureToggleModule,
        IntellijModule,
    ],
    declarations: [
        CodeEditorGridComponent,
        CodeEditorRepositoryIsLockedComponent,
        CodeEditorInstructorContainerComponent,
        CodeEditorStudentContainerComponent,
        CodeEditorFileBrowserComponent,
        CodeEditorFileBrowserDeleteComponent,
        CodeEditorFileBrowserFileComponent,
        CodeEditorFileBrowserFolderComponent,
        CodeEditorFileBrowserCreateNodeComponent,
        CodeEditorAceComponent,
        CodeEditorBuildOutputComponent,
        CodeEditorInstructionsComponent,
        CodeEditorStatusComponent,
        CodeEditorActionsComponent,
        CodeEditorResolveConflictModalComponent,
        CodeEditorInstructorIntellijContainerComponent,
    ],
    exports: [CodeEditorInstructorContainerComponent, CodeEditorStudentContainerComponent],
    entryComponents: [
        CodeEditorInstructorIntellijContainerComponent,
        CodeEditorInstructorContainerComponent,
        CodeEditorStudentContainerComponent,
        CodeEditorFileBrowserDeleteComponent,
        ExerciseHintStudentDialogComponent,
        CodeEditorResolveConflictModalComponent,
    ],
    providers: [],
})
export class ArtemisCodeEditorModule {}
