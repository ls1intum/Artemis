import { User } from 'app/account/user/user.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { newExerciseModel } from 'app/course/shared/entities/course-content-response.dto';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { createSubmission } from 'app/exercise/shared/entities/participation/student-participation.dto';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission, SubmissionExerciseType, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { TeamSearchUser } from 'app/exercise/shared/entities/team/team-search-user.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { CourseWithTeamExercises } from 'app/openapi/model/course-with-team-exercises';
import { ResultOverview } from 'app/openapi/model/result-overview';
import { SubmissionOverview, SubmissionOverviewTypeEnum } from 'app/openapi/model/submission-overview';
import { Team as ApiTeam } from 'app/openapi/model/team';
import { TeamExercise, TeamExerciseTypeEnum } from 'app/openapi/model/team-exercise';
import { TeamImport } from 'app/openapi/model/team-import';
import { TeamInput } from 'app/openapi/model/team-input';
import { TeamParticipation } from 'app/openapi/model/team-participation';
import { TeamResponse } from 'app/openapi/model/team-response';
import { TeamSearchUser as ApiTeamSearchUser } from 'app/openapi/model/team-search-user';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';

export function toTeamInput(team: Team): TeamInput {
    return {
        id: team.id,
        name: team.name ?? '',
        shortName: team.shortName ?? '',
        image: team.image,
        students: team.students?.map((student) => student.id).filter((id) => id !== undefined) ?? [],
        ownerId: team.owner?.id,
    };
}

export function toTeamImport(team: Team): TeamImport {
    return {
        name: team.name ?? '',
        shortName: team.shortName ?? '',
        image: team.image,
        students: team.students?.map((student) => ({ login: student.login, visibleRegistrationNumber: student.visibleRegistrationNumber })) ?? [],
    };
}

export function fromTeamResponse(dto: TeamResponse): Team {
    const { owner, students, createdDate, lastModifiedDate, ...fields } = dto;
    return hydrate(new Team(), fields, {
        owner: owner ? hydrate(new User(), owner) : undefined,
        students: students?.map((student) => hydrate(new User(), student)),
        createdDate: convertDateStringFromServer(createdDate),
        lastModifiedDate: convertDateStringFromServer(lastModifiedDate),
    });
}

export function fromTeamSearchUser(dto: ApiTeamSearchUser): TeamSearchUser {
    return hydrate(new TeamSearchUser(), dto);
}

export function fromCourseWithTeamExercises(dto: CourseWithTeamExercises): Course {
    return hydrate(new Course(), {
        id: dto.id,
        exercises: dto.exercises?.map(fromTeamExercise).filter((exercise) => exercise !== undefined),
    });
}

function fromTeamExercise(dto: TeamExercise): Exercise | undefined {
    const { type, releaseDate, dueDate, teams, studentParticipations, ...fields } = dto;
    if (type === undefined) {
        return undefined;
    }
    return hydrate(newExerciseModel(fromExerciseType(type)), fields, {
        releaseDate: convertDateStringFromServer(releaseDate),
        dueDate: convertDateStringFromServer(dueDate),
        teams: teams?.map(fromOverviewTeam),
        studentParticipations: studentParticipations?.map(fromParticipation),
    });
}

function fromExerciseType(type: TeamExerciseTypeEnum): ExerciseType {
    switch (type) {
        case 'text':
            return ExerciseType.TEXT;
        case 'programming':
            return ExerciseType.PROGRAMMING;
        case 'modeling':
            return ExerciseType.MODELING;
        case 'file-upload':
            return ExerciseType.FILE_UPLOAD;
        case 'quiz':
            return ExerciseType.QUIZ;
    }
}

function fromOverviewTeam({ students, ...fields }: ApiTeam): Team {
    return hydrate(new Team(), fields, { students: students?.map((student) => hydrate(new User(), student)) });
}

function fromParticipation(dto: TeamParticipation): StudentParticipation {
    const { type, initializationState, initializationDate, individualDueDate, repositoryUri, team, submissions, ...fields } = dto;
    const participationType = Object.values(ParticipationType).find((value) => value === type);
    const participation =
        participationType === ParticipationType.PROGRAMMING
            ? hydrate(new ProgrammingExerciseStudentParticipation(), { repositoryUri })
            : new StudentParticipation(ParticipationType.STUDENT);
    return hydrate(participation, fields, {
        initializationState: Object.values(InitializationState).find((value) => value === initializationState),
        initializationDate: convertDateStringFromServer(initializationDate),
        individualDueDate: convertDateStringFromServer(individualDueDate),
        team: team ? fromOverviewTeam(team) : undefined,
        submissions: submissions?.map(fromSubmission).filter((submission) => submission !== undefined),
    });
}

const submissionExerciseTypes = [
    SubmissionExerciseType.PROGRAMMING,
    SubmissionExerciseType.MODELING,
    SubmissionExerciseType.QUIZ,
    SubmissionExerciseType.TEXT,
    SubmissionExerciseType.FILE_UPLOAD,
];

const submissionTypes = {
    MANUAL: SubmissionType.MANUAL,
    TIMEOUT: SubmissionType.TIMEOUT,
    TEST: SubmissionType.TEST,
    OTHER: SubmissionType.OTHER,
    INSTRUCTOR: SubmissionType.INSTRUCTOR,
    EXTERNAL: SubmissionType.EXTERNAL,
} satisfies Record<SubmissionOverviewTypeEnum, SubmissionType>;

function fromSubmission(dto: SubmissionOverview): Submission | undefined {
    const { submissionExerciseType, type, submissionDate, buildFailed, commitHash, results, ...fields } = dto;
    const exerciseType = submissionExerciseTypes.find((value) => value === submissionExerciseType);
    if (exerciseType === undefined) {
        captureException(new Error(`Unsupported team overview submission type: ${String(submissionExerciseType)}`));
        return undefined;
    }
    const submission = createSubmission(exerciseType);
    if (submission instanceof ProgrammingSubmission) {
        submission.buildFailed = buildFailed;
        submission.commitHash = commitHash;
    }
    return hydrate(submission, fields, {
        type: type && submissionTypes[type],
        submissionDate: convertDateStringFromServer(submissionDate),
        results: results?.map(fromResult),
    });
}

function fromResult({ completionDate, assessmentType, ...fields }: ResultOverview): Result {
    return hydrate(new Result(), fields, {
        completionDate: convertDateStringFromServer(completionDate),
        assessmentType: Object.values(AssessmentType).find((value) => value === assessmentType),
    });
}
