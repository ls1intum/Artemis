import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { StaticContentService } from 'app/shared/service/static-content.service';
import { AccountService } from 'app/core/auth/account.service';

const privacyStatementFile = 'privacy_statement.html';

@Component({
    selector: 'jhi-privacy',
    template: `
        <h3 jhiTranslate="legal.privacy.title">Datenschutzerklärung</h3>
        <div [innerHTML]="privacyStatement"></div>
        <a *ngIf="isAdmin" jhiTranslate="artemisApp.dataExport.title" [routerLink]="['/data-export']"> </a>
    `,
})
export class PrivacyComponent implements AfterViewInit, OnInit {
    privacyStatement: SafeHtml;
    isAdmin = false;

    constructor(private route: ActivatedRoute, private staticContentService: StaticContentService, private accountService: AccountService) {}

    /**
     * On init get the privacy statement file from the Artemis server and save it.
     */
    ngOnInit(): void {
        this.isAdmin = this.accountService.isAdmin();
        this.staticContentService.getStaticHtmlFromArtemisServer(privacyStatementFile).subscribe((statement) => (this.privacyStatement = statement));
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
