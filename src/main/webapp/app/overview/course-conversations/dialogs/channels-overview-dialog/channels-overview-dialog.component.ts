import { Component, Input, OnInit } from '@angular/core';
import { debounceTime, distinctUntilChanged, finalize, from, map, Subject } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { Course } from 'app/entities/course.model';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';

export type ChannelActionType = 'register' | 'deregister' | 'view';
export type ChannelAction = {
    action: ChannelActionType;
    channel: ChannelDTO;
};
@Component({
    selector: 'jhi-channels-overview-dialog',
    templateUrl: './channels-overview-dialog.component.html',
    styleUrls: ['./channels-overview-dialog.component.scss'],
})
export class ChannelsOverviewDialogComponent implements OnInit {
    @Input()
    set metisConversationService(metisConversationService: MetisConversationService) {
        this._metisConversationService = metisConversationService;
        this.course = this._metisConversationService.course!;
        this.loadChannelsOfCourse();
    }
    _metisConversationService: MetisConversationService;

    channelActions$ = new Subject<ChannelAction>();

    course?: Course;
    noOfChannels = 0;

    channelModificationPerformed = false;
    isLoading = false;
    channels: ChannelDTO[] = [];
    constructor(
        private channelService: ChannelService,
        private conversationService: ConversationService,
        private alertService: AlertService,
        private activeModal: NgbActiveModal,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        this.channelActions$.pipe(debounceTime(500), distinctUntilChanged()).subscribe((channelAction) => {
            this.performChannelAction(channelAction);
        });
    }

    clear() {
        if (this.channelModificationPerformed) {
            this._metisConversationService.forceRefresh().subscribe({
                complete: () => {
                    this.activeModal.close();
                },
            });
        } else {
            this.activeModal.dismiss();
        }
    }

    trackIdentity(index: number, item: ChannelDTO) {
        return item.id!;
    }

    onChannelAction(channelAction: ChannelAction) {
        this.channelActions$.next(channelAction);
    }

    performChannelAction(channelAction: ChannelAction) {
        switch (channelAction.action) {
            case 'register':
                this.channelService.registerUsersToChannel(this.course?.id!, channelAction.channel.id!).subscribe(() => {
                    this.loadChannelsOfCourse();
                    this.channelModificationPerformed = true;
                });
                break;
            case 'deregister':
                this.channelService.deregisterUsersFromChannel(this.course?.id!, channelAction.channel.id!).subscribe(() => {
                    this.loadChannelsOfCourse();
                    this.channelModificationPerformed = true;
                });
                break;
            case 'view':
                this._metisConversationService.setActiveConversation(channelAction.channel);
                this.clear();
                break;
        }
    }
    loadChannelsOfCourse() {
        this.isLoading = true;
        this.channelService
            .getChannelsOfCourse(this.course?.id!)
            .pipe(
                map((res: HttpResponse<ChannelDTO[]>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (channels: ChannelDTO[]) => {
                    this.channels = channels ?? [];
                    this.noOfChannels = this.channels.length;
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    openCreateChannelDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsCreateDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.metisConversationService = this._metisConversationService;
        from(modalRef.result).subscribe(() => {
            this.loadChannelsOfCourse();
        });
    }
}
