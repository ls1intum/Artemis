import { of } from 'rxjs';
import { Participation, ParticipationType, StudentParticipation } from 'app/entities/participation';
import { fileUploadExercise } from './mock-file-upload-exercise.service';
import { EntityResponseType, FileUploadSubmission } from 'app/entities/file-upload-submission';
import { Result } from 'app/entities/result';

export const fileUploadParticipation = new StudentParticipation();
fileUploadParticipation.exercise = fileUploadExercise;
fileUploadParticipation.id = 1;

export const fileUploadSubmission = new FileUploadSubmission();
fileUploadSubmission.submitted = false;
fileUploadSubmission.participation = fileUploadParticipation;
fileUploadSubmission.id = 1;

export class MockFileUploadSubmissionService {
    getDataForFileUploadEditor = (participationId: number) => of(fileUploadParticipation);
    update = (fileUploadSubmission: FileUploadSubmission, exerciseId: number) => {
        fileUploadSubmission.result = new Result();
        return of({ body: fileUploadSubmission });
    };
}
