import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { Observable, map, shareReplay } from 'rxjs';
import { SERVER_API_URL } from 'app/environments/environment';

@Injectable({ providedIn: 'root' })
export class CourseConversationsNotificationsService {
    private resourceUrl = SERVER_API_URL + 'api/courses';

    coursesForNotifications$: Observable<Conversation[]>;

    constructor(private http: HttpClient) {}

    getConversationsForNotifications(): Observable<Conversation[]> {
        if (!this.coursesForNotifications$) {
            this.coursesForNotifications$ = this.http.get<Conversation[]>(`${this.resourceUrl}/conversations-for-notifications`, { observe: 'response' }).pipe(
                map((res: HttpResponse<Conversation[]>) => {
                    return res.body!;
                }),
                shareReplay({ bufferSize: 1, refCount: true }),
            );
        }

        return this.coursesForNotifications$;
    }
}
