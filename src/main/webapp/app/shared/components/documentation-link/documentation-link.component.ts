import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';

// The routes here are used to build the link to the documentation.
// Therefore, it's important that they exactly match the url to the subpage of the documentation.
// Additionally, the case names must match the keys in documentationLinks.json for the tooltip.
const DocumentationLinks: { [key: string]: string } = {
    SshSetup: 'icl/ssh-intro',
};

export type DocumentationType = keyof typeof DocumentationLinks;

@Component({
    selector: 'jhi-documentation-link',
    standalone: true,
    templateUrl: './documentation-link.component.html',
    imports: [TranslateDirective],
})
export class DocumentationLinkComponent {
    readonly BASE_URL = 'https://docs.artemis.cit.tum.de/user/';
    readonly DocumentationLinks = DocumentationLinks;

    documentationType = input<string>();
    displayString = input<string>();
}
