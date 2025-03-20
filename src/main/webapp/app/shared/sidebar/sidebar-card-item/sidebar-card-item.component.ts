import { Component, Input, OnChanges, OnInit, SimpleChanges, input } from '@angular/core';
import { OneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { faPeopleGroup } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { NgClass } from '@angular/common';
import { ProfilePictureComponent } from '../../profile-picture/profile-picture.component';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status.component';
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

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
