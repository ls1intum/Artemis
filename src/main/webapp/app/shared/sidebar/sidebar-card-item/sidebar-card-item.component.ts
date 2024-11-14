import { Component, Input, OnChanges, OnInit, SimpleChanges, input } from '@angular/core';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';

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
    otherUser: any;

    readonly faPeopleGroup = faPeopleGroup;

    formattedUnreadCount: string = '';

    ngOnInit(): void {
        this.formattedUnreadCount = this.getFormattedUnreadCount();
        this.extractMessageUser();
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

    extractMessageUser(): void {
        if (this.sidebarItem.type === 'oneToOneChat' && (this.sidebarItem.conversation as OneToOneChatDTO)?.members) {
            this.otherUser = (this.sidebarItem.conversation as OneToOneChatDTO).members!.find((user) => !user.isRequestingUser);
        } else {
            this.otherUser = null;
        }

        if (this.sidebarItem.type === 'groupChat') {
            this.sidebarItem.icon = this.faPeopleGroup;
        }
    }
}
