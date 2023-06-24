import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';

@Component({
    selector: 'jhi-privacy',
    template: `
        <div [innerHTML]="privacyStatement | htmlForMarkdown"></div>
        <a *ngIf="isAdmin" jhiTranslate="artemisApp.dataExport.title" [routerLink]="['/privacy/data-export']"> </a>
    `,
})
export class PrivacyComponent implements AfterViewInit, OnInit, OnDestroy {
    privacyStatement: string;
    private languageChangeSubscription?: Subscription;
    isAdmin: boolean;

    constructor(
        private route: ActivatedRoute,
        private privacyStatementService: LegalDocumentService,
        private languageHelper: JhiLanguageHelper,
        private accountService: AccountService,
    ) {}

    /**
     * On init get the privacy statement file from the Artemis server and set up a subscription to fetch the file again if the language was changed.
     */
    ngOnInit(): void {
        this.isAdmin = this.accountService.isAdmin();
        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe((lang) => {
            this.privacyStatementService.getPrivacyStatement(lang as LegalDocumentLanguage).subscribe((statement) => {
                this.privacyStatement = statement.text;
            });
        });
    }

    ngOnDestroy() {
        this.languageChangeSubscription?.unsubscribe();
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
