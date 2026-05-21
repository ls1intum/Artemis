import { Component, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { ChannelDTO, getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { DialogService } from 'primeng/dynamicdialog';
import type { DynamicDialogRef } from 'primeng/dynamicdialog';
import { GenericConfirmationDialogComponent } from 'app/communication/course-conversations-components/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { onError } from 'app/shared/util/global.utils';
import { Subject, takeUntil } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { faBoxArchive, faBoxOpen, faHashtag, faLock, faTrash } from '@fortawesome/free-solid-svg-icons';
import { isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { defaultSecondLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import { filter } from 'rxjs/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { GroupChatService } from 'app/communication/conversations/service/group-chat.service';
import {
    canChangeChannelArchivalState,
    canChangeChannelPrivacyState,
    canDeleteChannel,
    canLeaveConversation,
} from 'app/communication/conversations/conversation-permissions.utils';

@Component({
    selector: 'jhi-conversation-settings',
    templateUrl: './conversation-settings.component.html',
    styleUrls: ['./conversation-settings.component.scss'],
    imports: [TranslateDirective, DeleteButtonDirective, FaIconComponent],
})
export class ConversationSettingsComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    activeConversation = input.required<ConversationDTO>();
    course = input.required<Course>();

    channelArchivalChange = output<void>();
    channelPrivacyChange = output<void>();
    channelDeleted = output<void>();
    conversationLeave = output<void>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly faTrash = faTrash;
    readonly faBoxArchive = faBoxArchive;
    readonly faBoxOpen = faBoxOpen;
    readonly faHashtag = faHashtag;
    readonly faLock = faLock;

    conversationAsChannel: ChannelDTO | undefined;
    canLeaveConversation: boolean;
    canChangeChannelArchivalState: boolean;
    canChangeChannelPrivacyState: boolean;
    canDeleteChannel: boolean;

    private dialogService = inject(DialogService);
    private channelService = inject(ChannelService);
    private groupChatService = inject(GroupChatService);
    private alertService = inject(AlertService);

    ngOnInit(): void {
        const conversation = this.activeConversation();
        if (!conversation) {
            return;
        }
        this.canLeaveConversation = canLeaveConversation(conversation);
        this.conversationAsChannel = getAsChannelDTO(conversation);
        this.canChangeChannelArchivalState = this.conversationAsChannel ? canChangeChannelArchivalState(this.conversationAsChannel) : false;
        this.canChangeChannelPrivacyState = this.conversationAsChannel ? canChangeChannelPrivacyState(this.conversationAsChannel) : false;
        this.canDeleteChannel = this.conversationAsChannel ? canDeleteChannel(this.course(), this.conversationAsChannel) : false;
    }

    leaveConversation($event: MouseEvent) {
        $event.stopPropagation();
        if (isGroupChatDTO(this.activeConversation()!)) {
            this.groupChatService
                .removeUsersFromGroupChat(this.course().id!, this.activeConversation().id!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe(() => {
                    this.conversationLeave.emit();
                });
            return;
        } else if (isChannelDTO(this.activeConversation()!)) {
            this.channelService
                .deregisterUsersFromChannel(this.course().id!, this.activeConversation().id!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe(() => {
                    this.conversationLeave.emit();
                });
            return;
        }
        throw new Error('The conversation type is not supported');
    }

    toggleChannelArchivalState(event: Event): void {
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }
        if (channel.isArchived) {
            this.openUnArchivalModal(channel);
        } else {
            this.openArchivalModal(channel);
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openArchivalModal(channel: ChannelDTO) {
        const keys = {
            titleKey: 'artemisApp.dialogs.archiveChannel.title',
            questionKey: 'artemisApp.dialogs.archiveChannel.question',
            descriptionKey: 'artemisApp.dialogs.archiveChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.archiveChannel.confirmButton',
        };

        const modalRef = this.createModal(channel, keys);

        this.openModal(modalRef, () => {
            this.channelService.archive(this.course().id!, channel.id!).subscribe({
                next: () => {
                    this.channelArchivalChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    openUnArchivalModal(channel: ChannelDTO) {
        const keys = {
            titleKey: 'artemisApp.dialogs.unArchiveChannel.title',
            questionKey: 'artemisApp.dialogs.unArchiveChannel.question',
            descriptionKey: 'artemisApp.dialogs.unArchiveChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.unArchiveChannel.confirmButton',
        };
        const modalRef = this.createModal(channel, keys);

        this.openModal(modalRef, () => {
            this.channelService
                .unarchive(this.course().id!, channel.id!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe({
                    next: () => {
                        this.channelArchivalChange.emit();
                    },
                    error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                });
        });
    }

    private openModal(ref: DynamicDialogRef | null, callback: () => void) {
        ref?.onClose
            .pipe(
                filter((result) => !!result),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(callback);
    }

    private createModal(channel: ChannelDTO, keys: { titleKey: string; questionKey: string; descriptionKey: string; confirmButtonKey: string }) {
        return this.dialogService.open(GenericConfirmationDialogComponent, {
            ...defaultSecondLayerDialogOptions,
            data: {
                translationParameters: { channelName: channel.name },
                translationKeys: keys,
                canBeUndone: true,
            },
        });
    }

    deleteChannel() {
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }
        this.channelService
            .delete(this.course().id!, channel.id!)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe({
                next: () => {
                    this.dialogErrorSource.next('');
                    this.channelDeleted.emit();
                },
                error: (errorResponse: HttpErrorResponse) => this.dialogErrorSource.next(errorResponse.message),
            });
    }

    openPublicChannelModal(channel: ChannelDTO) {
        const keys = {
            titleKey: 'artemisApp.dialogs.publicChannel.title',
            questionKey: 'artemisApp.dialogs.publicChannel.question',
            descriptionKey: 'artemisApp.dialogs.publicChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.publicChannel.confirmButton',
        };
        this.openPrivacyChangeModal(channel, keys);
    }

    openPrivateChannelModal(channel: ChannelDTO) {
        const keys = {
            titleKey: 'artemisApp.dialogs.privateChannel.title',
            questionKey: 'artemisApp.dialogs.privateChannel.question',
            descriptionKey: 'artemisApp.dialogs.privateChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.privateChannel.confirmButton',
        };
        this.openPrivacyChangeModal(channel, keys);
    }

    private openPrivacyChangeModal(channel: ChannelDTO, keys: { titleKey: string; questionKey: string; descriptionKey: string; confirmButtonKey: string }) {
        const ref = this.createModal(channel, keys);
        this.openModal(ref, () => {
            this.channelService
                .toggleChannelPrivacy(this.course().id!, channel.id!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe({
                    next: (res) => {
                        const updatedChannel = res.body;
                        if (updatedChannel) {
                            this.conversationAsChannel = updatedChannel;
                            this.channelPrivacyChange.emit();
                        }
                    },
                    error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                });
        });
    }

    toggleChannelPrivacy() {
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }

        if (!channel.isPublic) {
            this.openPublicChannelModal(channel);
        } else {
            this.openPrivateChannelModal(channel);
        }
    }
}
