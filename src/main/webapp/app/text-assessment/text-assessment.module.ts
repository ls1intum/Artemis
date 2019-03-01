import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArTEMiSTextAssessmentRoutingModule } from './text-assessment-routing.module';
import { TextAssessmentComponent } from './text-assessment.component';
import { TextSelectDirective } from './text-assessment-editor/text-select.directive';
import { TextAssessmentEditorComponent } from './text-assessment-editor/text-assessment-editor.component';
import { ArTEMiSSharedModule } from 'app/shared';
import { ArTEMiSResultModule } from 'app/entities/result';
import { TextAssessmentDetailComponent } from './text-assessment-detail/text-assessment-detail.component';
import { TextAssessmentDashboardComponent } from './text-assessment-dashboard/text-assessment-dashboard.component';
import { SortByModule } from 'app/components/pipes';
import { TextSharedModule } from 'app/text-shared/text-shared.module';

@NgModule({
    declarations: [
        TextAssessmentComponent,
        TextSelectDirective,
        TextAssessmentEditorComponent,
        TextAssessmentDetailComponent,
        TextAssessmentDashboardComponent
    ],
    imports: [CommonModule, SortByModule, ArTEMiSTextAssessmentRoutingModule, ArTEMiSSharedModule, ArTEMiSResultModule, TextSharedModule],
    exports: [TextAssessmentEditorComponent, TextAssessmentDetailComponent],
    providers: [{ provide: JhiLanguageService, useClass: JhiLanguageService }]
})
export class ArTEMiSTextAssessmentModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey !== undefined) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
