import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { map, Observable, shareReplay } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class TutorialGroupsNotificationService {
    private resourceUrl = SERVER_API_URL + 'api/tutorial-groups';

    tutorialGroupsForNotifications$: Observable<TutorialGroup[]>;

    constructor(private http: HttpClient) {}

    getTutorialGroupsForNotifications(): Observable<TutorialGroup[]> {
        if (!this.tutorialGroupsForNotifications$) {
            this.tutorialGroupsForNotifications$ = this.http.get<TutorialGroup[]>(`${this.resourceUrl}/for-notifications`, { observe: 'response' }).pipe(
                map((res: HttpResponse<TutorialGroup[]>) => {
                    return res.body!;
                }),
                shareReplay({ bufferSize: 1, refCount: true }),
            );
        }

        return this.tutorialGroupsForNotifications$;
    }
}
