import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { ArtemisSharedModule } from 'app/shared';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { SortByModule } from 'app/components/pipes';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import {
    ParticipationSubmissionComponent,
    ParticipationSubmissionDeleteDialogComponent,
    ParticipationSubmissionDeletePopupComponent,
    participationSubmissionPopupRoute,
    participationSubmissionRoute,
    ParticipationSubmissionPopupService,
} from './';
import { ArtemisResultModule } from 'app/entities/result';

const ENTITY_STATES = [...participationSubmissionRoute, ...participationSubmissionPopupRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), SortByModule, NgxDatatableModule, ArtemisResultModule],

    declarations: [ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent, ParticipationSubmissionComponent],
    entryComponents: [ParticipationSubmissionComponent, ParticipationSubmissionDeleteDialogComponent, ParticipationSubmissionDeletePopupComponent],
    providers: [SubmissionService, ParticipationSubmissionPopupService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisParticipationSubmissionModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
