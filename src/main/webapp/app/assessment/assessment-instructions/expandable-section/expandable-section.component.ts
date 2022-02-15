import { Component, Input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-expandable-section',
    templateUrl: './expandable-section.component.html',
})
export class ExpandableSectionComponent {
    @Input() headerKey: string;
    @Input() isCollapsed = false;
    @Input() hasTranslation = true;
    @Input() isSubHeader = false;

    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;
}
