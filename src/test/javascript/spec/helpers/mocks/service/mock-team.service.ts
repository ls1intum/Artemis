import { Observable, of } from 'rxjs';
import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { ITeamService } from 'app/exercises/shared/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { Team, TeamImportStrategyType } from 'app/entities/team.model';
import { StudentWithTeam } from 'app/entities/team.model';
import { Course } from 'app/entities/course.model';
import { TeamSearchUser } from 'app/entities/team-search-user.model';
import { User } from 'app/core/user/user.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import dayjs from 'dayjs/esm';

export const mockTeamStudents = [
    { id: 1, firstName: 'John', lastName: 'Doe', name: 'John Doe', login: 'ga12abc', email: 'john.doe@example.com', visibleRegistrationNumber: '01234567' },
    { id: 2, firstName: 'Sidney', lastName: 'Humphrey', name: 'Sidney Humphrey', login: 'ga23bcd', email: 'sidney.humphrey@example.com', visibleRegistrationNumber: '01234568' },
    { id: 3, firstName: 'Elane', lastName: 'Cravens', name: 'Elane Cravens', login: 'ga34cde', email: 'elane.cravens@example.com', visibleRegistrationNumber: '01234569' },
] as User[];

export const mockNonTeamStudents = [
    { id: 4, firstName: 'Ned', lastName: 'Atkins', name: 'Ned Atkins', login: 'ga45def', email: 'ned.atkins@example.com' },
    { id: 5, firstName: 'Debbie', lastName: 'Roman', name: 'Debbie Roman', login: 'ga56efg', email: 'debbie.roman@example.com' },
    { id: 6, firstName: 'Jeffrey', lastName: 'McGill', name: 'Jeffrey McGill', login: 'ga67ghi', email: 'jeffrey.mcgill@example.com' },
] as User[];

export const mockSourceTeamStudents = [
    { id: 7, firstName: 'Alice', lastName: 'McCarthy', name: 'Alice McCarthy', login: 'ga78abc', email: 'alice.mccarthy@example.com', visibleRegistrationNumber: '11234567' },
    { id: 8, firstName: 'Lena', lastName: 'Dudley', name: 'Lena Dudley', login: 'ga89bcd', email: 'lena.dudley@example.com', visibleRegistrationNumber: '11234568' },
    { id: 9, firstName: 'Thomas', lastName: 'Smith', name: 'Thomas Smith', login: 'ga90cde', email: 'thomas.smith@example.com', visibleRegistrationNumber: '11234569' },
] as User[];

const teamAssignmentConfig = { minTeamSize: 1, maxTeamSize: 5 } as TeamAssignmentConfig; // note: size of mockTeamStudents above should conform

const mockCourse = { id: 1 };

export const mockExercise = { id: 1, title: 'Programming exercise', teamAssignmentConfig, course: mockCourse } as ProgrammingExercise;
export const mockSourceExercise = { id: 2, title: 'Source Programming exercise', teamAssignmentConfig, course: mockCourse } as ProgrammingExercise;

export const mockEmptyTeam = {
    students: [],
} as unknown as Team;

const mockTeamFromServer = {
    id: 1,
    name: 'Team 1',
    shortName: 'team1',
    students: mockTeamStudents,
    owner: { id: 1 } as User,
    createdBy: 'tutor1',
    createdDate: new Date(),
    lastModifiedDate: new Date(),
};

export const mockTeam = {
    id: 1,
    name: 'Team 1',
    shortName: 'team1',
    students: mockTeamStudents,
    owner: { id: 1 } as User,
    createdBy: 'tutor1',
    createdDate: dayjs(mockTeamFromServer.createdDate),
    lastModifiedDate: dayjs(mockTeamFromServer.lastModifiedDate),
} as Team;

export const mockTeams = [
    mockTeam,
    { id: 2, name: 'Team 2', shortName: 'team2', students: [], owner: { id: 1 } as User, createdBy: 'tutor1', createdDate: dayjs() } as Team,
    { id: 3, name: 'Team 3', shortName: 'team3', students: [], owner: { id: 1 } as User, createdBy: 'tutor1', createdDate: dayjs() } as Team,
];

export const mockSourceTeam = {
    id: 1,
    name: 'Team 4',
    shortName: 'team4',
    students: mockSourceTeamStudents,
    owner: { id: 1 } as User,
    createdBy: 'tutor1',
    createdDate: dayjs(),
} as Team;

export const mockSourceTeams = [
    mockSourceTeam,
    { id: 2, name: 'Team 5', shortName: 'team5', students: [], owner: { id: 1 } as User, createdBy: 'tutor1', createdDate: dayjs() } as Team,
    { id: 3, name: 'Team 6', shortName: 'team6', students: [], owner: { id: 1 } as User, createdBy: 'tutor1', createdDate: dayjs() } as Team,
];

export const mockShortNames = {
    existing: 'team1',
    nonExisting: 'team2',
};

export const mockFileStudents = [
    { firstName: 'Jack', lastName: 'Doe', username: 'jack_doe', teamName: 'File Team 1' } as StudentWithTeam,
    { firstName: 'Jackie', lastName: 'Doen', username: 'jackie_doen', teamName: 'File Team 1' } as StudentWithTeam,
    { firstName: 'Alyson', lastName: 'Smithson', registrationNumber: '23458', teamName: 'File Team 2' } as StudentWithTeam,
    { firstName: 'Alysia', lastName: 'Smith', registrationNumber: '23459', teamName: 'File Team 2' } as StudentWithTeam,
];

export const mockFileTeamsConverted: Team[] = [
    {
        ...new Team(),
        name: 'File Team 1',
        shortName: 'fileteam1',
        students: [
            { ...new User(), firstName: 'Jack', lastName: 'Doe', login: 'jack_doe', name: 'Jack Doe', visibleRegistrationNumber: undefined } as User,
            { ...new User(), firstName: 'Jackie', lastName: 'Doen', login: 'jackie_doen', name: 'Jackie Doen', visibleRegistrationNumber: undefined } as User,
        ],
    } as Team,
    {
        ...new Team(),
        name: 'File Team 2',
        shortName: 'fileteam2',
        students: [
            { ...new User(), firstName: 'Alyson', lastName: 'Smithson', login: undefined, visibleRegistrationNumber: '23458', name: 'Alyson Smithson' } as User,
            { ...new User(), firstName: 'Alysia', lastName: 'Smith', login: undefined, visibleRegistrationNumber: '23459', name: 'Alysia Smith' } as User,
        ],
    } as Team,
];

export const mockTeamSearchUsers = [...mockTeamStudents, ...mockNonTeamStudents].map((student) => ({
    ...student,
    assignedTeamId: mockTeamStudents.includes(student) ? mockTeam.id : null,
})) as TeamSearchUser[];

export class MockTeamService implements ITeamService {
    create(exercise: Exercise, team: Team) {
        return MockTeamService.response({ ...team, id: 1 });
    }

    update(exercise: Exercise, team: Team) {
        return MockTeamService.response(team);
    }

    find(exercise: Exercise, teamId: number) {
        return MockTeamService.response(mockTeam);
    }

    findAllByExerciseId(exerciseId: number, teamOwnerId?: number) {
        return MockTeamService.response(mockTeams);
    }

    delete(exercise: Exercise, teamId: number) {
        return MockTeamService.response({});
    }

    existsByShortName(course: Course, shortName: string) {
        return MockTeamService.response(shortName === mockShortNames.existing);
    }

    searchInCourseForExerciseTeam(course: Course, exercise: Exercise, loginOrName: string) {
        return MockTeamService.response(mockTeamSearchUsers);
    }

    importTeamsFromSourceExercise(exercise: Exercise, sourceExercise: Exercise, importStrategy: TeamImportStrategyType) {
        return MockTeamService.response(mockTeams);
    }

    importTeams(exercise: Exercise, teams: Team[], importStrategyType: TeamImportStrategyType) {
        return MockTeamService.response(mockTeams);
    }

    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team): Observable<HttpResponse<Course>> {
        return MockTeamService.response({ ...mockCourse, exercises: [{ ...(mockExercise as Exercise), teams: [mockTeam] }] } as Course);
    }

    exportTeams(teams: Team[]) {}

    // helper method
    private static response<T>(entity: T) {
        return of({ body: entity }) as Observable<HttpResponse<T>>;
    }
}

@Injectable()
export class TeamRequestInterceptorMock implements HttpInterceptor {
    constructor() {}

    intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        if (request.url && request.url.indexOf(`${TeamService.resourceUrl(mockExercise.id!)}/${mockTeamFromServer.id}`) > -1) {
            return of(new HttpResponse({ status: 200, body: mockTeamFromServer }));
        }
        if (request.url === `api/exercises/${mockExercise.id}`) {
            return of(new HttpResponse({ status: 200, body: mockExercise }));
        }
        return next.handle(request);
    }
}
