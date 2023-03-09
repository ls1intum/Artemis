import { HttpResponse } from '@angular/common/http';
import { GroupNotification } from 'app/entities/group-notification.model';
import { Notification } from 'app/entities/notification.model';
import { BehaviorSubject, Observable, of } from 'rxjs';

export class MockNotificationService {
    queryNotificationsFilteredBySettings = (req?: any): Observable<HttpResponse<Notification[]>> => of();
    subscribeToNotificationUpdates = (): BehaviorSubject<Notification | null> => new BehaviorSubject(null);
    interpretNotification = (notification: GroupNotification): void => {};
    cleanUp = () => {};
}
