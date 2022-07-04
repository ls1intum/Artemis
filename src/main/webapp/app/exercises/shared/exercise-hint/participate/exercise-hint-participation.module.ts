import { NgModule } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisExerciseHintManagementModule } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-management.module';
import { ArtemisExerciseHintSharedModule } from 'app/exercises/shared/exercise-hint/shared/exercise-hint-shared.module';
import { ExerciseHintExpandableComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-expandable.component';
import { ExerciseHintButtonOverlayComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-button-overlay.component';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisMarkdownModule,
        ArtemisExerciseHintManagementModule,
        ArtemisExerciseHintSharedModule,
        RatingModule,
        MatExpansionModule,
        ArtemisSharedComponentModule,
    ],
    declarations: [ExerciseHintStudentDialogComponent, ExerciseHintExpandableComponent, ExerciseHintButtonOverlayComponent],
    entryComponents: [ExerciseHintStudentDialogComponent],
    exports: [ExerciseHintStudentDialogComponent, ExerciseHintExpandableComponent, ExerciseHintExpandableComponent, ExerciseHintButtonOverlayComponent],
})
export class ArtemisExerciseHintParticipationModule {}
