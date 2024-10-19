import { Component, Input, OnInit } from '@angular/core';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';

@Component({
    selector: 'jhi-sidebar-card-item',
    templateUrl: './sidebar-card-item.component.html',
    styleUrls: ['./sidebar-card-item.component.scss', '../sidebar.component.scss'],
})
export class SidebarCardItemComponent implements OnInit {
    @Input() sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() groupKey?: string;
    @Input() unreadCount: number = 0;

    formattedUnreadCount: string = '';

    ngOnInit(): void {
        this.formattedUnreadCount = this.getFormattedUnreadCount();
    }

    private getFormattedUnreadCount(): string {
        if (this.unreadCount > 99) {
            return '99+';
        }
        return this.unreadCount?.toString() || '';
    }
}
