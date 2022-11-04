import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Channel } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';

export class ChannelOverviewDTO {
    public channelId: number;
    public channelName: string;
    public channelDescription: string;
    public isPublic: boolean;
    public isMember: boolean;
    public noOfMembers: number;
}

type EntityResponseType = HttpResponse<Channel>;

@Injectable({ providedIn: 'root' })
export class ChannelService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(private http: HttpClient, private conversationService: ConversationService, private accountService: AccountService) {}

    getChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelOverviewDTO[]>> {
        return this.http.get<ChannelOverviewDTO[]>(`${this.resourceUrl}${courseId}/channels/overview`, {
            observe: 'response',
        });
    }

    create(courseId: number, channel: Channel): Observable<EntityResponseType> {
        const copy = this.conversationService.convertDateFromClient(channel);
        return this.http.post<Channel>(`${this.resourceUrl}${courseId}/channels`, copy, { observe: 'response' }).pipe(map(this.conversationService.convertDateFromServer));
    }

    deregisterUsersFromChannel(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self deregistration
        const userLogins = logins ? logins : [this.accountService.userIdentity?.login];

        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/deregister`, userLogins, { observe: 'response' });
    }

    registerUsersToChannel(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self registration
        const userLogins = logins ? logins : [this.accountService.userIdentity?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/register`, userLogins, { observe: 'response' });
    }
}
