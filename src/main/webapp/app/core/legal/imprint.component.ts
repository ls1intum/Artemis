import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@Component({
    selector: 'jhi-imprint',
    template: ` <div [innerHTML]="imprint | htmlForMarkdown"></div> `,
    standalone: true,
    imports: [ArtemisMarkdownModule],
})
export class ImprintComponent implements OnInit, OnDestroy {
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
}
