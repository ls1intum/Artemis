import { AfterViewInit, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { LegalDocumentLanguage } from 'app/core/shared/entities/legal-document.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-privacy',
    template: `
        <div [innerHTML]="privacyStatement() | htmlForMarkdown"></div>
        @if (isAuthenticated()) {
            <a jhiTranslate="artemisApp.dataExport.title" [routerLink]="['/privacy/data-exports']"> </a>
        }
    `,
    imports: [TranslateDirective, RouterLink, HtmlForMarkdownPipe],
})
export class PrivacyComponent implements AfterViewInit, OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly legalDocumentService = inject(LegalDocumentService);
    private readonly languageHelper = inject(JhiLanguageHelper);
    private readonly accountService = inject(AccountService);
    private readonly destroyRef = inject(DestroyRef);

    readonly privacyStatement = signal<string | undefined>(undefined);
    readonly isAuthenticated = signal(false);

    /**
     * On init get the privacy statement file from the Artemis server and set up a subscription to fetch the file again if the language was changed.
     */
    ngOnInit(): void {
        this.isAuthenticated.set(this.accountService.isAuthenticated());
        // Update the view if the language was changed
        this.languageHelper.language.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((lang) => {
            this.legalDocumentService.getPrivacyStatement(lang as LegalDocumentLanguage).subscribe((statement) => this.privacyStatement.set(statement.text));
        });
    }

    /**
     * After view initialization scroll the fragment of the current route into view.
     */
    ngAfterViewInit(): void {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
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
