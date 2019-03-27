import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { ApollonDiagramCreateFormComponent } from './apollon-diagram-create-form.component';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { apollonDiagramsRoutes } from './apollon-diagrams.route';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { ArTEMiSSharedModule } from '../shared';
import { ArTEMiSResultModule, ResultComponent } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { SortByModule } from 'app/components/pipes';

const ENTITY_STATES = [...apollonDiagramsRoutes];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        RouterModule.forChild(ENTITY_STATES),
        SortByModule,
        ArTEMiSResultModule
    ],
    declarations: [
        ApollonDiagramCreateFormComponent,
        ApollonDiagramDetailComponent,
        ApollonDiagramListComponent,
        ApollonQuizExerciseGenerationComponent,
    ],
    entryComponents: [
        ApollonDiagramCreateFormComponent,
        ApollonDiagramListComponent,
        ApollonQuizExerciseGenerationComponent,
        ResultComponent
    ],
    providers: [JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSApollonDiagramsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
