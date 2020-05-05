import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ITeamService } from 'app/exercises/shared/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { Team, TeamImportStrategyType } from 'app/entities/team.model';
import { Course } from 'app/entities/course.model';
import { TeamSearchUser } from 'app/entities/team-search-user.model';
import { User } from 'app/core/user/user.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';
import * as moment from 'moment';

export const mockTeamStudents = [
    { id: 1, firstName: 'John', lastName: 'Doe', name: 'John Doe', login: 'ga12abc', email: 'john.doe@example.com' },
    { id: 2, firstName: 'Sidney', lastName: 'Humphrey', name: 'Sidney Humphrey', login: 'ga23bcd', email: 'sidney.humphrey@example.com' },
    { id: 3, firstName: 'Elane', lastName: 'Cravens', name: 'Elane Cravens', login: 'ga34cde', email: 'elane.cravens@example.com' },
] as User[];

export const mockNonTeamStudents = [
    { id: 4, firstName: 'Ned', lastName: 'Atkins', name: 'Ned Atkins', login: 'ga45def', email: 'ned.atkins@example.com' },
    { id: 5, firstName: 'Debbie', lastName: 'Roman', name: 'Debbie Roman', login: 'ga56efg', email: 'debbie.roman@example.com' },
    { id: 6, firstName: 'Jeffrey', lastName: 'McGill', name: 'Jeffrey McGill', login: 'ga67ghi', email: 'jeffrey.mcgill@example.com' },
] as User[];

const teamAssignmentConfig = { minTeamSize: 1, maxTeamSize: 5 } as TeamAssignmentConfig; // note: size of mockTeamStudents above should conform

const mockCourse = { id: 1 };

export const mockExercise = { id: 1, title: 'Programming exercise', teamAssignmentConfig, course: mockCourse } as ProgrammingExercise;

export const mockEmptyTeam = ({
    students: [],
} as unknown) as Team;

export const mockTeam = {
    id: 1,
    name: 'Team 1',
    shortName: 'team1',
    students: mockTeamStudents,
    owner: { id: 1 } as User,
    createdBy: 'tutor1',
    createdDate: moment(),
} as Team;

export const mockTeams = [
    mockTeam,
    { id: 2, name: 'Team 2', shortName: 'team2', students: [], owner: { id: 1 } as User, createdBy: 'tutor1', createdDate: moment() } as Team,
    { id: 3, name: 'Team 3', shortName: 'team3', students: [], owner: { id: 1 } as User, createdBy: 'tutor1', createdDate: moment() } as Team,
];

export const mockShortNames = {
    existing: 'team1',
    nonExisting: 'team2',
};

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

    findAllByExerciseId(exerciseId: number) {
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

    findCourseWithExercisesAndParticipationsForTeam(course: Course, team: Team): Observable<HttpResponse<Course>> {
        return MockTeamService.response({ ...mockCourse, exercises: [{ ...(mockExercise as Exercise), teams: [mockTeam] }] } as Course);
    }

    // helper method
    private static response<T>(entity: T) {
        return of({ body: entity }) as Observable<HttpResponse<T>>;
    }
}
