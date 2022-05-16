import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ExerciseHintExpandableComponent } from 'app/exercises/shared/exercise-hint/shared/exercise-hint-expandable.component';
import { MatExpansionModule } from '@angular/material/expansion';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisMarkdownModule, MatExpansionModule],
    declarations: [ExerciseHintExpandableComponent],
    exports: [ExerciseHintExpandableComponent],
})
export class ExerciseHintSharedModule {}
