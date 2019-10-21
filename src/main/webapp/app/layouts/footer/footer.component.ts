import { Component } from '@angular/core';
import { VERSION } from 'app/app.constants';

@Component({
    selector: 'jhi-footer',
    templateUrl: './footer.component.html',
})
export class FooterComponent {
    readonly email =
        'mailto:artemis.in@tum.de?body=Note%3A%20Please%20send%20only%20support%2Ffeature%20request%20or%20bug%20reports%20regarding%20the%20Artemis%20Platform%20to%20this%20address.%20Please%20check%20our%20public%20bug%20tracker%20at%20https%3A%2F%2Fgithub.com%2Fls1intum%2FArtemis%20for%20known%20bugs.%0AFor%20questions%20regarding%20exercises%20and%20their%20content%2C%20please%20contact%20your%20instructors.';
    readonly imprintUrl = 'https://ase.in.tum.de/lehrstuhl_1/component/content/article/179-imprint';
    private readonly issueBaseUrl = 'https://github.com/ls1intum/Artemis/issues/new?projects=ls1intum/1';
    readonly bugReportUrl = `${this.issueBaseUrl}&labels=bug&template=bug-report.md`;
    readonly featureRequestUrl = `${this.issueBaseUrl}&labels=feature&template=feature-request.md`;
    readonly releaseNotesUrl = `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;
}
