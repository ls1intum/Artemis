import { of } from 'rxjs';
import { AgentParticipation } from 'app/entities/participation';
import { fileUploadExercise } from './mock-file-upload-exercise.service';
import { FileUploadSubmission } from 'app/entities/file-upload-submission';
import { Result } from 'app/entities/result';

export const fileUploadParticipation = new AgentParticipation();
fileUploadParticipation.exercise = fileUploadExercise;
fileUploadParticipation.id = 1;

export const createFileUploadSubmission = () => {
    const fileUploadSubmission = new FileUploadSubmission();
    fileUploadSubmission.submitted = false;
    fileUploadSubmission.participation = fileUploadParticipation;
    fileUploadSubmission.id = 1;
    return fileUploadSubmission;
};

export class MockFileUploadSubmissionService {
    getDataForFileUploadEditor = (participationId: number) => of(createFileUploadSubmission());
    update = (fileUploadSubmission: FileUploadSubmission, exerciseId: number) => {
        fileUploadSubmission.result = new Result();
        return of({ body: fileUploadSubmission });
    };
}
