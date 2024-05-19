import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, from } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { canCreateChannel } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { SidebarTypes } from 'app/types/sidebar';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { AccountService } from 'app/core/auth/account.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { Course } from 'app/entities/course.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';

@Component({
    selector: 'jhi-accordion-add-options',
    templateUrl: './accordion-add-options.component.html',
    styleUrl: './accordion-add-options.component.scss',
})
export class AccordionAddOptionsComponent implements OnInit {
    protected readonly channelSubType = ChannelSubType;
    protected readonly canCreateChannel = canCreateChannel;
    @Output() onUpdateSidebar = new EventEmitter<void>();
    @Input() sidebarType?: SidebarTypes;
    @Input() groupKey?: string;
    @Input() courseId: number;

    course: Course;
    faPlus = faPlus;

    constructor(
        private modalService: NgbModal,
        public metisConversationService: MetisConversationService,
        public accountService: AccountService,
        public conversationService: ConversationService,
        private courseStorageService: CourseStorageService,
    ) {}

    ngOnInit() {
        this.course = this.courseStorageService.getCourse(this.courseId)!;
    }

    openCreateGroupChatDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(GroupChatCreateDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(catchError(() => EMPTY))
            .subscribe((chatPartners: UserPublicInfoDTO[]) => {
                this.metisConversationService.createGroupChat(chatPartners?.map((partner) => partner.login!)).subscribe({
                    complete: () => {
                        this.metisConversationService.forceRefresh().subscribe({
                            complete: () => {},
                        });
                        this.onUpdateSidebar.emit();
                    },
                });
            });
    }

    openCreateOneToOneChatDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(OneToOneChatCreateDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(catchError(() => EMPTY))
            .subscribe((chatPartner: UserPublicInfoDTO) => {
                if (chatPartner?.login) {
                    this.metisConversationService.createOneToOneChat(chatPartner.login).subscribe({
                        complete: () => {
                            this.metisConversationService.forceRefresh().subscribe({
                                complete: () => {},
                            });
                            this.onUpdateSidebar.emit();
                        },
                    });
                }
            });
    }

    openChannelOverviewDialog(event: MouseEvent) {
        const subType = this.getChannelSubType();
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsOverviewDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.createChannelFn = subType === ChannelSubType.GENERAL ? this.metisConversationService.createChannel : undefined;
        modalRef.componentInstance.channelSubType = subType;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(catchError(() => EMPTY))
            .subscribe((result) => {
                const [newActiveConversation, isModificationPerformed] = result;
                if (isModificationPerformed) {
                    this.metisConversationService.forceRefresh(!newActiveConversation, true).subscribe({
                        complete: () => {
                            if (newActiveConversation) {
                                this.metisConversationService.setActiveConversation(newActiveConversation);
                            }
                        },
                    });
                } else {
                    if (newActiveConversation) {
                        this.metisConversationService.setActiveConversation(newActiveConversation);
                    }
                }
                this.onUpdateSidebar.emit();
            });
    }

    getChannelSubType() {
        if (this.groupKey == 'exerciseChannels') {
            return ChannelSubType.EXERCISE;
        }
        if (this.groupKey == 'generalChannels') {
            return ChannelSubType.GENERAL;
        }
        if (this.groupKey == 'lectureChannels') {
            return ChannelSubType.LECTURE;
        }
        if (this.groupKey == 'examChannels') {
            return ChannelSubType.EXAM;
        }
        return ChannelSubType.GENERAL;
    }
}
