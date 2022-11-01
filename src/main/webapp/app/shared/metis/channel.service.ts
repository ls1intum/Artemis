import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

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

    constructor(protected http: HttpClient) {}

    getChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelOverviewDTO[]>> {
        return this.http.get<ChannelOverviewDTO[]>(`${this.resourceUrl}${courseId}/channels`, {
            observe: 'response',
        });
    }
}
