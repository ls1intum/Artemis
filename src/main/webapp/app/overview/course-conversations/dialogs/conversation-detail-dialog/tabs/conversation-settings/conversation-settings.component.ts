import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
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
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { canChangeChannelArchivalState, canDeleteChannel, canLeaveConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { catchError } from 'rxjs/operators';

@Component({
    selector: 'jhi-conversation-settings',
    templateUrl: './conversation-settings.component.html',
    styleUrls: ['./conversation-settings.component.scss'],
})
export class ConversationSettingsComponent implements OnInit, OnDestroy {
    private modalService = inject(NgbModal);
    private channelService = inject(ChannelService);
    private groupChatService = inject(GroupChatService);
    private alertService = inject(AlertService);

    private ngUnsubscribe = new Subject<void>();

    @Input()
    activeConversation: ConversationDTO;

    @Input()
    course: Course;

    @Output()
    channelArchivalChange: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    channelDeleted: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    conversationLeave: EventEmitter<void> = new EventEmitter<void>();

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faTimes = faTimes;

    conversationAsChannel: ChannelDTO | undefined;
    canLeaveConversation: boolean;
    canChangeChannelArchivalState: boolean;
    canDeleteChannel: boolean;

    ngOnInit(): void {
        this.canLeaveConversation = canLeaveConversation(this.activeConversation);

        this.conversationAsChannel = getAsChannelDTO(this.activeConversation);
        this.canChangeChannelArchivalState = this.conversationAsChannel ? canChangeChannelArchivalState(this.conversationAsChannel) : false;
        this.canDeleteChannel = this.conversationAsChannel ? canDeleteChannel(this.course, this.conversationAsChannel) : false;
    }

    leaveConversation($event: MouseEvent) {
        $event.stopPropagation();
        if (isGroupChatDTO(this.activeConversation)) {
            this.groupChatService
                .removeUsersFromGroupChat(this.course.id!, this.activeConversation.id!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe(() => {
                    this.conversationLeave.emit();
                });
            return;
        } else if (isChannelDTO(this.activeConversation)) {
            this.channelService
                .deregisterUsersFromChannel(this.course.id!, this.activeConversation.id!)
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
        const channel = getAsChannelDTO(this.activeConversation);
        if (!channel) {
            return;
        }

        const keys = {
            titleKey: 'artemisApp.dialogs.archiveChannel.title',
            questionKey: 'artemisApp.dialogs.archiveChannel.question',
            descriptionKey: 'artemisApp.dialogs.archiveChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.archiveChannel.confirmButton',
        };

        const translationParams = {
            channelName: channel.name,
        };

        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.translationParameters = translationParams;
        modalRef.componentInstance.translationKeys = keys;
        modalRef.componentInstance.canBeUndone = true;
        modalRef.componentInstance.initialize();

        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.channelService.archive(this.course.id!, channel.id!).subscribe({
                    next: () => {
                        this.channelArchivalChange.emit();
                    },
                    error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                });
            });
    }

    openUnArchivalModal(event: MouseEvent) {
        const channel = getAsChannelDTO(this.activeConversation);
        if (!channel) {
            return;
        }

        const keys = {
            titleKey: 'artemisApp.dialogs.unArchiveChannel.title',
            questionKey: 'artemisApp.dialogs.unArchiveChannel.question',
            descriptionKey: 'artemisApp.dialogs.unArchiveChannel.description',
            confirmButtonKey: 'artemisApp.dialogs.unArchiveChannel.confirmButton',
        };

        const translationParams = {
            channelName: channel.name,
        };

        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(GenericConfirmationDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.translationParameters = translationParams;
        modalRef.componentInstance.translationKeys = keys;
        modalRef.componentInstance.canBeUndone = true;
        modalRef.componentInstance.initialize();

        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe(() => {
                this.channelService
                    .unarchive(this.course.id!, channel.id!)
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe({
                        next: () => {
                            this.channelArchivalChange.emit();
                        },
                        error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
                    });
            });
    }

    deleteChannel() {
        const channel = getAsChannelDTO(this.activeConversation);
        if (!channel) {
            return;
        }
        this.channelService
            .delete(this.course.id!, channel.id!)
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
