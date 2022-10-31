import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';

export class ChannelOverviewDTO {
    public channelId: number;
    public channelName: string;
    public channelDescription: string;
    public isPublic: boolean;
    public isMember: boolean;
    public noOfMembers: number;
}

@Injectable({ providedIn: 'root' })
export class ChannelService {
    public resourceUrl = SERVER_API_URL + '/api/courses/';

    constructor(protected http: HttpClient, private accountService: AccountService) {}

    getChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelOverviewDTO[]>> {
        return this.http.get<ChannelOverviewDTO[]>(`${this.resourceUrl}${courseId}/channels`, {
            observe: 'response',
        });
    }

    deregisterStudent(courseId: number, channelId: number, login?: string): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self deregistration
        const userLogin = login ? login : this.accountService.userIdentity?.login;

        return this.http.delete<void>(`${this.resourceUrl}${courseId}/channels/${channelId}/deregister/${userLogin}`, { observe: 'response' });
    }

    registerStudent(courseId: number, tutorialGroupId: number, login?: string): Observable<HttpResponse<void>> {
        // if no explicit login is give we assume self registration
        const userLogin = login ? login : this.accountService.userIdentity?.login;
        return this.http.post<void>(`${this.resourceUrl}${courseId}/channels/${tutorialGroupId}/register/${userLogin}`, {}, { observe: 'response' });
    }
}
