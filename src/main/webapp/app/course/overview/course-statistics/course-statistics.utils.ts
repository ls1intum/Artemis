import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';

/** The overview intentionally withholds pending submission content; its status flag remains safe to display. */
export function hasSubmittedForStatistics(participation: StudentParticipation | undefined): boolean {
    return (
        participation?.hasSubmitted ||
        participation?.submissions?.some(
            (submission) =>
                submission.submitted || (participation.type === ParticipationType.PROGRAMMING && !!submission.submissionDate) || submission.results?.some((result) => result.rated),
        ) ||
        false
    );
}
