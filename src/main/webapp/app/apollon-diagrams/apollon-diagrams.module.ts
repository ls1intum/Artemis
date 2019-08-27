import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { ApollonDiagramCreateFormComponent } from './apollon-diagram-create-form.component';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { apollonDiagramsRoutes } from './apollon-diagrams.route';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { ArtemisSharedModule } from '../shared';
import { ArtemisResultModule, ResultComponent } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { SortByModule } from 'app/components/pipes';

const ENTITY_STATES = [...apollonDiagramsRoutes];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, ArtemisResultModule],
    declarations: [ApollonDiagramCreateFormComponent, ApollonDiagramDetailComponent, ApollonDiagramListComponent, ApollonQuizExerciseGenerationComponent],
    entryComponents: [ApollonDiagramCreateFormComponent, ApollonQuizExerciseGenerationComponent],
    providers: [JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisApollonDiagramsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
