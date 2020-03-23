import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { Team } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { TeamSearchUser } from 'app/entities/team-search-user.model';

export type TeamResponse = HttpResponse<Team>;

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
        return this.http.post<Team>(TeamService.resourceUrl(exercise.id), team, { observe: 'response' });
    }

    update(exercise: Exercise, team: Team): Observable<TeamResponse> {
        return this.http.put<Team>(`${TeamService.resourceUrl(exercise.id)}/${team.id}`, team, { observe: 'response' });
    }

    find(exercise: Exercise, teamId: number): Observable<TeamResponse> {
        return this.http.get<Team>(`${TeamService.resourceUrl(exercise.id)}/${teamId}`, { observe: 'response' });
    }

    findAllByExerciseId(exerciseId: number): Observable<HttpResponse<Team[]>> {
        return this.http.get<Team[]>(TeamService.resourceUrl(exerciseId), { observe: 'response' });
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
}
