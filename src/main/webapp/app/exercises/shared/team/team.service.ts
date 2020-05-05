import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import * as moment from 'moment';

import { SERVER_API_URL } from 'app/app.constants';
import { Team, TeamImportStrategyType } from 'app/entities/team.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { TeamSearchUser } from 'app/entities/team-search-user.model';

export type TeamResponse = HttpResponse<Team>;
export type TeamArrayResponse = HttpResponse<Team[]>;

export interface ITeamService {
    /**
     * Create a team for a given exercise
     * @param {Exercise} exercise - Exercise to create the team for
     * @param {Team} team - Team to create
     */
    create(exercise: Exercise, team: Team): Observable<TeamResponse>;

    /**
     * Update a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {Team} team - Team to update
     */
    update(exercise: Exercise, team: Team): Observable<TeamResponse>;

    /**
     * Get a team belonging to a given exercise
     * @param {Exercise} exercise - Exercise to search through
     * @param {number} teamId - Team to search for
     */
    find(exercise: Exercise, teamId: number): Observable<TeamResponse>;

    /**
     * Get all teams belonging to a given exercise
     * @param {number} exerciseId - Exercise to search through
     */
    findAllByExerciseId(exerciseId: number): Observable<HttpResponse<Team[]>>;

    /**
     * Delete a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {number} teamId - Team to delete
     */
    delete(exercise: Exercise, teamId: number): Observable<HttpResponse<any>>;

    /**
     * Check if a given team short name exists in a course
     * @param {Course} course - Course in which to search
     * @param {string} shortName - Short name to search for
     */
    existsByShortName(course: Course, shortName: string): Observable<HttpResponse<boolean>>;

    /**
     * Find the team of a login/name in a given exercise and course
     * @param {Course} course - Course to search in
     * @param {Exercise} exercise - Exercise to search in
     * @param {string} loginOrName - Login/Name to search for
     */
    searchInCourseForExerciseTeam(course: Course, exercise: Exercise, loginOrName: string): Observable<HttpResponse<TeamSearchUser[]>>;

    /**
     * Import the teams of an existing source exercise
     * @param {Exercise} exercise - Exercise the teams should be imported into
     * @param {Exercise} sourceExercise - Exercise the teams should be imported from
     * @param {TeamImportStrategyType} importStrategyType - Strategy to use for the import
     */
    importTeamsFromSourceExercise(exercise: Exercise, sourceExercise: Exercise, importStrategyType: TeamImportStrategyType): Observable<HttpResponse<Team[]>>;

    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team): Observable<HttpResponse<Course>>;
}

@Injectable({ providedIn: 'root' })
export class TeamService implements ITeamService {
    constructor(protected http: HttpClient) {}

    private static resourceUrl(exerciseId: number) {
        return `${SERVER_API_URL}api/exercises/${exerciseId}/teams`;
    }

    /**
     * Create a team for a given exercise
     * @param {Exercise} exercise - Exercise to create the team for
     * @param {Team} team - Team to create
     */
    create(exercise: Exercise, team: Team): Observable<TeamResponse> {
        const copy = TeamService.convertDateFromClient(team);
        return this.http
            .post<Team>(TeamService.resourceUrl(exercise.id), copy, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertDateFromServer(res)));
    }

    /**
     * Update a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {Team} team - Team to update
     */
    update(exercise: Exercise, team: Team): Observable<TeamResponse> {
        const copy = TeamService.convertDateFromClient(team);
        return this.http
            .put<Team>(`${TeamService.resourceUrl(exercise.id)}/${team.id}`, copy, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertDateFromServer(res)));
    }

    /**
     * Get a team belonging to a given exercise
     * @param {Exercise} exercise - Exercise to search through
     * @param {number} teamId - Team to search for
     */
    find(exercise: Exercise, teamId: number): Observable<TeamResponse> {
        return this.http
            .get<Team>(`${TeamService.resourceUrl(exercise.id)}/${teamId}`, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertDateFromServer(res)));
    }

    /**
     * Get all teams belonging to a given exercise
     * @param {number} exerciseId - Exercise to search through
     */
    findAllByExerciseId(exerciseId: number): Observable<TeamArrayResponse> {
        return this.http
            .get<Team[]>(TeamService.resourceUrl(exerciseId), { observe: 'response' })
            .pipe(map((res: TeamArrayResponse) => TeamService.convertDateArrayFromServer(res)));
    }

    /**
     * Delete a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {number} teamId - Team to delete
     */
    delete(exercise: Exercise, teamId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${TeamService.resourceUrl(exercise.id)}/${teamId}`, { observe: 'response' });
    }

    /**
     * Check if a given team short name exists in a course
     * @param {Course} course - Course in which to search
     * @param {string} shortName - Short name to search for
     */
    existsByShortName(course: Course, shortName: string): Observable<HttpResponse<boolean>> {
        return this.http.get<boolean>(`${SERVER_API_URL}api/courses/${course.id}/teams/exists?shortName=${shortName}`, { observe: 'response' });
    }

    /**
     * Find the team of a login/name in a given exercise and course
     * @param {Course} course - Course to search in
     * @param {Exercise} exercise - Exercise to search in
     * @param {string} loginOrName - Login/Name to search for
     */
    searchInCourseForExerciseTeam(course: Course, exercise: Exercise, loginOrName: string): Observable<HttpResponse<TeamSearchUser[]>> {
        const url = `${SERVER_API_URL}api/courses/${course.id}/exercises/${exercise.id}/team-search-users?loginOrName=${loginOrName}`;
        return this.http.get<TeamSearchUser[]>(url, { observe: 'response' });
    }

    /**
     * Import the teams of an existing source exercise
     * @param {Exercise} exercise - Exercise the teams should be imported into
     * @param {Exercise} sourceExercise - Exercise the teams should be imported from
     * @param {TeamImportStrategyType} importStrategyType - Strategy to use for the import
     */
    importTeamsFromSourceExercise(exercise: Exercise, sourceExercise: Exercise, importStrategyType: TeamImportStrategyType) {
        return this.http.put<Team[]>(
            `${TeamService.resourceUrl(exercise.id)}/import-from-exercise/${sourceExercise.id}?importStrategyType=${importStrategyType}`,
            {},
            { observe: 'response' },
        );
    }

    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team): Observable<HttpResponse<Course>> {
        return this.http.get<Course>(`${SERVER_API_URL}api/courses/${course.id}/teams/${team.shortName}/with-exercises-and-participations`, { observe: 'response' });
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
