import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, OnDestroy } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { StudentWithTeam, Team, TeamAssignmentPayload, TeamImportStrategyType } from 'app/entities/team.model';
import { TeamSearchUser } from 'app/entities/team-search-user.model';
import { downloadFile } from 'app/shared/util/download.util';
import { createRequestOption } from 'app/shared/util/request.util';
import { Observable, Subscription } from 'rxjs';
import { filter, map, shareReplay } from 'rxjs/operators';
import { EntityResponseType } from 'app/exercises/shared/exercise/exercise.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

export type TeamResponse = HttpResponse<Team>;
export type TeamArrayResponse = HttpResponse<Team[]>;

const teamAssignmentUpdatesWebsocketTopic = '/user/topic/team-assignments';

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
     * @param {number} teamOwnerId - Optional team owner id by which to filter exercises
     */
    findAllByExerciseId(exerciseId: number, teamOwnerId?: number): Observable<HttpResponse<Team[]>>;

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

    /**
     * Import the teams of an existing source exercise
     * @param {Exercise} exercise - Exercise the teams should be imported into
     * @param {Team[]} teams - Teams that should be imported into the exercise
     */
    importTeams(exercise: Exercise, teams: Team[], importStrategyType: TeamImportStrategyType): Observable<HttpResponse<Team[]>>;

    /**
     * Finds a course with all its team exercises and participations in which the given team exists
     * @param {Course} course - Course which to find
     * @param {Team} team - Team for which to find exercises and participations (by team short name)
     */
    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team): Observable<HttpResponse<Course>>;

    /**
     * Exports given teams into json file
     * @param teams Teams to add to export file
     */
    exportTeams(teams: Team[]): void;
}

@Injectable({ providedIn: 'root' })
export class TeamService implements ITeamService, OnDestroy {
    // Team Assignment Update Stream
    private teamAssignmentUpdates$: Observable<TeamAssignmentPayload> | null;
    private teamAssignmentUpdatesResolver: () => void;
    private authenticationStateSubscriber: Subscription;
    private websocketStatusSubscription?: Subscription;

    constructor(protected http: HttpClient, private websocketService: JhiWebsocketService, private accountService: AccountService) {}

    ngOnDestroy(): void {
        this.websocketStatusSubscription?.unsubscribe();
    }

    static resourceUrl(exerciseId: number) {
        return `${SERVER_API_URL}api/exercises/${exerciseId}/teams`;
    }

    /**
     * Create a team for a given exercise
     * @param {Exercise} exercise - Exercise to create the team for
     * @param {Team} team - Team to create
     */
    create(exercise: Exercise, team: Team): Observable<TeamResponse> {
        const copy = TeamService.convertTeamDatesFromClient(team);
        return this.http
            .post<Team>(TeamService.resourceUrl(exercise.id!), copy, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertTeamResponseDatesFromServer(res)));
    }

    /**
     * Update a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {Team} team - Team to update
     */
    update(exercise: Exercise, team: Team): Observable<TeamResponse> {
        const copy = TeamService.convertTeamDatesFromClient(team);
        return this.http
            .put<Team>(`${TeamService.resourceUrl(exercise.id!)}/${team.id}`, copy, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertTeamResponseDatesFromServer(res)));
    }

    /**
     * Get a team belonging to a given exercise
     * @param {Exercise} exercise - Exercise to search through
     * @param {number} teamId - Team to search for
     */
    find(exercise: Exercise, teamId: number): Observable<TeamResponse> {
        return this.http
            .get<Team>(`${TeamService.resourceUrl(exercise.id!)}/${teamId}`, { observe: 'response' })
            .pipe(map((res: TeamResponse) => TeamService.convertTeamResponseDatesFromServer(res)));
    }

    /**
     * Get all teams belonging to a given exercise
     * @param {number} exerciseId - Exercise to search through
     * @param {number} teamOwnerId - Optional team owner id by which to filter exercises
     */
    findAllByExerciseId(exerciseId: number, teamOwnerId?: number): Observable<TeamArrayResponse> {
        const options = createRequestOption(teamOwnerId ? { teamOwnerId } : {});
        return this.http
            .get<Team[]>(TeamService.resourceUrl(exerciseId), {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: TeamArrayResponse) => TeamService.convertTeamArrayResponseDatesFromServer(res)));
    }

    /**
     * Delete a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {number} teamId - Team to delete
     */
    delete(exercise: Exercise, teamId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${TeamService.resourceUrl(exercise.id!)}/${teamId}`, { observe: 'response' });
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
     * @param {Team[]} teams - Teams that should be imported into the exercise
     */
    importTeams(exercise: Exercise, teams: Team[], importStrategyType: TeamImportStrategyType) {
        const copy = teams.map((team) => TeamService.convertTeamDatesFromClient(team));
        return this.http.put<Team[]>(`${TeamService.resourceUrl(exercise.id!)}/import-from-list?importStrategyType=${importStrategyType}`, copy, {
            observe: 'response',
        });
    }

    /**
     * Import the teams of an existing source exercise
     * @param {Exercise} exercise - Exercise the teams should be imported into
     * @param {Exercise} sourceExercise - Exercise the teams should be imported from
     * @param {TeamImportStrategyType} importStrategyType - Strategy to use for the import
     */
    importTeamsFromSourceExercise(exercise: Exercise, sourceExercise: Exercise, importStrategyType: TeamImportStrategyType) {
        return this.http.put<Team[]>(
            `${TeamService.resourceUrl(exercise.id!)}/import-from-exercise/${sourceExercise.id}?importStrategyType=${importStrategyType}`,
            {},
            { observe: 'response' },
        );
    }

    /**
     * Finds a course with all its team exercises and participations in which the given team exists
     * @param {Course} course - Course which to find
     * @param {Team} team - Team for which to find exercises and participations (by team short name)
     */
    // TODO: Move this method to the CourseManagementService and delete the only here used duplicated setAccessRightsCourseEntityResponseType() helper method
    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team): Observable<HttpResponse<Course>> {
        return this.http
            .get<Course>(`${SERVER_API_URL}api/courses/${course.id}/teams/${team.shortName}/with-exercises-and-participations`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.setAccessRightsCourseEntityResponseType(res)));
    }

    /**
     * Exports given teams into json file
     * @param teams Teams to add to export file
     */
    exportTeams(teams: Team[]) {
        // Make list of teams which we need to export,
        const exportedTeams: StudentWithTeam[] = [];
        teams!.forEach((team) => {
            if (team.students) {
                team.students.forEach((student) => {
                    const exportStudent = {
                        teamName: team.name ?? '',
                        username: student.login ?? '',
                    } as StudentWithTeam;
                    if (student.firstName) {
                        exportStudent.firstName = student.firstName;
                    }
                    if (student.lastName) {
                        exportStudent.lastName = student.lastName;
                    }
                    if (student.visibleRegistrationNumber) {
                        exportStudent.registrationNumber = student.visibleRegistrationNumber;
                    }
                    exportedTeams.push(exportStudent);
                });
            }
        });
        // Make blob from the list of teams and download the file,
        const teamJson = JSON.stringify(exportedTeams);
        const blob = new Blob([teamJson], { type: 'application/json' });
        downloadFile(blob, 'teams.json');
    }

    /**
     * Returns a promise of an observable that emits team assignment updates
     *
     * 1. If there is already an update stream, return it
     * 2. If there is no update stream yet, wait for the user to log in and create a new update stream
     */
    get teamAssignmentUpdates(): Promise<Observable<TeamAssignmentPayload>> {
        return new Promise((resolve) => {
            if (this.teamAssignmentUpdates$) {
                return resolve(this.teamAssignmentUpdates$);
            }
            this.authenticationStateSubscriber = this.accountService.getAuthenticationState().subscribe((user: User | undefined) => {
                setTimeout(() => {
                    if (user) {
                        this.teamAssignmentUpdatesResolver = () => resolve(this.newTeamAssignmentUpdates$);
                        this.websocketStatusSubscription = this.websocketService.connectionState
                            .pipe(filter((status) => status.connected))
                            .subscribe(() => this.teamAssignmentUpdatesResolver());
                    } else {
                        this.websocketService.unsubscribe(teamAssignmentUpdatesWebsocketTopic);
                        this.websocketStatusSubscription?.unsubscribe();
                        this.teamAssignmentUpdates$ = null;
                        this.authenticationStateSubscriber.unsubscribe();
                    }
                }, 500);
            });
        });
    }

    /**
     * Subscribes to the team assignment updates websocket topic and stores the stream in teamAssignmentUpdates$
     */
    private get newTeamAssignmentUpdates$(): Observable<TeamAssignmentPayload> {
        this.websocketService.subscribe(teamAssignmentUpdatesWebsocketTopic);
        this.teamAssignmentUpdates$ = this.websocketService.receive(teamAssignmentUpdatesWebsocketTopic).pipe(shareReplay(16));
        return this.teamAssignmentUpdates$;
    }

    /**
     * Helper methods for date conversion from server and client
     */
    private static convertTeamArrayResponseDatesFromServer(res: TeamArrayResponse): TeamArrayResponse {
        if (res.body) {
            res.body.map((team: Team) => this.convertTeamDatesFromServer(team));
        }
        return res;
    }

    private static convertTeamResponseDatesFromServer(res: TeamResponse): TeamResponse {
        if (res.body) {
            res.body.createdDate = convertDateFromServer(res.body.createdDate);
            res.body.lastModifiedDate = convertDateFromServer(res.body.lastModifiedDate);
        }
        return res;
    }

    private static convertTeamDatesFromServer(team: Team): Team {
        team.createdDate = convertDateFromServer(team.createdDate);
        team.lastModifiedDate = convertDateFromServer(team.lastModifiedDate);
        return team;
    }

    private static convertTeamDatesFromClient(team: Team): Team {
        return Object.assign({}, team, {
            createdDate: convertDateFromClient(team.createdDate),
            lastModifiedDate: convertDateFromClient(team.lastModifiedDate),
        });
    }

    private setAccessRightsCourseEntityResponseType(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.accountService.setAccessRightsForCourse(res.body);
        }
        return res;
    }
}
