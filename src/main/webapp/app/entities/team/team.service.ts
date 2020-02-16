import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { Team } from 'app/entities/team/team.model';
import { Exercise } from 'app/entities/exercise';

export type TeamResponse = HttpResponse<Team>;

export interface ITeamService {
    create(exercise: Exercise, team: Team): Observable<TeamResponse>;

    update(team: Team): Observable<TeamResponse>;

    find(id: number): Observable<TeamResponse>;

    findAllByExerciseId(exerciseId: number): Observable<HttpResponse<Team[]>>;

    delete(id: number): Observable<HttpResponse<any>>;
}

@Injectable({ providedIn: 'root' })
export class TeamService implements ITeamService {
    public resourceUrl = SERVER_API_URL + 'api/teams';

    constructor(protected http: HttpClient) {}

    create(exercise: Exercise, team: Team): Observable<TeamResponse> {
        return this.http.post<Team>(`api/exercises/${exercise.id}/teams`, team, { observe: 'response' });
    }

    update(team: Team): Observable<TeamResponse> {
        return this.http.put<Team>(this.resourceUrl, team, { observe: 'response' });
    }

    existsByShortName(shortName: string): Observable<HttpResponse<boolean>> {
        return this.http.get<boolean>(`${this.resourceUrl}?shortName=${shortName}`, { observe: 'response' });
    }

    find(id: number): Observable<TeamResponse> {
        return this.http.get<Team>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    findAllByExerciseId(exerciseId: number): Observable<HttpResponse<Team[]>> {
        return this.http.get<Team[]>(`api/exercises/${exerciseId}/teams`, { observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
