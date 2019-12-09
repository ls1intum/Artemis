import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-expandable-section',
    templateUrl: './expandable-section.component.html',
})
export class ExpandableSectionComponent {
    @Input() headerKey: string;
    @Input() isCollapsed = false;
}
