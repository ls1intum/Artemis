import { AfterViewInit, Component, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';

import { StaticContentService } from 'app/shared/service/static-content.service';

const imprintFile = 'imprint.html';

@Component({
    selector: 'jhi-imprint',
    template: `
        <h3 jhiTranslate="legal.imprint.title">Impressum</h3>
        <div [innerHTML]="imprint"></div>
    `,
})
export class ImprintComponent implements AfterViewInit, OnInit {
    imprint: SafeHtml;

    constructor(private route: ActivatedRoute, private staticContentService: StaticContentService) {}

    /**
     * On init get the privacy statement file from the Artemis server and save it.
     */
    ngOnInit(): void {
        this.staticContentService.getStaticHtmlFromArtemisServer(imprintFile).subscribe((imprint) => (this.imprint = imprint));
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
