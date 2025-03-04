import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { Observable, Subject, debounceTime, distinctUntilChanged, finalize, map, takeUntil } from 'rxjs';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ChannelDTO, ChannelSubType } from 'app/entities/metis/conversation/channel.model';
import { Course } from 'app/entities/course.model';
import { canCreateChannel } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { ChannelItemComponent } from './channel-item/channel-item.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export type ChannelActionType = 'register' | 'deregister' | 'view' | 'create';
export type ChannelAction = {
    action: ChannelActionType;
    channel: ChannelDTO;
};
@Component({
    selector: 'jhi-channels-overview-dialog',
    templateUrl: './channels-overview-dialog.component.html',
    styleUrls: ['./channels-overview-dialog.component.scss'],
    imports: [LoadingIndicatorContainerComponent, ChannelItemComponent, ArtemisTranslatePipe],
})
export class ChannelsOverviewDialogComponent extends AbstractDialogComponent implements OnInit, OnDestroy {
    private channelService = inject(ChannelService);
    private alertService = inject(AlertService);

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

    isInitialized = false;

    faChevronRight = faChevronRight;

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
                    this.channels = channels;
                    this.noOfChannels = this.channels.length;
                },
                error: (errorResponse: HttpErrorResponse) => {
                    onError(this.alertService, errorResponse);
                },
            });
    }
}
