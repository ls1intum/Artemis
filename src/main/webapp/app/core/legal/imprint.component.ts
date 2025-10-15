import { AfterViewInit, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { LegalDocumentLanguage } from 'app/core/shared/entities/legal-document.model';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-imprint',
    template: ` <div class="module-bg m-3 mb-5 p-3 rounded rounded-3" [innerHTML]="imprint | htmlForMarkdown"></div> `,
    imports: [HtmlForMarkdownPipe],
})
export class ImprintComponent implements AfterViewInit, OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private legalDocumentService = inject(LegalDocumentService);
    private languageHelper = inject(JhiLanguageHelper);

    imprint?: string;
    private languageChangeSubscription?: Subscription;

    /**
     * On init get the Imprint statement file from the Artemis server and set up a subscription to fetch the file again if the language was changed.
     */
    ngOnInit(): void {
        // Update the view if the language was changed
        this.languageChangeSubscription = this.languageHelper.language.subscribe((lang) => {
            this.legalDocumentService.getImprint(lang as LegalDocumentLanguage).subscribe((imprint) => {
                this.imprint = imprint.text;
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
