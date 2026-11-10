import { convertFeedbackFromServer } from 'app/assessment/shared/entities/feedback.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { EditorViewingPurpose } from 'app/exercise/shared/exercise-submission.interface';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { Service, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { FileUploadParticipation, FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { createRequestOption } from 'app/foundation/util/request.util';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { addPublicFilePrefix } from 'app/app.constants';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { FileUploadSubmissionApi } from 'app/openapi/api/file-upload-submission-api';
import { FileUploadSubmission as FileUploadSubmissionResponse } from 'app/openapi/model/file-upload-submission';
import { FileUploadParticipation as FileUploadParticipationResponse } from 'app/openapi/model/file-upload-participation';
import { FileUploadExerciseContext } from 'app/openapi/model/file-upload-exercise-context';
import { FileUploadExerciseGroupContext } from 'app/openapi/model/file-upload-exercise-group-context';
import { FileUploadExamContext } from 'app/openapi/model/file-upload-exam-context';
import { FileUploadCourseContext } from 'app/openapi/model/file-upload-course-context';
import { FileUploadResult } from 'app/openapi/model/file-upload-result';
import { toFeedbackFromServer } from 'app/fileupload/shared/entities/file-upload-assessment-dto.model';

@Service()
export class FileUploadSubmissionService {
    private http = inject(HttpClient);
    private fileUploadSubmissionApi = inject(FileUploadSubmissionApi);
    private submissionService = inject(SubmissionService);

    /**
     * Updates File Upload submission on the server
     * @param fileUploadSubmission that will be updated on the server
     * @param exerciseId id of the exercise
     * @param submissionFile the file submitted that will for the exercise
     */
    update(fileUploadSubmission: FileUploadSubmission, exerciseId: number, submissionFile: File): Observable<FileUploadSubmission> {
        const submissionInput = {
            id: fileUploadSubmission.id,
            submitted: fileUploadSubmission.submitted ?? false,
            exerciseId,
        };
        return this.fileUploadSubmissionApi
            .createFileUploadSubmission(exerciseId, submissionInput, submissionFile)
            .pipe(map((submission) => this.convertFileSubmissionFromServer(submission)));
    }

    /**
     * Returns File Upload submission from the server
     * @param fileUploadSubmissionId the id of the File Upload submission
     * @param correctionRound the correction round to load, used only without a result id
     * @param resultId the result to load; when set, the correction round is not sent
     */
    get(fileUploadSubmissionId: number, correctionRound = 0, resultId?: number): Observable<FileUploadSubmission> {
        // in case resultId is set, we do not need the correction round
        const request =
            resultId && resultId > 0
                ? this.fileUploadSubmissionApi.getFileUploadSubmission(fileUploadSubmissionId, undefined, resultId)
                : this.fileUploadSubmissionApi.getFileUploadSubmission(fileUploadSubmissionId, correctionRound, undefined);
        return request.pipe(map((submission) => this.convertFileSubmissionFromServer(submission)));
    }

    /**
     * Lists file upload submissions with participant identities; requires instructor access.
     * @param exerciseId the id of the exercise
     * @param req request parameters
     * @param correctionRound for which to get the Submissions
     */
    getSubmissions(exerciseId: number, req: { submittedOnly?: boolean; assessedByMe?: boolean }, correctionRound = 0): Observable<HttpResponse<FileUploadSubmission[]>> {
        return this.getSubmissionList(`api/fileupload/exercises/${exerciseId}/file-upload-submissions`, req, correctionRound);
    }

    /** Lists the authenticated assessor's submissions with anonymous participants, including for instructors. */
    getMyAssessedSubmissions(exerciseId: number, correctionRound = 0): Observable<HttpResponse<FileUploadSubmission[]>> {
        return this.getSubmissionList(`api/fileupload/exercises/${exerciseId}/file-upload-submissions/assessed-by-me`, undefined, correctionRound);
    }

    private getSubmissionList(
        url: string,
        req: { submittedOnly?: boolean; assessedByMe?: boolean } | undefined,
        correctionRound: number,
    ): Observable<HttpResponse<FileUploadSubmission[]>> {
        let params = createRequestOption(req);
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http
            .get<FileUploadSubmissionResponse[]>(url, {
                params,
                observe: 'response',
            })
            .pipe(
                map((res: HttpResponse<FileUploadSubmissionResponse[]>) =>
                    res.clone({ body: res.body?.map((submission) => this.convertFileSubmissionFromServer(submission)) ?? null }),
                ),
            );
    }

    /**
     * Returns next File Upload submission without assessment from the server
     * @param exerciseId the id of the exercise
     * @param lock whether to lock the submission for the current assessor
     * @param correctionRound for which to get the Submissions
     * @returns the submission, or undefined when no submission is left to assess
     */
    getSubmissionWithoutAssessment(exerciseId: number, lock?: boolean, correctionRound = 0): Observable<FileUploadSubmission | undefined> {
        return this.fileUploadSubmissionApi
            .getFileUploadSubmissionWithoutAssessment(exerciseId, lock, correctionRound)
            .pipe(map((submission) => (submission ? this.convertFileSubmissionFromServer(submission) : undefined)));
    }

    /**
     * Returns data for File Upload editor from the server
     * @param participationId the id of the participation
     * @param purpose whether the owner or a staff member opens the editor
     */
    getDataForFileUploadEditor(participationId: number, purpose: EditorViewingPurpose = 'owner'): Observable<FileUploadSubmission> {
        const request =
            purpose === 'staff-preview'
                ? this.fileUploadSubmissionApi.getDataForFileUploadForStaff(participationId)
                : this.fileUploadSubmissionApi.getDataForFileUpload(participationId);
        return request.pipe(map((submission) => this.convertFileSubmissionFromServer(submission)));
    }

    /**
     * In {@link submissionService.convertSubmissionFromServer} {@link object.assign} is used, which means that we never actually call the
     * constructor of the {@link FileUploadSubmission} class, which would set {@link FileUploadSubmission.filePathUrl}, therefore we need to set it manually here.
     */
    private convertFileSubmissionFromServer(dto: FileUploadSubmissionResponse): FileUploadSubmission {
        const convertedBaseSubmission = this.submissionService.convertSubmissionFromServer(this.mapSubmissionDtoToModel(dto));
        convertedBaseSubmission.filePathUrl = addPublicFilePrefix(dto.filePath);
        return convertedBaseSubmission;
    }

    private mapSubmissionDtoToModel(dto: FileUploadSubmissionResponse): FileUploadSubmission {
        // The class types several enums that the generated model types as string literals, so the intersection hydrate() returns
        // collapses to never. Annotating the target keeps the class view of the data.
        const submission: FileUploadSubmission = hydrate(new FileUploadSubmission(), dto);
        submission.submissionDate = convertDateStringFromServer(dto.submissionDate);
        submission.results = dto.results?.map((result) => this.mapResultDtoToModel(result));
        submission.participation = this.mapParticipationDtoToModel(dto.participation);
        return submission;
    }

    private mapResultDtoToModel(dto: FileUploadResult): Result {
        const result: Result = hydrate(new Result(), dto);
        result.completionDate = convertDateStringFromServer(dto.completionDate);
        result.feedbacks = dto.feedbacks?.map((feedback) => convertFeedbackFromServer(toFeedbackFromServer(feedback)));
        return result;
    }

    private mapParticipationDtoToModel(dto: FileUploadParticipationResponse | undefined): FileUploadParticipation | undefined {
        if (!dto) {
            return undefined;
        }
        // The participation class is built from its type, which the server sends as the client's participation type value.
        const type = Object.values(ParticipationType).find((participationType) => participationType === dto.type);
        return hydrate(new FileUploadParticipation(type), dto, { exercise: this.mapExerciseDtoToModel(dto.exercise) });
    }

    private mapExerciseDtoToModel(dto: FileUploadExerciseContext | undefined): FileUploadExercise | undefined {
        if (!dto) {
            return undefined;
        }
        const course = this.mapCourseDtoToModel(dto.course);
        const exerciseGroup = this.mapExerciseGroupDtoToModel(dto.exerciseGroup);
        return hydrate(new FileUploadExercise(course, exerciseGroup), dto, { course, exerciseGroup });
    }

    private mapExerciseGroupDtoToModel(dto: FileUploadExerciseGroupContext | undefined): ExerciseGroup | undefined {
        if (!dto) {
            return undefined;
        }
        return hydrate(new ExerciseGroup(), dto, { exam: this.mapExamDtoToModel(dto.exam) });
    }

    private mapExamDtoToModel(dto: FileUploadExamContext | undefined): Exam | undefined {
        if (!dto) {
            return undefined;
        }
        return hydrate(new Exam(), dto, { course: this.mapCourseDtoToModel(dto.course) });
    }

    private mapCourseDtoToModel(dto: FileUploadCourseContext | undefined): Course | undefined {
        return dto ? hydrate(new Course(), dto) : undefined;
    }
}
