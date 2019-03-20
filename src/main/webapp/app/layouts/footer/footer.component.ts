import { Component } from '@angular/core';
import { VERSION } from 'app/app.constants';

@Component({
    selector: 'jhi-footer',
    templateUrl: './footer.component.html'
})
export class FooterComponent {
    readonly email = 'artemis.ase@in.tum.de';
    readonly imprintUrl = 'https://ase.in.tum.de/lehrstuhl_1/component/content/article/179-imprint';
    get releaseNotesUrl(): string {
        return `https://github.com/ls1intum/ArTEMiS/releases/tag/${VERSION}`;
    }
}
