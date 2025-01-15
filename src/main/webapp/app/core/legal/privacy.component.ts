import { AfterViewInit, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-privacy',
    template: `
        <div [innerHTML]="privacyStatement | htmlForMarkdown"></div>
        @if (isAuthenticated) {
            <a jhiTranslate="artemisApp.dataExport.title" [routerLink]="['/privacy/data-exports']"> </a>
        }
    `,
    imports: [TranslateDirective, RouterLink, HtmlForMarkdownPipe],
})
export class PrivacyComponent implements AfterViewInit, OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private legalDocumentService = inject(LegalDocumentService);
    private languageHelper = inject(JhiLanguageHelper);
    private accountService = inject(AccountService);

    privacyStatement?: string;
    private languageChangeSubscription?: Subscription;
    isAuthenticated: boolean;

    /**
     * On init get the privacy statement file from the Artemis server and set up a subscription to fetch the file again if the language was changed.
     */
    ngOnInit(): void {
        this.isAuthenticated = this.accountService.isAuthenticated();
        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe((lang) => {
            this.legalDocumentService.getPrivacyStatement(lang as LegalDocumentLanguage).subscribe((statement) => (this.privacyStatement = statement.text));
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
