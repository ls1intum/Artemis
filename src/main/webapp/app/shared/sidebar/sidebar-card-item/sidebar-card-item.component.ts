import { Component, Input, OnChanges, OnInit, SimpleChanges, input } from '@angular/core';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';

@Component({
    selector: 'jhi-sidebar-card-item',
    templateUrl: './sidebar-card-item.component.html',
    styleUrls: ['./sidebar-card-item.component.scss', '../sidebar.component.scss'],
})
export class SidebarCardItemComponent implements OnInit, OnChanges {
    @Input() sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() groupKey?: string;
    unreadCount = input<number>(0);

    formattedUnreadCount: string = '';

    ngOnInit(): void {
        this.formattedUnreadCount = this.getFormattedUnreadCount();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['unreadCount']) {
            this.formattedUnreadCount = this.getFormattedUnreadCount();
        }
    }

    private getFormattedUnreadCount(): string {
        if (this.unreadCount() > 99) {
            return '99+';
        }
        return this.unreadCount().toString() || '';
    }
}
