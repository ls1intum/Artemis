import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { LegalDocumentLanguage } from 'app/entities/legal-document.model';
import { LegalDocumentService } from 'app/shared/service/legal-document.service';

@Component({
    selector: 'jhi-imprint',
    template: ` <div [innerHTML]="imprint | htmlForMarkdown"></div> `,
})
export class ImprintComponent implements AfterViewInit, OnInit, OnDestroy {
    imprint: string;
    private languageChangeSubscription?: Subscription;

    constructor(private route: ActivatedRoute, private legalDocumentService: LegalDocumentService, private languageHelper: JhiLanguageHelper) {}

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
