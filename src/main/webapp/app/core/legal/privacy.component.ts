import { AfterViewInit, Component, OnDestroy, OnInit, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { LegalDocumentLanguage } from 'app/core/shared/entities/legal-document.model';
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
    private legalDocumentService = inject(LegalDocumentService);
    private languageHelper = inject(JhiLanguageHelper);
    private accountService = inject(AccountService);

    fragment = input<string>();

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
        // Initial scroll if fragment exists
        this.scrollToFragment();
    }

    constructor() {
        effect(() => {
            this.scrollToFragment();
        });
    }

    scrollToFragment(): void {
        const fragmentValue = this.fragment();
        if (fragmentValue) {
            // Use setTimeout to ensure DOM is updated
            setTimeout(() => {
                try {
                    const element = document.querySelector('#' + fragmentValue);
                    element?.scrollIntoView();
                } catch (e) {
                    /* empty */
                }
            });
        }
    }
}
