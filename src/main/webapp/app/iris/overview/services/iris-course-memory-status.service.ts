import { Injectable, OnDestroy, inject } from '@angular/core';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Observable, Subject, Subscription } from 'rxjs';
import { IrisCourseMemoryStatusDTO } from 'app/iris/shared/entities/iris-course-memory-status-dto.model';

type SubscribedChannel = { wsSubscription: Subscription; subject: Subject<IrisCourseMemoryStatusDTO> };

/**
 * Receives Course Memory progress for the current user.
 *
 * Separate from IrisWebsocketService because that one is keyed by chat session, while these events
 * are course-scoped: one subscription covers every thread the user resolves in a course.
 */
@Injectable({ providedIn: 'root' })
export class IrisCourseMemoryStatusService implements OnDestroy {
    private websocketService = inject(WebsocketService);

    private subscribedChannels = new Map<number, SubscribedChannel>();

    ngOnDestroy(): void {
        this.subscribedChannels.forEach((channel) => channel.wsSubscription.unsubscribe());
        this.subscribedChannels.clear();
    }

    /**
     * Subscribes to Course Memory status updates for a course.
     * @param courseId The course to listen on.
     */
    subscribeToCourse(courseId: number): Observable<IrisCourseMemoryStatusDTO> {
        if (!courseId) {
            throw new Error('Course ID is required');
        }

        let subscribedChannel = this.subscribedChannels.get(courseId);
        if (!subscribedChannel) {
            const subject = new Subject<IrisCourseMemoryStatusDTO>();
            const wsSubscription = this.websocketService
                .subscribe<IrisCourseMemoryStatusDTO>(this.getChannel(courseId))
                .subscribe((status: IrisCourseMemoryStatusDTO) => subject.next(status));
            subscribedChannel = { wsSubscription, subject };
            this.subscribedChannels.set(courseId, subscribedChannel);
        }

        return subscribedChannel.subject.asObservable();
    }

    /**
     * Unsubscribes from a course's Course Memory status updates.
     * @param courseId The course to stop listening on.
     * @return true if there was a subscription to remove.
     */
    unsubscribeFromCourse(courseId: number): boolean {
        const subscribedChannel = this.subscribedChannels.get(courseId);
        if (!subscribedChannel) {
            return false;
        }
        subscribedChannel.wsSubscription.unsubscribe();
        subscribedChannel.subject.complete();
        this.subscribedChannels.delete(courseId);
        return true;
    }

    private getChannel(courseId: number): string {
        return '/user/topic/iris/course-memory/' + courseId;
    }
}
