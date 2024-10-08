import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { EMPTY, Observable, Subject, debounceTime, distinctUntilChanged, finalize, from, map, takeUntil } from 'rxjs';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';

import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { Course } from 'app/entities/course.model';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { canCreateChannel } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { catchError } from 'rxjs/operators';

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
export class ChannelsOverviewDialogComponent extends AbstractDialogComponent implements OnInit, OnDestroy {
    private channelService = inject(ChannelService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);

    private ngUnsubscribe = new Subject<void>();

    canCreateChannel = canCreateChannel;
    @Input() createChannelFn?: (channel: ChannelDTO) => Observable<never>;
    @Input() course: Course;
    @Input() channelSubType: ChannelSubType;

    channelActions$ = new Subject<ChannelAction>();

    noOfChannels = 0;
    channelModificationPerformed = false;
    isLoading = false;
    channels: ChannelDTO[] = [];
    otherChannels: ChannelDTO[] = [];

    isInitialized = false;

    faChevronRight = faChevronRight;
    otherChannelsAreCollapsed = true;

    initialize() {
        super.initialize(['course', 'channelSubType']);
        if (this.isInitialized) {
            this.loadChannelsOfCourse();
        }
    }

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
            this.close([undefined, true]);
        } else {
            this.dismiss();
        }
    }

    onChannelAction(channelAction: ChannelAction) {
        this.channelActions$.next(channelAction);
    }

    performChannelAction(channelAction: ChannelAction) {
        switch (channelAction.action) {
            case 'register':
                this.channelService
                    .registerUsersToChannel(this.course.id!, channelAction.channel.id!)
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe(() => {
                        this.loadChannelsOfCourse();
                        this.channelModificationPerformed = true;
                    });
                break;
            case 'deregister':
                this.channelService
                    .deregisterUsersFromChannel(this.course.id!, channelAction.channel.id!)
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe(() => {
                        this.loadChannelsOfCourse();
                        this.channelModificationPerformed = true;
                    });
                break;
            case 'view':
                this.close([channelAction.channel, this.channelModificationPerformed]);
                break;
            case 'create':
                if (this.createChannelFn) {
                    this.createChannelFn(channelAction.channel)
                        .pipe(takeUntil(this.ngUnsubscribe))
                        .subscribe({
                            complete: () => {
                                this.loadChannelsOfCourse();
                                this.channelModificationPerformed = true;
                            },
                        });
                }
                break;
        }
    }

    loadChannelsOfCourse() {
        this.isLoading = true;
        this.channelService
            .getChannelsOfCourse(this.course.id!)
            .pipe(
                map((res: HttpResponse<ChannelDTO[]>) => res.body),
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (channels: ChannelDTO[]) => {
                    this.channels = channels?.filter((channel) => channel.subType === this.channelSubType) ?? [];
                    this.otherChannels = channels?.filter((channel) => channel.subType !== this.channelSubType) ?? [];
                    this.noOfChannels = this.channels.length;
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                },
            });
    }

    openCreateChannelDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsCreateDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((channel: ChannelDTO) => {
                this.channelActions$.next({ action: 'create', channel });
            });
    }
}
