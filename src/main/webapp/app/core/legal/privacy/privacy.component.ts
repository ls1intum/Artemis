import { AfterViewInit, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { StaticHtmlContentService } from 'app/shared/service/static-html-content.service';

const privacyStatementFile = 'privacy_statement.html';

@Component({
    selector: 'jhi-privacy',
    template: `
        <h3 jhiTranslate="legal.privacy.title">Datenschutzerklärung</h3>
        <div [innerHTML]="privacyStatement"></div>
    `,
    styles: [],
})
export class PrivacyComponent implements AfterViewInit, OnInit {
    privacyStatement: SafeHtml;

    constructor(private route: ActivatedRoute, private staticContentService: StaticHtmlContentService) {}

    /**
     * On init get the privacy statement file from the Artemis server and save it.
     */
    ngOnInit(): void {
        this.staticContentService.getStaticHtmlFromArtemisServer(privacyStatementFile).subscribe((statement) => (this.privacyStatement = statement));
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
            } catch (e) {}
        });
    }
}
