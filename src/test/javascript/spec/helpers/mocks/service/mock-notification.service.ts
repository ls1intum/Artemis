import { BehaviorSubject, Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Notification } from 'app/entities/notification.model';
import { GroupNotification } from 'app/entities/group-notification.model';

export class MockNotificationService {
    queryNotificationsFilteredBySettings = (req?: any): Observable<HttpResponse<Notification[]>> => of();
    subscribeToNotificationUpdates = (): BehaviorSubject<Notification | null> => new BehaviorSubject(null);
    interpretNotification = (notification: GroupNotification): void => {};
    cleanUp = () => {};
    forceComponentReload = () => {};

    subscribeToTotalNotificationCountUpdates = (): Observable<number> => of();

    getNotificationTextTranslation = (notification: Notification, maxContentLength: number) => {
        if (notification.textIsPlaceholder) {
            return notification.text;
        }
        return notification.text ?? 'No text found';
    };

    subscribeToLoadingStateUpdates = (): Observable<number> => of();

    subscribeToSingleIncomingNotifications = (): Observable<Notification> => of();
}
