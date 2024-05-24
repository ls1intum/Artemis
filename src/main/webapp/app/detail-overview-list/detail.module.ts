import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { DetailOverviewListComponent } from 'app/detail-overview-list/detail-overview-list.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { GitDiffReportModule } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisProgrammingExerciseLifecycleModule } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { IrisModule } from 'app/iris/iris.module';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisProgrammingExerciseBuildDetailsModule } from 'app/exercises/programming/shared/build-details/build-details.module';

@NgModule({
    imports: [
        RouterModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseStatusModule,
        ArtemisProgrammingExerciseActionsModule,
        GitDiffReportModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseLifecycleModule,
        AssessmentInstructionsModule,
        IrisModule,
        ArtemisModelingEditorModule,
        ArtemisProgrammingExerciseBuildDetailsModule,
    ],
    declarations: [DetailOverviewListComponent],
    exports: [DetailOverviewListComponent],
})
export class DetailModule {}
