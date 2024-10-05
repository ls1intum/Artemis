import { Component, Input, inject } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { SidebarEventService } from 'app/shared/sidebar/sidebar-event.service';

@Component({
    selector: 'jhi-accordion-add-options',
    templateUrl: './accordion-add-options.component.html',
    styleUrl: './accordion-add-options.component.scss',
})
export class AccordionAddOptionsComponent {
    private sidebarEventService = inject(SidebarEventService);

    @Input() groupKey: string;
    faPlus = faPlus;

    onPlusPressed(event: MouseEvent) {
        event.stopPropagation();
        this.sidebarEventService.emitSidebarAccordionPlusClickedEvent(this.groupKey);
    }
}
