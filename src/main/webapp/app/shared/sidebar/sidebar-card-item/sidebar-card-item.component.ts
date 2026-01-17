import { Component, Input, OnChanges, OnInit, SimpleChanges, input, signal } from '@angular/core';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { NgClass } from '@angular/common';
import { ProfilePictureComponent } from '../../profile-picture/profile-picture.component';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { SidebarCardElement, SidebarTypes } from 'app/shared/types/sidebar';

@Component({
    selector: 'jhi-sidebar-card-item',
    templateUrl: './sidebar-card-item.component.html',
    styleUrls: ['./sidebar-card-item.component.scss', '../sidebar.component.scss'],
    imports: [
        FaIconComponent,
        TranslateDirective,
        StudentExamWorkingTimeComponent,
        NgClass,
        ProfilePictureComponent,
        SubmissionResultStatusComponent,
        ArtemisDatePipe,
        ArtemisDurationFromSecondsPipe,
    ],
})
export class SidebarCardItemComponent implements OnInit, OnChanges {
    @Input() sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() groupKey?: string;
    unreadCount = input<number>(0);
    otherUser: any;

    readonly faPeopleGroup = faPeopleGroup;
    readonly shouldDisplayUnreadCount = signal<boolean>(false);

    formattedUnreadCount: string = '';

    ngOnInit(): void {
        this.formattedUnreadCount = this.getFormattedUnreadCount();
        this.extractMessageUser();
        this.updateShouldDisplayUnreadCount();
    }

    ngOnChanges(changes: SimpleChanges): void {
        // Recompute unread count string if value changes
        if (changes['unreadCount']) {
            this.formattedUnreadCount = this.getFormattedUnreadCount();
        }
        if (changes['sidebarItem']) {
            this.updateShouldDisplayUnreadCount();
        }
    }

    /**
     * Converts the unread count into a human-friendly string (e.g. '99+' if >99).
     */
    private getFormattedUnreadCount(): string {
        if (this.unreadCount() > 99) {
            return '99+';
        }
        return this.unreadCount().toString() || '';
    }

    protected hasMarkedAsUnread(): boolean {
        return this.sidebarItem.conversation?.isMarkedAsUnread ?? false;
    }

    protected updateShouldDisplayUnreadCount(): void {
        this.shouldDisplayUnreadCount.set(!this.sidebarItem.conversation?.isMuted);
    }

    /**
     * Extracts and stores the "other user" in case the item is a one-to-one chat.
     * If it's a group chat, sets the group icon explicitly.
     */
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

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
