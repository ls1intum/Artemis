import { HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';

import { GroupNotification } from 'app/entities/group-notification.model';
import { Notification } from 'app/entities/notification.model';

export class MockNotificationService {
    queryNotificationsFilteredBySettings = (req?: any): Observable<HttpResponse<Notification[]>> => of();
    subscribeToNotificationUpdates = (): BehaviorSubject<Notification | null> => new BehaviorSubject(null);
    interpretNotification = (notification: GroupNotification): void => {};
    cleanUp = () => {};
}
