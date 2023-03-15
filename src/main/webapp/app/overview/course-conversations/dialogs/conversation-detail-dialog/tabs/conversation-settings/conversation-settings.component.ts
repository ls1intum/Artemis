import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Subject, from, takeUntil } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { GenericConfirmationDialogComponent } from 'app/overview/course-conversations/dialogs/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { canChangeChannelArchivalState, canDeleteChannel, canLeaveConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { GroupChatService } from 'app/shared/metis/conversations/group-chat.service';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-conversation-settings',
    templateUrl: './conversation-settings.component.html',
    styleUrls: ['./conversation-settings.component.scss'],
})
export class ConversationSettingsComponent implements OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    @Input()
    activeConversation: ConversationDto;

    @Input()
    course: Course;

    @Output()
    channelArchivalChange: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    channelDeleted: EventEmitter<void> = new EventEmitter<void>();

    @Output()
    conversationLeave: EventEmitter<void> = new EventEmitter<void>();

    canChangeArchivalState = canChangeChannelArchivalState;
    canDeleteChannel = canDeleteChannel;
    canLeaveConversation = canLeaveConversation;
    getAsChannel = getAsChannelDto;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    faTimes = faTimes;

    constructor(private modalService: NgbModal, private channelService: ChannelService, private groupChatService: GroupChatService, private alertService: AlertService) {}

    leaveConversation($event: MouseEvent) {
        $event.stopPropagation();
        if (isGroupChatDto(this.activeConversation)) {
            this.groupChatService
                .removeUsersFromGroupChat(this.course.id!, this.activeConversation.id!)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe(() => {
                    this.conversationLeave.emit();
                });
            return;
        } else if (isChannelDto(this.activeConversation)) {
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
        const channel = getAsChannelDto(this.activeConversation);
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
        const channel = getAsChannelDto(this.activeConversation);
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
        const channel = getAsChannelDto(this.activeConversation);
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
