import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IdeFilterDirective } from 'app/intellij/ide-filter.directive';
import { IntellijButtonComponent } from 'app/intellij/intellij-button/intellij-button.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IdeProgrammingExerciseDetailsComponent } from './ide-programming-exercise-details/ide-programming-exercise-details.component';
import { ArTEMiSProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArTEMiSHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArTEMiSOverviewModule } from 'app/overview';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';
import { MomentModule } from 'ngx-moment';

@NgModule({
    declarations: [IdeFilterDirective, IntellijButtonComponent, IdeProgrammingExerciseDetailsComponent],
    imports: [
        CommonModule,
        FontAwesomeModule,
        ArTEMiSProgrammingExerciseModule,
        ArTEMiSHeaderExercisePageWithDetailsModule,
        ArTEMiSOverviewModule,
        ArTEMiSSharedModule,
        ArTEMiSResultModule,
        MomentModule,
    ],
    exports: [IdeFilterDirective, IntellijButtonComponent],
})
export class IntellijModule {}
