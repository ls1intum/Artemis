import { Component } from '@angular/core';
import { VERSION } from 'app/app.constants';

@Component({
    selector: 'jhi-footer',
    templateUrl: './footer.component.html',
})
export class FooterComponent {
    readonly email = 'mailto:artemis.in@tum.de';
    readonly imprintUrl = 'https://ase.in.tum.de/lehrstuhl_1/component/content/article/179-imprint';
    private readonly issueBaseUrl = 'https://github.com/ls1intum/Artemis/issues/new?projects=ls1intum/1';
    readonly bugReportUrl = `${this.issueBaseUrl}&labels=bug&template=bug-report.md`;
    readonly featureRequestUrl = `${this.issueBaseUrl}&labels=feature&template=feature-request.md`;
    readonly releaseNotesUrl = `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;
}
