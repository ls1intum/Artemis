import { Component, Input } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { SidebarEventService } from 'app/shared/sidebar/sidebar-event.service';

@Component({
    selector: 'jhi-accordion-add-options',
    templateUrl: './accordion-add-options.component.html',
    styleUrl: './accordion-add-options.component.scss',
})
export class AccordionAddOptionsComponent {
    @Input() groupKey: string;
    faPlus = faPlus;

    constructor(private sidebarEventService: SidebarEventService) {}

    onPlusPressed(event: MouseEvent) {
        event.stopPropagation();
        this.sidebarEventService.emitSidebarAccordionPlusClickedEvent(this.groupKey);
    }
}
