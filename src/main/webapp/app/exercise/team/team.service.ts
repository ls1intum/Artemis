import { HttpResponse } from '@angular/common/http';
import { OnDestroy, Service, inject } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentWithTeam, Team, TeamAssignmentPayload, TeamImportStrategyType } from 'app/exercise/shared/entities/team/team.model';
import { TeamSearchUser } from 'app/exercise/shared/entities/team/team-search-user.model';
import { downloadFile } from 'app/foundation/util/download.util';
import { Observable, Subscription } from 'rxjs';
import { filter, map, shareReplay } from 'rxjs/operators';
import { TeamApi } from 'app/openapi/api/team-api';
import { CourseWithTeamExercises } from 'app/openapi/model/course-with-team-exercises';
import { fromCourseWithTeamExercises, fromTeamResponse, fromTeamSearchUser, toTeamImport, toTeamInput } from './team-api.mapper';

export type TeamOverviewPurpose = 'student' | 'staff';

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
    delete(exercise: Exercise, teamId: number): Observable<HttpResponse<void>>;

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
     * @param {TeamImportStrategyType} importStrategyType - How the teams should be imported
     */
    importTeams(exercise: Exercise, teams: Team[], importStrategyType: TeamImportStrategyType): Observable<HttpResponse<Team[]>>;

    /**
     * Finds a course with all its team exercises and participations in which the given team exists
     * @param {Course} course - Course which to find
     * @param {Team} team - Team for which to find exercises and participations (by team short name)
     */
    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team, purpose?: TeamOverviewPurpose): Observable<HttpResponse<Course>>;

    /**
     * Exports given teams into json file
     * @param teams Teams to add to export file
     */
    exportTeams(teams: Team[]): void;
}

@Service()
export class TeamService implements ITeamService, OnDestroy {
    private teamApi = inject(TeamApi);
    private websocketService = inject(WebsocketService);
    private accountService = inject(AccountService);

    // Team Assignment Update Stream
    private teamAssignmentUpdates$?: Observable<TeamAssignmentPayload>;
    private teamAssignmentUpdatesResolver!: () => void; // assigned in teamAssignmentUpdates getter before the websocket callback invokes it
    private authenticationStateSubscriber!: Subscription; // assigned in teamAssignmentUpdates getter before the auth-state callback unsubscribes it
    private websocketStatusSubscription?: Subscription;

    ngOnDestroy(): void {
        this.websocketStatusSubscription?.unsubscribe();
    }

    /**
     * Create a team for a given exercise
     * @param {Exercise} exercise - Exercise to create the team for
     * @param {Team} team - Team to create
     */
    create(exercise: Exercise, team: Team): Observable<TeamResponse> {
        return this.teamApi.createTeam(exercise.id!, toTeamInput(team)).pipe(map((dto) => this.response(fromTeamResponse(dto), 201)));
    }

    /**
     * Update a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {Team} team - Team to update
     */
    update(exercise: Exercise, team: Team): Observable<TeamResponse> {
        return this.teamApi.updateTeam(exercise.id!, team.id!, toTeamInput(team)).pipe(map((dto) => this.response(fromTeamResponse(dto))));
    }

    /**
     * Get a team belonging to a given exercise
     * @param {Exercise} exercise - Exercise to search through
     * @param {number} teamId - Team to search for
     */
    find(exercise: Exercise, teamId: number): Observable<TeamResponse> {
        return this.teamApi.getTeam(exercise.id!, teamId).pipe(map((dto) => this.response(fromTeamResponse(dto))));
    }

    /**
     * Get all teams belonging to a given exercise
     * @param {number} exerciseId - Exercise to search through
     * @param {number} teamOwnerId - Optional team owner id by which to filter exercises
     */
    findAllByExerciseId(exerciseId: number, teamOwnerId?: number): Observable<TeamArrayResponse> {
        return this.teamApi.getTeamsForExercise(exerciseId, teamOwnerId || undefined).pipe(map((teams) => this.response(teams.map(fromTeamResponse))));
    }

    /**
     * Delete a team
     * @param {Exercise} exercise - Exercise the team belongs to
     * @param {number} teamId - Team to delete
     */
    delete(exercise: Exercise, teamId: number): Observable<HttpResponse<void>> {
        return this.teamApi.deleteTeam(exercise.id!, teamId).pipe(map(() => this.response<void>()));
    }

    /**
     * Check if a given team short name exists in a course
     * @param {Course} course - Course in which to search
     * @param {string} shortName - Short name to search for
     */
    existsByShortName(course: Course, shortName: string): Observable<HttpResponse<boolean>> {
        return this.teamApi.existsTeamByShortName(course.id!, shortName).pipe(map((exists) => this.response(exists)));
    }

    /**
     * Find the team of a login/name in a given exercise and course
     * @param {Course} course - Course to search in
     * @param {Exercise} exercise - Exercise to search in
     * @param {string} loginOrName - Login/Name to search for
     */
    searchInCourseForExerciseTeam(course: Course, exercise: Exercise, loginOrName: string): Observable<HttpResponse<TeamSearchUser[]>> {
        return this.teamApi.searchTeamInExercise(course.id!, exercise.id!, loginOrName).pipe(map((users) => this.response(users.map(fromTeamSearchUser))));
    }

    /**
     * Import the teams of an existing source exercise
     * @param {Exercise} exercise - Exercise the teams should be imported into
     * @param {Team[]} teams - Teams that should be imported into the exercise
     */
    importTeams(exercise: Exercise, teams: Team[], importStrategyType: TeamImportStrategyType) {
        return this.teamApi.importTeamsFromList(exercise.id!, importStrategyType, teams.map(toTeamImport)).pipe(map((dtos) => this.response(dtos.map(fromTeamResponse))));
    }

    /**
     * Import the teams of an existing source exercise
     * @param {Exercise} exercise - Exercise the teams should be imported into
     * @param {Exercise} sourceExercise - Exercise the teams should be imported from
     * @param {TeamImportStrategyType} importStrategyType - Strategy to use for the import
     */
    importTeamsFromSourceExercise(exercise: Exercise, sourceExercise: Exercise, importStrategyType: TeamImportStrategyType) {
        return this.teamApi.importTeamsFromSourceExercise(exercise.id!, sourceExercise.id!, importStrategyType).pipe(map((dtos) => this.response(dtos.map(fromTeamResponse))));
    }

    /**
     * Finds a course with all its team exercises and participations in which the given team exists
     * @param {Course} course - Course which to find
     * @param {Team} team - Team for which to find exercises and participations (by team short name)
     */
    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team, purpose: TeamOverviewPurpose = 'student'): Observable<HttpResponse<Course>> {
        let overview: Observable<CourseWithTeamExercises>;
        switch (purpose) {
            case 'student':
                overview = this.teamApi.getCourseWithExercisesAndParticipationsForTeam(course.id!, team.shortName!);
                break;
            case 'staff':
                overview = this.teamApi.getCourseWithExercisesAndParticipationsForTeamForStaff(course.id!, team.shortName!);
                break;
            default:
                purpose satisfies never;
                throw new Error('Unsupported team overview purpose');
        }
        return overview.pipe(
            map((dto) => {
                const course = fromCourseWithTeamExercises(dto);
                this.accountService.setAccessRightsForCourse(course);
                return this.response(course);
            }),
        );
    }

    /**
     * Exports given teams into json file
     * @param teams Teams to add to export file
     */
    exportTeams(teams: Team[]) {
        // Make list of teams which we need to export,
        const exportedTeams: StudentWithTeam[] = [];
        teams.forEach((team) => {
            if (team.students) {
                team.students.forEach((student) => {
                    const exportStudent: StudentWithTeam = {
                        teamName: team.name ?? '',
                        username: student.login ?? '',
                    };
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
                        this.websocketStatusSubscription?.unsubscribe();
                        this.teamAssignmentUpdates$ = undefined;
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
        this.teamAssignmentUpdates$ = this.websocketService
            .subscribe<TeamAssignmentPayload>(teamAssignmentUpdatesWebsocketTopic)
            .pipe(shareReplay({ bufferSize: 16, refCount: true }));
        return this.teamAssignmentUpdates$;
    }

    // Callers read the body. The notification interceptor has already handled the server's alert headers.
    private response<T>(body?: T, status = 200): HttpResponse<T> {
        return new HttpResponse({ body, status });
    }
}
