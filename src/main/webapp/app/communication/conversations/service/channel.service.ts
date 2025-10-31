import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ChannelDTO, ChannelIdAndNameDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { convertDateFromServer } from 'app/shared/util/date.utils';

@Injectable({ providedIn: 'root' })
export class ChannelService {
    public resourceUrl = '/api/communication/courses/';

    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    getChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelDTO[]>> {
        return this.http.get<ChannelDTO[]>(`${this.resourceUrl}${courseId}/channels/overview`, {
            observe: 'response',
        });
    }

    getPublicChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelIdAndNameDTO[]>> {
        return this.http.get<ChannelIdAndNameDTO[]>(`${this.resourceUrl}${courseId}/channels/public-overview`, {
            observe: 'response',
        });
    }

    getChannelOfExercise(courseId: number, exerciseId: number): Observable<HttpResponse<ChannelDTO>> {
        return this.http.get<ChannelDTO>(`${this.resourceUrl}${courseId}/exercises/${exerciseId}/channel`, {
            observe: 'response',
        });
    }

    getChannelOfLecture(courseId: number, lectureId: number): Observable<HttpResponse<ChannelDTO>> {
        return this.http.get<ChannelDTO>(`${this.resourceUrl}${courseId}/lectures/${lectureId}/channel`, {
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
        return this.http.post<ChannelDTO>(`${this.resourceUrl}${courseId}/channels`, channelDTO, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    update(courseId: number, channelId: number, channelDTO: ChannelDTO): Observable<HttpResponse<ChannelDTO>> {
        return this.http.put<ChannelDTO>(`${this.resourceUrl}${courseId}/channels/${channelId}`, channelDTO, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }
    deregisterUsersFromChannel(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self deregistration
        const userLogins = logins ? logins : [this.accountService.userIdentity()?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/deregister`, userLogins, { observe: 'response' });
    }

    registerUsersToChannel(
        courseId: number,
        channelId: number,
        addAllStudents = false,
        addAllTutors = false,
        addAllInstructors = false,
        logins?: string[],
    ): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self registration (will be ignored on the server if any of the addAll booleans is true)
        const userLogins = logins ? logins : [this.accountService.userIdentity()?.login];

        let params = new HttpParams();
        if (addAllStudents) {
            params = params.set('addAllStudents', 'true');
        }
        if (addAllTutors) {
            params = params.set('addAllTutors', 'true');
        }
        if (addAllInstructors) {
            params = params.set('addAllInstructors', 'true');
        }

        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/register`, userLogins, { observe: 'response', params });
    }

    grantChannelModeratorRole(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume trying to grant channel moderator role to self
        const userLogins = logins ? logins : [this.accountService.userIdentity()?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/grant-channel-moderator`, userLogins, { observe: 'response' });
    }

    revokeChannelModeratorRole(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume trying to revoke channel moderator role from self
        const userLogins = logins ? logins : [this.accountService.userIdentity()?.login];
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/revoke-channel-moderator`, userLogins, { observe: 'response' });
    }

    /**
     * Toggles the privacy status of a channel (if public, it becomes private; if private, it becomes public).
     *
     * @param courseId The ID of the course the channel belongs to.
     * @param channelId The ID of the channel whose privacy status will be changed.
     */
    toggleChannelPrivacy(courseId: number, channelId: number): Observable<HttpResponse<ChannelDTO>> {
        return this.http
            .post<ChannelDTO>(`${this.resourceUrl}${courseId}/channels/${channelId}/toggle-privacy`, null, { observe: 'response' })
            .pipe(map(this.convertDateFromServer));
    }

    public convertDateFromServer = (res: HttpResponse<ChannelDTO>): HttpResponse<ChannelDTO> => {
        if (res.body) {
            this.convertServerDates(res.body);
        }
        return res;
    };

    public convertServerDates(conversation: ChannelDTO) {
        conversation.creationDate = convertDateFromServer(conversation.creationDate);
        conversation.lastMessageDate = convertDateFromServer(conversation.lastMessageDate);
        conversation.lastReadDate = convertDateFromServer(conversation.lastReadDate);
        return conversation;
    }
}
