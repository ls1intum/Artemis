import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ITeamService } from 'app/exercises/shared/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { Team } from 'app/entities/team.model';
import { Course } from 'app/entities/course.model';
import { TeamSearchUser } from 'app/entities/team-search-student.model';
import { User } from 'app/core/user/user.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';

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

export const mockTeamSearchUsers = [...mockTeamStudents, ...mockNonTeamStudents].map(student => ({
    ...student,
    assignedToTeam: mockTeamStudents.includes(student),
})) as TeamSearchUser[];

const teamAssignmentConfig = { minTeamSize: 1, maxTeamSize: 5 } as TeamAssignmentConfig; // note: size of mockTeamStudents above should conform

export const mockExercise = { id: 1, title: 'Programming exercise', teamAssignmentConfig } as ProgrammingExercise;

export const mockEmptyTeam = ({
    students: [],
} as unknown) as Team;

export const mockTeam = {
    id: 1,
    name: 'Team 1',
    shortName: 'team1',
    exercise: mockExercise,
    students: mockTeamStudents,
} as Team;

export const mockShortNames = {
    existing: 'team1',
    nonExisting: 'team2',
};

export class MockTeamService implements ITeamService {
    create(exercise: Exercise, team: Team) {
        return MockTeamService.response({ ...team, id: 1 });
    }

    update(exercise: Exercise, team: Team) {
        return MockTeamService.response(team);
    }

    find(exercise: Exercise, teamId: number) {
        return MockTeamService.response({ ...mockTeam, exercise });
    }

    findAllByExerciseId(exerciseId: number) {
        return MockTeamService.response([{ ...mockTeam, exercise: { ...mockExercise, id: exerciseId } }]);
    }

    delete(exercise: Exercise, teamId: number) {
        return MockTeamService.response({});
    }

    existsByShortName(shortName: string) {
        return MockTeamService.response(shortName === mockShortNames.existing);
    }

    searchInCourseForExerciseTeam(course: Course, exercise: Exercise, loginOrName: string) {
        return MockTeamService.response(mockTeamSearchUsers);
    }

    // helper method
    private static response<T>(entity: T) {
        return of({ body: entity }) as Observable<HttpResponse<T>>;
    }
}
