import { Service, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ChannelDTO, ChannelIdAndNameDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { AccountService } from 'app/core/auth/account.service';
import { toChannelDTO, toChannelRequest } from 'app/communication/conversations/service/conversation-dto-mapper';
import { ChannelApi } from 'app/openapi/api/channel-api';
import type { Channel } from 'app/openapi/model/channel';

@Service()
export class ChannelService {
    private readonly channelApi = inject(ChannelApi);
    private readonly accountService = inject(AccountService);

    getChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelDTO[]>> {
        return this.channelApi.getCourseChannelsOverview(courseId).pipe(map(this.toChannelListResponse));
    }

    getChannelsOfCourseForStaff(courseId: number): Observable<HttpResponse<ChannelDTO[]>> {
        return this.channelApi.getCourseChannelsOverviewForStaff(courseId).pipe(map(this.toChannelListResponse));
    }

    getChannelsOfCourseForInstructor(courseId: number): Observable<HttpResponse<ChannelDTO[]>> {
        return this.channelApi.getCourseChannelsOverviewForInstructor(courseId).pipe(map(this.toChannelListResponse));
    }

    getPublicChannelsOfCourse(courseId: number): Observable<HttpResponse<ChannelIdAndNameDTO[]>> {
        return this.channelApi.getCoursePublicChannelsOverview(courseId);
    }

    getChannelOfExercise(courseId: number, exerciseId: number): Observable<HttpResponse<ChannelDTO>> {
        return this.channelApi.getExerciseChannel(courseId, exerciseId).pipe(map(this.toChannelResponse));
    }

    getChannelOfLecture(courseId: number, lectureId: number): Observable<HttpResponse<ChannelDTO>> {
        return this.channelApi.getLectureChannel(courseId, lectureId).pipe(map(this.toChannelResponse));
    }

    delete(courseId: number, channelId: number): Observable<HttpResponse<void>> {
        return this.channelApi.deleteChannel(courseId, channelId);
    }

    archive(courseId: number, channelId: number): Observable<HttpResponse<void>> {
        return this.channelApi.archiveChannel(courseId, channelId);
    }

    unarchive(courseId: number, channelId: number): Observable<HttpResponse<void>> {
        return this.channelApi.unArchiveChannel(courseId, channelId);
    }

    create(courseId: number, channelDTO: ChannelDTO): Observable<HttpResponse<ChannelDTO>> {
        return this.channelApi.createChannel(courseId, toChannelRequest(channelDTO)).pipe(map(this.toChannelResponse));
    }

    update(courseId: number, channelId: number, channelDTO: ChannelDTO): Observable<HttpResponse<ChannelDTO>> {
        return this.channelApi.updateChannel(courseId, channelId, toChannelRequest(channelDTO)).pipe(map(this.toChannelResponse));
    }

    deregisterUsersFromChannel(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        return this.channelApi.deregisterUsers(courseId, channelId, logins ?? this.selfLogins());
    }

    registerUsersToChannel(
        courseId: number,
        channelId: number,
        addAllStudents = false,
        addAllTutors = false,
        addAllInstructors = false,
        logins?: string[],
    ): Observable<HttpResponse<void>> {
        return this.channelApi.registerUsersToChannel(
            courseId,
            channelId,
            addAllStudents || undefined,
            addAllTutors || undefined,
            addAllInstructors || undefined,
            logins ?? this.selfLogins(),
        );
    }

    grantChannelModeratorRole(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        return this.channelApi.grantChannelModeratorRole(courseId, channelId, logins ?? this.selfLogins());
    }

    revokeChannelModeratorRole(courseId: number, channelId: number, logins?: string[]): Observable<HttpResponse<void>> {
        return this.channelApi.revokeChannelModeratorRole(courseId, channelId, logins ?? this.selfLogins());
    }

    /**
     * Toggles the privacy status of a channel (if public, it becomes private; if private, it becomes public).
     *
     * @param courseId The ID of the course the channel belongs to.
     * @param channelId The ID of the channel whose privacy status will be changed.
     */
    toggleChannelPrivacy(courseId: number, channelId: number): Observable<HttpResponse<ChannelDTO>> {
        return this.channelApi.toggleChannelPrivacy(courseId, channelId).pipe(map(this.toChannelResponse));
    }

    private toChannelResponse(response: HttpResponse<Channel>): HttpResponse<ChannelDTO> {
        return response.clone({ body: response.body && toChannelDTO(response.body) });
    }

    private toChannelListResponse(response: HttpResponse<Channel[]>): HttpResponse<ChannelDTO[]> {
        return response.clone({ body: response.body && response.body.map(toChannelDTO) });
    }

    private selfLogins(): string[] {
        // The authenticated UI has a login. Without one, there is no user to add or remove.
        const login = this.accountService.userIdentity()?.login;
        return login === undefined ? [] : [login];
    }
}
