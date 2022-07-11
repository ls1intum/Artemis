import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseManagementRoutingModule } from 'app/exercises/programming/manage/programming-exercise-management-routing.module';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisProgrammingExerciseActionsModule } from 'app/exercises/programming/shared/actions/programming-exercise-actions.module';
import { ArtemisProgrammingExerciseInstructionsEditorModule } from 'app/exercises/programming/manage/instructions-editor/programming-exercise-instructions-editor.module';
import { ArtemisProgrammingExerciseUpdateModule } from 'app/exercises/programming/manage/update/programming-exercise-update.module';
import { ProgrammingExerciseImportComponent } from 'app/exercises/programming/manage/programming-exercise-import.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisProgrammingExerciseStatusModule } from 'app/exercises/programming/manage/status/programming-exercise-status.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { ExerciseDetailsModule } from 'app/exercises/shared/exercise/exercise-details/exercise-details.module';
import { AssessmentInstructionsModule } from 'app/assessment/assessment-instructions/assessment-instructions.module';
import { ProgrammingExerciseEditSelectedComponent } from 'app/exercises/programming/manage/programming-exercise-edit-selected.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from '../shared/actions/programming-exercise-instructor-repo-download.component';
import { ArtemisProgrammingExerciseLifecycleModule } from 'app/exercises/programming/shared/lifecycle/programming-exercise-lifecycle.module';
import { ProgrammingExerciseInstructorExerciseDownloadComponent } from '../shared/actions/programming-exercise-instructor-exercise-download.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { GitDiffReportModule } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.module';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-example-solution-repo-download.component';
import { TestwiseCoverageReportModule } from 'app/exercises/programming/hestia/testwise-coverage-report/testwise-coverage-report.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisResultModule,
        ArtemisAssessmentSharedModule,
        ArtemisPlagiarismModule,
        ArtemisProgrammingExerciseModule,
        ArtemisProgrammingExerciseManagementRoutingModule,
        ArtemisProgrammingAssessmentModule,
        ArtemisProgrammingExerciseActionsModule,
        ArtemisProgrammingExerciseInstructionsEditorModule,
        ArtemisProgrammingExerciseUpdateModule,
        ArtemisProgrammingExerciseStatusModule,
        FeatureToggleModule,
        ExerciseDetailsModule,
        AssessmentInstructionsModule,
        OrionModule,
        ArtemisProgrammingExerciseLifecycleModule,
        SubmissionResultStatusModule,
        GitDiffReportModule,
        TestwiseCoverageReportModule,
    ],
    declarations: [
        ProgrammingExerciseDetailComponent,
        ProgrammingExerciseImportComponent,
        ProgrammingExerciseEditSelectedComponent,
        ProgrammingExerciseInstructorRepoDownloadComponent,
        ProgrammingExerciseInstructorExerciseDownloadComponent,
        ProgrammingExerciseExampleSolutionRepoDownloadComponent,
    ],
    exports: [ProgrammingExerciseExampleSolutionRepoDownloadComponent],
})
export class ArtemisProgrammingExerciseManagementModule {}
