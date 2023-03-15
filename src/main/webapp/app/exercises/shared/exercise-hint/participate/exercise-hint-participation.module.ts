import { NgModule } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';

import { ArtemisExerciseHintManagementModule } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-management.module';
import { ExerciseHintButtonOverlayComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-button-overlay.component';
import { ExerciseHintExpandableComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-expandable.component';
import { ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { ArtemisExerciseHintSharedModule } from 'app/exercises/shared/exercise-hint/shared/exercise-hint-shared.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

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
    exports: [ExerciseHintStudentDialogComponent, ExerciseHintExpandableComponent, ExerciseHintExpandableComponent, ExerciseHintButtonOverlayComponent],
})
export class ArtemisExerciseHintParticipationModule {}
