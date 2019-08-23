import { NgModule } from '@angular/core';
import { JhiAlertService, JhiLanguageService } from 'ng-jhipster';
import { JhiLanguageHelper } from 'app/core';

import { ArtemisSharedModule } from '../shared';
import { ComplaintsComponent } from './complaints.component';
import { MomentModule } from 'ngx-moment';
import { ClipboardModule } from 'ngx-clipboard';
import { ComplaintService } from 'app/entities/complaint/complaint.service';

@NgModule({
    imports: [ArtemisSharedModule, MomentModule, ClipboardModule],
    declarations: [ComplaintsComponent],
    exports: [ComplaintsComponent],
    providers: [JhiAlertService, ComplaintService, { provide: JhiLanguageService, useClass: JhiLanguageService }],
})
export class ArtemisComplaintsModule {
    constructor(private languageService: JhiLanguageService, private languageHelper: JhiLanguageHelper) {
        this.languageHelper.language.subscribe((languageKey: string) => {
            if (languageKey) {
                this.languageService.changeLanguage(languageKey);
            }
        });
    }
}
