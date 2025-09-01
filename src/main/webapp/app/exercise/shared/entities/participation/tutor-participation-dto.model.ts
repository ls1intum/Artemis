import { TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';

export interface TutorParticipationDTO {
    id: number;
    exerciseId: number;
    tutorId: number;
    status: TutorParticipationStatus;
    trainedCount: number;
}
