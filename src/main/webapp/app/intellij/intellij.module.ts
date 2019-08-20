import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IntellijButtonComponent } from 'app/intellij/intellij-button/intellij-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IdeProgrammingExerciseDetailsComponent } from './ide-programming-exercise-details/ide-programming-exercise-details.component';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { MomentModule } from 'ngx-moment';

@NgModule({
    declarations: [IntellijButtonComponent, IdeProgrammingExerciseDetailsComponent],
    imports: [
        CommonModule,
        FontAwesomeModule,
        ArtemisProgrammingExerciseModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisSharedModule,
        ArtemisResultModule,
        MomentModule,
    ],
    exports: [IntellijButtonComponent],
})
export class IntellijModule {}
