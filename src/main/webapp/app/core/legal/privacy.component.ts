import { AfterViewInit, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { LegalDocumentLanguage } from 'app/admin/legal/legal-document.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { switchMap } from 'rxjs';

@Component({
    selector: 'jhi-privacy',
    template: `
        <div [jhiMarkdown]="privacyStatement()"></div>
        @if (isAuthenticated()) {
            <a jhiTranslate="artemisApp.dataExport.title" [routerLink]="['/privacy/data-exports']"> </a>
        }
    `,
    imports: [TranslateDirective, RouterLink, MarkdownDirective],
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
        this.languageHelper.language
            .pipe(
                switchMap((lang) => this.legalDocumentService.getPrivacyStatement(lang as LegalDocumentLanguage)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((statement) => this.privacyStatement.set(statement.text));
    }

    /**
     * After view initialization scroll the fragment of the current route into view.
     */
    ngAfterViewInit(): void {
        this.route.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this.scrollToFragment(params['fragment']);
        });
    }

    /**
     * Scrolls to the anchor with the given id. The privacy statement is rendered asynchronously (HTTP load +
     * lazy markdown rendering), so the anchor may not exist yet; retry over a few animation frames until it does.
     */
    private scrollToFragment(fragmentId: string | undefined, attemptsLeft = 20): void {
        if (!fragmentId) {
            return;
        }
        try {
            const fragment = document.querySelector('#' + fragmentId);
            if (fragment !== null) {
                fragment.scrollIntoView();
                return;
            }
        } catch (e) {
            return; // invalid selector — nothing to scroll to
        }
        if (attemptsLeft > 0) {
            requestAnimationFrame(() => this.scrollToFragment(fragmentId, attemptsLeft - 1));
        }
    }
}
