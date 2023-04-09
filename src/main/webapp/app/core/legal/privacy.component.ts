import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PrivacyStatementService } from 'app/shared/service/privacy-statement.service';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';

@Component({
    selector: 'jhi-privacy',
    template: ` <div [innerHTML]="privacyStatement | htmlForMarkdown"></div> `,
})
export class PrivacyComponent implements AfterViewInit, OnInit, OnDestroy {
    privacyStatement: string;
    private languageChangeSubscription?: Subscription;

    constructor(private route: ActivatedRoute, private privacyStatementService: PrivacyStatementService, private languageHelper: JhiLanguageHelper) {}

    /**
     * On init get the privacy statement file from the Artemis server and set up a subscription to fetch the file again if the language was changed.
     */
    ngOnInit(): void {
        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe((lang) => {
            console.log('lang', lang);
            this.privacyStatementService.getPrivacyStatement(lang as LegalDocumentLanguage).subscribe((statement) => (this.privacyStatement = statement.text));
        });
    }

    ngOnDestroy() {
        if (this.languageChangeSubscription) {
            this.languageChangeSubscription.unsubscribe();
        }
    }

    /**
     * After view initialization scroll the fragment of the current route into view.
     */
    ngAfterViewInit(): void {
        this.route.params.subscribe((params) => {
            try {
                const fragment = document.querySelector('#' + params['fragment']);
                if (fragment !== null) {
                    fragment.scrollIntoView();
                }
            } catch (e) {
                /* empty */
            }
        });
    }
}
