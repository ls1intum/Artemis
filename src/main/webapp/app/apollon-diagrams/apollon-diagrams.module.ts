import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { ApollonDiagramCreateFormComponent } from './apollon-diagram-create-form.component';
import { ApollonDiagramDetailComponent } from './apollon-diagram-detail.component';
import { ApollonDiagramListComponent } from './apollon-diagram-list.component';
import { apollonDiagramsRoutes } from './apollon-diagrams.route';
import { ApollonQuizExerciseGenerationComponent } from './exercise-generation/apollon-quiz-exercise-generation.component';
import { ArTEMiSSharedModule } from '../shared';
import { ApollonDiagramStudentComponent } from './apollon-diagram-student.component';
import { ArTEMiSResultModule, ResultComponent } from '../entities/result';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';
import { ApollonDiagramTutorComponent } from './apollon-diagram-tutor/apollon-diagram-tutor.component';
import { AssessmentInstructionsComponent } from './assessment-instructions/assessment-instructions.component';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { ExpandableParagraphComponent } from 'app/apollon-diagrams/assessment-instructions/expandable-paragraph/expandable-paragraph.component';
import { ModelingAssessmentComponent } from 'app/apollon-diagrams/modeling-assessment/modeling-assessment.component';
import { ModelingAssessmentConflictComponent } from './modeling-assessment/modeling-assessment-conflict/modeling-assessment-conflict.component';

const ENTITY_STATES = [...apollonDiagramsRoutes];

@NgModule({
    imports: [ArTEMiSSharedModule, RouterModule.forChild(ENTITY_STATES), ArTEMiSResultModule, DragDropModule],
    declarations: [
        ApollonDiagramCreateFormComponent,
        ApollonDiagramDetailComponent,
        ApollonDiagramListComponent,
        ApollonQuizExerciseGenerationComponent,
        ApollonDiagramStudentComponent,
        ModelingAssessmentComponent,
        ApollonDiagramTutorComponent,
        ExpandableParagraphComponent,
        AssessmentInstructionsComponent,
        ModelingAssessmentConflictComponent
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
