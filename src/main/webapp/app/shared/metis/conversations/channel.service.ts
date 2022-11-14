import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class ChannelService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(private http: HttpClient, private conversationService: ConversationService, private accountService: AccountService) {}

    getChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelDTO[]>> {
        return this.http.get<ChannelDTO[]>(`${this.resourceUrl}${courseId}/channels/overview`, {
            observe: 'response',
        });
    }

    delete(courseId: number, channelId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}${courseId}/channels/${channelId}`, { observe: 'response' });
    }

    archive(courseId: number, channelId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/archive`, null, { observe: 'response' });
    }

    unarchive(courseId: number, channelId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/unarchive`, null, { observe: 'response' });
    }

    create(courseId: number, channelDTO: ChannelDTO): Observable<HttpResponse<ChannelDTO>> {
        return this.http.post<ChannelDTO>(`${this.resourceUrl}${courseId}/channels`, channelDTO, { observe: 'response' }).pipe(map(this.conversationService.convertDateFromServer));
    }

    update(courseId: number, channelId: number, channelDTO: ChannelDTO): Observable<HttpResponse<ChannelDTO>> {
        return this.http
            .put<ChannelDTO>(`${this.resourceUrl}${courseId}/channels/${channelId}`, channelDTO, { observe: 'response' })
            .pipe(map(this.conversationService.convertDateFromServer));
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

    grantChannelAdminRights(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume trying to grant admin rights to self
        const userLogins = logins ? logins : [this.accountService.userIdentity?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/grant-channel-admin`, userLogins, { observe: 'response' });
    }

    revokeChannelAdminRights(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume trying to revoke admin rights from self
        const userLogins = logins ? logins : [this.accountService.userIdentity?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/revoke-channel-admin`, userLogins, { observe: 'response' });
    }
}
