import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subject, debounceTime, distinctUntilChanged, finalize, from, map, takeUntil } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { Course } from 'app/entities/course.model';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { canCreateChannel } from 'app/shared/metis/conversations/conversation-permissions.utils';

export type ChannelActionType = 'register' | 'deregister' | 'view' | 'create';
export type ChannelAction = {
    action: ChannelActionType;
    channel: ChannelDTO;
};
@Component({
    selector: 'jhi-channels-overview-dialog',
    templateUrl: './channels-overview-dialog.component.html',
    styleUrls: ['./channels-overview-dialog.component.scss'],
})
export class ChannelsOverviewDialogComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    canCreateChannel = canCreateChannel;
    @Input()
    createChannelFn: (channel: ChannelDTO) => Observable<never>;

    @Input()
    course: Course;

    channelActions$ = new Subject<ChannelAction>();

    noOfChannels = 0;

    channelModificationPerformed = false;
    isLoading = false;
    channels: ChannelDTO[] = [];

    isInitialized = false;

    initialize() {
        if (!this.course || !this.createChannelFn) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
            this.loadChannelsOfCourse();
        }
    }

    constructor(
        private channelService: ChannelService,
        private conversationService: ConversationService,
        private alertService: AlertService,
        private activeModal: NgbActiveModal,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        this.channelActions$.pipe(debounceTime(500), distinctUntilChanged(), takeUntil(this.ngUnsubscribe)).subscribe((channelAction) => {
            this.performChannelAction(channelAction);
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    clear() {
        if (this.channelModificationPerformed) {
            this.activeModal.close();
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
                this.channelService
                    .registerUsersToChannel(this.course?.id!, channelAction.channel.id!)
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe(() => {
                        this.loadChannelsOfCourse();
                        this.channelModificationPerformed = true;
                    });
                break;
            case 'deregister':
                this.channelService
                    .deregisterUsersFromChannel(this.course?.id!, channelAction.channel.id!)
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe(() => {
                        this.loadChannelsOfCourse();
                        this.channelModificationPerformed = true;
                    });
                break;
            case 'view':
                this.activeModal.close(channelAction);
                break;
            case 'create':
                this.createChannelFn(channelAction.channel)
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe({
                        complete: () => {
                            this.loadChannelsOfCourse();
                            this.channelModificationPerformed = true;
                        },
                    });
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
                takeUntil(this.ngUnsubscribe),
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
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((channel: ChannelDTO) => {
                this.channelActions$.next({ action: 'create', channel });
            });
    }
}
