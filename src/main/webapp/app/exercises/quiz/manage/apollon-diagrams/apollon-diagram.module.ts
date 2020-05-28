import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ApollonDiagramCreateFormComponent } from './apollon-diagram-create-form.component';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { apollonDiagramsRoutes } from './apollon-diagram.route';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...apollonDiagramsRoutes];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES)],
    declarations: [ApollonDiagramCreateFormComponent, ApollonDiagramDetailComponent, ApollonDiagramListComponent, ApollonQuizExerciseGenerationComponent],
})
export class ArtemisApollonDiagramsModule {}
