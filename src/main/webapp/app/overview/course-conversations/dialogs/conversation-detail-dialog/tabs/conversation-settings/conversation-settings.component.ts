import { Component, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { ChannelDTO, getAsChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { onError } from 'app/shared/util/global.utils';
import { EMPTY, Subject, from, takeUntil } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { faTrash } from '@fortawesome/free-solid-svg-icons';
import { canChangeChannelArchivalState, canDeleteChannel, canLeaveConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { catchError } from 'rxjs/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
    channelDeleted = output<void>();
    conversationLeave = output<void>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    readonly faTrash = faTrash;

    conversationAsChannel: ChannelDTO | undefined;
    canLeaveConversation: boolean;
    canChangeChannelArchivalState: boolean;
    canDeleteChannel: boolean;

    private modalService = inject(NgbModal);
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

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openArchivalModal(event: MouseEvent) {
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }

        const keys = {
            titleKey: 'artemisApp.dialogs.archiveChannel.title',
            questionKey: 'artemisApp.dialogs.archiveChannel.question',
            descriptionKey: 'artemisApp.dialogs.archiveChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.archiveChannel.confirmButton',
        };

        const modalRef = this.createModal(channel, event, keys);

        this.openModal(modalRef, () => {
            this.channelService.archive(this.course().id!, channel.id!).subscribe({
                next: () => {
                    this.channelArchivalChange.emit();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
        });
    }

    openUnArchivalModal(event: MouseEvent) {
        const channel = getAsChannelDTO(this.activeConversation()!);
        if (!channel) {
            return;
        }

        const keys = {
            titleKey: 'artemisApp.dialogs.unArchiveChannel.title',
            questionKey: 'artemisApp.dialogs.unArchiveChannel.question',
            descriptionKey: 'artemisApp.dialogs.unArchiveChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.unArchiveChannel.confirmButton',
        };
        const modalRef = this.createModal(channel, event, keys);

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

    private openModal(modalRef: NgbModalRef, unArchiveObservable: () => void) {
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(unArchiveObservable);
    }

    private createModal(channel: ChannelDTO, event: MouseEvent, keys: { titleKey: string; questionKey: string; descriptionKey: string; confirmButtonKey: string }): NgbModalRef {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.translationParameters = { channelName: channel.name };
        modalRef.componentInstance.translationKeys = keys;
        modalRef.componentInstance.canBeUndone = true;
        modalRef.componentInstance.initialize();
        return modalRef;
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
}
