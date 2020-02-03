import { NgModule } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { RouterModule } from '@angular/router';
import { MomentModule } from 'ngx-moment';
import { AceEditorModule } from 'ng2-ace-editor';
import { TreeviewModule } from 'ngx-treeview';
import { codeEditorRoute } from './code-editor.route';
import { ArtemisResultModule } from 'app/entities/result';

import {
    CodeEditorAceComponent,
    CodeEditorActionsComponent,
    CodeEditorBuildLogService,
    CodeEditorBuildOutputComponent,
    CodeEditorConflictStateService,
    CodeEditorFileBrowserComponent,
    CodeEditorFileBrowserCreateNodeComponent,
    CodeEditorFileBrowserDeleteComponent,
    CodeEditorFileBrowserFileComponent,
    CodeEditorFileBrowserFolderComponent,
    CodeEditorFileService,
    CodeEditorGridComponent,
    CodeEditorGridService,
    CodeEditorInstructionsComponent,
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryIsLockedComponent,
    CodeEditorRepositoryService,
    CodeEditorResolveConflictModalComponent,
    CodeEditorSessionService,
    CodeEditorStatusComponent,
    CodeEditorStudentContainerComponent,
    CodeEditorSubmissionService,
    DomainService,
} from './';
import { ArtemisExerciseHintModule } from 'app/entities/exercise-hint/exercise-hint.module';
import { ExerciseHintStudentDialogComponent } from 'app/entities/exercise-hint';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/entities/programming-exercise/instructions/instructions-editor';
import { ArtemisProgrammingExerciseStatusModule } from 'app/entities/programming-exercise/status';
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
    providers: [
        JhiAlertService,
        DomainService,
        CodeEditorRepositoryService,
        CodeEditorRepositoryFileService,
        CodeEditorBuildLogService,
        CodeEditorSessionService,
        CodeEditorFileService,
        CodeEditorGridService,
        CodeEditorConflictStateService,
        CodeEditorSubmissionService,
    ],
})
export class ArtemisCodeEditorModule {}
