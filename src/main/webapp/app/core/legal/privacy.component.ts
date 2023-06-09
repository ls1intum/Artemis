import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PrivacyStatementService } from 'app/shared/service/privacy-statement.service';
import { PrivacyStatementLanguage } from 'app/entities/privacy-statement.model';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-privacy',
    template: `
        <h3 jhiTranslate="legal.privacy.title">Datenschutzerklärung</h3>
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
        private privacyStatementService: PrivacyStatementService,
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
            this.privacyStatementService.getPrivacyStatement(lang as PrivacyStatementLanguage).subscribe((statement) => (this.privacyStatement = statement.text));
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
