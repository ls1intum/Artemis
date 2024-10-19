import { Component, Input } from '@angular/core';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';

@Component({
    selector: 'jhi-sidebar-card-item',
    templateUrl: './sidebar-card-item.component.html',
    styleUrls: ['./sidebar-card-item.component.scss', '../sidebar.component.scss'],
})
export class SidebarCardItemComponent {
    @Input() sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() groupKey?: string;
}
