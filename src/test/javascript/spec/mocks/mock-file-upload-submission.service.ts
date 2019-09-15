import { of } from 'rxjs';
import { Participation, ParticipationType, StudentParticipation } from 'app/entities/participation';
import { fileUploadExercise } from './mock-file-upload-exercise.service';

export const fileUploadParticipation = new StudentParticipation();
fileUploadParticipation.exercise = fileUploadExercise;
fileUploadParticipation.id = 1;

export class MockFileUploadSubmissionService {
    getDataForFileUploadEditor = (participationId: number) => of(fileUploadParticipation);
}
