import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AlertService } from 'app/core/alert/alert.service';
import { ApollonDiagramCreateFormComponent } from './apollon-diagram-create-form.component';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { apollonDiagramsRoutes } from './apollon-diagram.route';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { SortByModule } from 'app/shared/pipes/sort-by.module';

const ENTITY_STATES = [...apollonDiagramsRoutes];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArtemisResultModule],
    declarations: [ApollonDiagramCreateFormComponent, ApollonDiagramDetailComponent, ApollonDiagramListComponent, ApollonQuizExerciseGenerationComponent],
    entryComponents: [ApollonDiagramCreateFormComponent, ApollonQuizExerciseGenerationComponent],
    providers: [],
})
export class ArtemisApollonDiagramsModule {}
