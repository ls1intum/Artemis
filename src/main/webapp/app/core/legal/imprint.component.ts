import { LegalDocumentFragmentDirective } from './legal-document-fragment.directive';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { LegalDocumentLanguage } from 'app/admin/legal/legal-document.model';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { switchMap } from 'rxjs';

@Component({
    selector: 'jhi-imprint',
    template: ` <div [jhiMarkdown]="imprint()" (markdownRendered)="onMarkdownRendered()"></div> `,
    imports: [MarkdownDirective],
})
export class ImprintComponent extends LegalDocumentFragmentDirective implements OnInit {
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
}
