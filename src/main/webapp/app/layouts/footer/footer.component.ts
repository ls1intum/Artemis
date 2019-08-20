import { Component } from '@angular/core';
import { VERSION } from 'app/app.constants';

@Component({
    selector: 'jhi-footer',
    templateUrl: './footer.component.html',
})
export class FooterComponent {
    readonly email = 'mailto:artemis.ase@in.tum.de';
    readonly imprintUrl = 'https://ase.in.tum.de/lehrstuhl_1/component/content/article/179-imprint';
    readonly bugReportUrl = 'https://github.com/ls1intum/Artemis/issues/new?assignees=jpbernius%2C+krusche&labels=bug&template=bug-report.md';
    readonly featureRequestUrl = 'https://github.com/ls1intum/Artemis/issues/new?assignees=krusche%2C+jpbernius&labels=feature&template=feature-request.md';
    get releaseNotesUrl(): string {
        return `https://github.com/ls1intum/Artemis/releases/tag/${VERSION}`;
    }
}
