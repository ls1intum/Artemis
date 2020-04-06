import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';

import { SERVER_API_URL } from 'app/app.constants';
import { Team } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { TeamSearchUser } from 'app/entities/team-search-user.model';

export type TeamResponse = HttpResponse<Team>;
export type TeamArrayResponse = HttpResponse<Team[]>;

export interface ITeamService {
    create(exercise: Exercise, team: Team): Observable<TeamResponse>;

    update(exercise: Exercise, team: Team): Observable<TeamResponse>;

    find(exercise: Exercise, teamId: number): Observable<TeamResponse>;

    findAllByExerciseId(exerciseId: number): Observable<HttpResponse<Team[]>>;

    delete(exercise: Exercise, teamId: number): Observable<HttpResponse<any>>;

    existsByShortName(shortName: string): Observable<HttpResponse<boolean>>;

    searchInCourseForExerciseTeam(course: Course, exercise: Exercise, loginOrName: string): Observable<HttpResponse<TeamSearchUser[]>>;
}

@Injectable({ providedIn: 'root' })
export class TeamService implements ITeamService {
    constructor(protected http: HttpClient) {}

    private static resourceUrl(exerciseId: number) {
        return `${SERVER_API_URL}api/exercises/${exerciseId}/teams`;
    }

    create(exercise: Exercise, team: Team): Observable<TeamResponse> {
        const copy = TeamService.convertDateFromClient(team);
        return this.http
            .post<Team>(TeamService.resourceUrl(exercise.id), copy, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertDateFromServer(res)));
    }

    update(exercise: Exercise, team: Team): Observable<TeamResponse> {
        const copy = TeamService.convertDateFromClient(team);
        return this.http
            .put<Team>(`${TeamService.resourceUrl(exercise.id)}/${team.id}`, copy, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertDateFromServer(res)));
    }

    find(exercise: Exercise, teamId: number): Observable<TeamResponse> {
        return this.http
            .get<Team>(`${TeamService.resourceUrl(exercise.id)}/${teamId}`, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertDateFromServer(res)));
    }

    findAllByExerciseId(exerciseId: number): Observable<TeamArrayResponse> {
        return this.http
            .get<Team[]>(TeamService.resourceUrl(exerciseId), { observe: 'response' })
            .pipe(map((res: TeamArrayResponse) => TeamService.convertDateArrayFromServer(res)));
    }

    delete(exercise: Exercise, teamId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${TeamService.resourceUrl(exercise.id)}/${teamId}`, { observe: 'response' });
    }

    existsByShortName(shortName: string): Observable<HttpResponse<boolean>> {
        return this.http.get<boolean>(`${SERVER_API_URL}api/teams?shortName=${shortName}`, { observe: 'response' });
    }

    searchInCourseForExerciseTeam(course: Course, exercise: Exercise, loginOrName: string): Observable<HttpResponse<TeamSearchUser[]>> {
        const url = `${SERVER_API_URL}api/courses/${course.id}/exercises/${exercise.id}/team-search-users?loginOrName=${loginOrName}`;
        return this.http.get<TeamSearchUser[]>(url, { observe: 'response' });
    }

    /**
     * Helper methods for date conversion from server and client
     */
    private static convertDateArrayFromServer(res: TeamArrayResponse): TeamArrayResponse {
        if (res.body) {
            res.body.map((team: Team) => this.convertDatesForTeamFromServer(team));
        }
        return res;
    }

    private static convertDateFromServer(res: TeamResponse): TeamResponse {
        if (res.body) {
            res.body.createdDate = moment(res.body.createdDate);
            res.body.lastModifiedDate = res.body.lastModifiedDate != null ? moment(res.body.lastModifiedDate) : null;
        }
        return res;
    }

    private static convertDatesForTeamFromServer(team: Team): Team {
        team.createdDate = moment(team.createdDate);
        team.lastModifiedDate = team.lastModifiedDate != null ? moment(team.lastModifiedDate) : null;
        return team;
    }

    private static convertDateFromClient(team: Team): Team {
        return Object.assign({}, team, {
            createdDate: moment(team.createdDate).isValid() ? moment(team.createdDate).toJSON() : null,
            lastModifiedDate: team.lastModifiedDate != null && moment(team.lastModifiedDate).isValid() ? moment(team.lastModifiedDate).toJSON() : null,
        });
    }
}
