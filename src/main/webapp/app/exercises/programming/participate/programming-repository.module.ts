import { NgModule } from '@angular/core';
import { ArtemisCodeEditorModule } from 'app/exercises/programming/shared/code-editor/code-editor.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
import { ArtemisProgrammingRepositoryRoutingModule } from 'app/exercises/programming/participate/programming-repository-routing.module';
import { FormsModule } from '@angular/forms';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { CommitHistoryComponent } from 'app/localvc/commit-history/commit-history.component';
import { CommitDetailsViewComponent } from 'app/localvc/commit-details-view/commit-details-view.component';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { GitDiffReportComponent } from 'app/exercises/programming/git-diff-report/git-diff-report.component';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        FormsModule,
        ComplaintsForTutorComponent,
        ArtemisProgrammingRepositoryRoutingModule,
        ArtemisCodeEditorModule,
        ArtemisResultModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseModule,
        ArtemisProgrammingExerciseActionsModule,
        GitDiffReportComponent,
        RepositoryViewComponent,
        CommitHistoryComponent,
        CommitDetailsViewComponent,
    ],
    exports: [RepositoryViewComponent],
})
export class ArtemisProgrammingRepositoryModule {}
