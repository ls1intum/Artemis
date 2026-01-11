import { AfterViewInit, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { LegalDocumentLanguage } from 'app/core/shared/entities/legal-document.model';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { switchMap } from 'rxjs';

@Component({
    selector: 'jhi-imprint',
    template: ` <div [innerHTML]="imprint() | htmlForMarkdown"></div> `,
    imports: [HtmlForMarkdownPipe],
})
export class ImprintComponent implements AfterViewInit, OnInit {
    private readonly route = inject(ActivatedRoute);
    private readonly legalDocumentService = inject(LegalDocumentService);
    private readonly languageHelper = inject(JhiLanguageHelper);
    private readonly destroyRef = inject(DestroyRef);

    readonly imprint = signal<string | undefined>(undefined);

    /**
     * On init get the Imprint statement file from the Artemis server and set up a subscription to fetch the file again if the language was changed.
     */
    ngOnInit(): void {
        // Update the view if the language was changed
        this.languageHelper.language
            .pipe(
                switchMap((lang) => this.legalDocumentService.getImprint(lang as LegalDocumentLanguage)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((imprint) => this.imprint.set(imprint.text));
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
