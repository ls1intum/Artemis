import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from '../shared';
import { CourseComponent, CourseService } from '../entities/course';
import { JhiAlertService } from 'ng-jhipster';
import { ArtemisResultModule } from '../entities/result';
import { HomeComponent } from '../home';
import { MomentModule } from 'ngx-moment';
import { JhiMainComponent } from '../layouts';
import { ClipboardModule } from 'ngx-clipboard';
import { exampleTextSubmissionRoute } from 'app/example-text-submission/example-text-submission.route';
import { ExampleTextSubmissionComponent } from 'app/example-text-submission/example-text-submission.component';
import { ArtemisTextAssessmentModule } from 'app/text-assessment';

const ENTITY_STATES = [...exampleTextSubmissionRoute];

@NgModule({
    imports: [BrowserModule, ArtemisSharedModule, ArtemisResultModule, MomentModule, ClipboardModule, RouterModule.forChild(ENTITY_STATES), ArtemisTextAssessmentModule],
    declarations: [ExampleTextSubmissionComponent],
    exports: [],
    entryComponents: [HomeComponent, CourseComponent, JhiMainComponent],
    providers: [CourseService, JhiAlertService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
    schemas: [],
})
export class ArtemisExampleTextSubmissionModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
