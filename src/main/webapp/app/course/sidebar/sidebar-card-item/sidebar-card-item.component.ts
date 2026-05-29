import { Component, computed, effect, input, signal } from '@angular/core';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { NgClass } from '@angular/common';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { addPublicFilePrefix } from 'app/app.constants';
import { SidebarCardElement, SidebarTypes } from 'app/foundation/types/sidebar';

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
export class SidebarCardItemComponent {
    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly groupKey = input<string>();
    readonly unreadCount = input<number>(0);
    readonly otherUser = signal<any>(undefined);

    readonly faPeopleGroup = faPeopleGroup;
    readonly shouldDisplayUnreadCount = computed<boolean>(() => !this.sidebarItem().conversation?.isMuted);

    /**
     * Converts the unread count into a human-friendly string (e.g. '99+' if >99).
     */
    readonly formattedUnreadCount = computed<string>(() => {
        if (this.unreadCount() > 99) {
            return '99+';
        }
        return this.unreadCount().toString() || '';
    });

    constructor() {
        effect(() => this.extractMessageUser());
    }

    /**
     * Extracts and stores the "other user" in case the item is a one-to-one chat.
     * If it's a group chat, sets the group icon explicitly.
     */
    extractMessageUser(): void {
        const sidebarItem = this.sidebarItem();
        if (sidebarItem.type === 'oneToOneChat' && (sidebarItem.conversation as OneToOneChatDTO)?.members) {
            this.otherUser.set((sidebarItem.conversation as OneToOneChatDTO).members!.find((user) => !user.isRequestingUser));
        } else {
            this.otherUser.set(undefined);
        }

        if (sidebarItem.type === 'groupChat') {
            sidebarItem.icon = this.faPeopleGroup;
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
