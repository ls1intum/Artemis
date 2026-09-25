import { Service, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { createRequestOption } from 'app/foundation/util/request.util';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { downloadFile, downloadZipFromFilePromises } from 'app/foundation/util/download.util';
import { objectToJsonBlob } from 'app/foundation/util/blob-util';
import { ZipBuilder } from 'app/foundation/util/zip.util';
import { FileService } from 'app/foundation/service/file.service';
import { AccountService } from 'app/core/auth/account.service';
import { QuizExerciseRetrievalApi } from 'app/openapi/api/quiz-exercise-retrieval-api';
import { QuizStatisticsApi } from 'app/openapi/api/quiz-statistics-api';
import {
    toQuizExercise,
    toQuizExerciseFromListRow,
    toQuizPointStatistics,
    toQuizQuestionStatistic,
    toQuizStatisticsOverview,
} from 'app/quiz/shared/util/generated-quiz-exercise.util';
import { toQuizExerciseUpdateDTO } from 'app/quiz/shared/entities/quiz-exercise-update-dto.model';
import { convertQuizExerciseToCreationDTO } from 'app/quiz/shared/entities/quiz-exercise-creation/quiz-exercise-creation-dto.model';
import { QuizPointStatisticsResponse, QuizQuestionStatisticResponse, QuizStatisticsOverviewResponse } from 'app/quiz/manage/statistics/quiz-statistics-response.model';

export type EntityResponseType = HttpResponse<QuizExercise>;

@Service()
export class QuizExerciseService {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);
    private fileService = inject(FileService);
    private accountService = inject(AccountService);
    private quizExerciseRetrievalApi = inject(QuizExerciseRetrievalApi);
    private quizStatisticsApi = inject(QuizStatisticsApi);
    private resourceUrl = 'api/quiz/quiz-exercises';
    private quizBaseURL = 'api/quiz';

    /**
     * Create the given quiz exercise
     * @param quizExercise the quiz exercise that should be created
     * @param files the files that should be uploaded
     */
    create(quizExercise: QuizExercise, files: Map<string, Blob>): Observable<EntityResponseType> {
        const copy = ExerciseService.convertExerciseDatesFromClient(quizExercise);
        ExerciseService.stringifyExerciseCategories(copy);

        const exerciseDTO = convertQuizExerciseToCreationDTO(copy);

        const formData = new FormData();
        formData.append('exercise', objectToJsonBlob(exerciseDTO));
        files.forEach((file, fileName) => {
            formData.append('files', file, fileName);
        });

        const hasExerciseGroup = quizExercise.exerciseGroup?.id;
        const hasCourse = quizExercise.course?.id;

        let url: string;
        if (hasExerciseGroup) {
            url = `${this.quizBaseURL}/exercise-groups/${quizExercise.exerciseGroup!.id}/quiz-exercises`;
        } else if (hasCourse) {
            url = `${this.quizBaseURL}/courses/${quizExercise.course!.id}/quiz-exercises`;
        } else {
            throw new Error('Quiz exercise must belong to a course or an exercise group');
        }

        return this.http.post<QuizExercise>(url, formData, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Imports a quiz exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceQuizExercise The exercise that should be imported, including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     * @param files The files that should be uploaded
     */
    import(adaptedSourceQuizExercise: QuizExercise, files: Map<string, Blob>) {
        let copy = ExerciseService.convertExerciseDatesFromClient(adaptedSourceQuizExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        ExerciseService.stringifyExerciseCategories(copy);

        const formData = new FormData();
        formData.append('exercise', objectToJsonBlob(copy));
        files.forEach((file, fileName) => {
            formData.append('files', file, fileName);
        });

        return this.http
            .post<QuizExercise>(`${this.resourceUrl}/import/${adaptedSourceQuizExercise.id}`, formData, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Update the given quiz exercise
     * @param id the id of the quiz exercise that should be updated
     * @param quizExercise the quiz exercise that should be updated
     * @param files the files that should be uploaded
     * @param req Additional parameters that should be passed to the server when updating the exercise
     */
    update(id: number, quizExercise: QuizExercise, files: Map<string, Blob>, req?: { notificationText?: string }): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = ExerciseService.convertExerciseDatesFromClient(quizExercise);
        ExerciseService.stringifyExerciseCategories(copy);

        const exerciseDTO = toQuizExerciseUpdateDTO(copy);
        const formData = new FormData();
        formData.append('exercise', objectToJsonBlob(exerciseDTO));
        files.forEach((file, fileName) => {
            formData.append('files', file, fileName);
        });

        return this.http
            .put<QuizExercise>(this.resourceUrl + '/' + id, formData, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Find the quiz exercise with the given id, with the full question graph an instructor edits
     * @param quizExerciseId the id of the quiz exercise that should be found
     */
    find(quizExerciseId: number): Observable<QuizExercise> {
        return this.quizExerciseRetrievalApi.getQuizExercise(quizExerciseId).pipe(map((exercise) => this.prepareForClient(toQuizExercise(exercise))));
    }

    /**
     * Loads the calculated overview statistics for a quiz exercise.
     *
     * @param quizExerciseId the ID of the quiz exercise
     * @return the quiz exercise overview and its calculated statistics
     */
    findStatisticsOverview(quizExerciseId: number): Observable<QuizStatisticsOverviewResponse> {
        return this.quizStatisticsApi.getQuizStatisticsOverview(quizExerciseId).pipe(map((overview) => this.prepareForClient(toQuizStatisticsOverview(overview))));
    }

    /**
     * Loads the calculated point distribution for a quiz exercise.
     *
     * @param quizExerciseId the ID of the quiz exercise
     * @return the quiz exercise and its calculated point distribution
     */
    findPointStatistic(quizExerciseId: number): Observable<QuizPointStatisticsResponse> {
        return this.quizStatisticsApi.getQuizPointStatistic(quizExerciseId).pipe(map((pointStatistics) => this.prepareForClient(toQuizPointStatistics(pointStatistics))));
    }

    /**
     * Loads the calculated statistic for one question in a quiz exercise.
     *
     * @param quizExerciseId the ID of the quiz exercise
     * @param questionId the ID of the quiz question
     * @return the quiz exercise, question, and calculated question statistic
     */
    findQuestionStatistic(quizExerciseId: number, questionId: number): Observable<QuizQuestionStatisticResponse> {
        return this.quizStatisticsApi
            .getQuizQuestionStatistic(quizExerciseId, questionId)
            .pipe(map((questionStatistic) => this.prepareForClient(toQuizQuestionStatistic(questionStatistic))));
    }

    /**
     * Note: the exercises in the response do not contain participations, the course or questions, to save network bandwidth
     *
     * @param courseId the course for which the quiz exercises should be returned
     */
    findForCourse(courseId: number): Observable<QuizExercise[]> {
        return this.quizExerciseRetrievalApi
            .getQuizExercisesForCourse(courseId)
            .pipe(map((exercises) => exercises.map((exercise) => this.prepareForClient(toQuizExerciseFromListRow(exercise)))));
    }

    /**
     * Note: the exercises in the response do not contain participations, the course, the exercise group or questions, to
     * save network bandwidth
     *
     * @param examId the exam for which the quiz exercises should be returned
     */
    findForExam(examId: number): Observable<QuizExercise[]> {
        return this.quizExerciseRetrievalApi
            .getQuizExercisesForExam(examId)
            .pipe(map((exercises) => exercises.map((exercise) => this.prepareForClient(toQuizExerciseFromListRow(exercise)))));
    }

    /**
     * Find the quiz exercise with the given id, with as much of the question graph as the quiz state lets a student see
     * @param quizExerciseId the id of the quiz exercise that should be loaded
     */
    findForStudent(quizExerciseId: number): Observable<QuizExercise> {
        return this.quizExerciseRetrievalApi.getQuizExerciseForStudent(quizExerciseId).pipe(map((exercise) => this.prepareForClient(toQuizExercise(exercise))));
    }

    /**
     * Exports given quiz questions into json file
     * @param quizQuestions Quiz questions we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     * @param fileName Name (without ending) of the resulting file, defaults to 'quiz'
     */
    exportQuiz(quizQuestions?: QuizQuestion[], exportAll?: boolean, fileName?: string) {
        // Make list of questions which we need to export,
        const questions: QuizQuestion[] = [];

        quizQuestions!.forEach((question) => {
            if (exportAll === true || question.exportQuiz) {
                question.exercise = undefined;
                questions.push(question);
            }
        });
        if (questions.length === 0) {
            return;
        }
        this.exportAssetsFromAllQuestions(questions, fileName ?? 'quiz');
    }

    /**
     * Exports assets (images) embedded in the markdown and from drag and drop exercises
     * @param questions list of questions which will be exported
     * @param fileName name of the output zip file
     */
    exportAssetsFromAllQuestions(questions: QuizQuestion[], fileName: string) {
        const zip: ZipBuilder = new ZipBuilder();
        const filePromises: Promise<void | File>[] = [];
        const quizJson = JSON.stringify(questions);
        const blob = new Blob([quizJson], { type: 'application/json' });
        questions.forEach((question, questionIndex) => {
            if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                if ((question as DragAndDropQuestion).backgroundFilePath) {
                    const filePath = (question as DragAndDropQuestion).backgroundFilePath!;
                    const fileNameExtension = filePath.split('.').last();
                    filePromises.push(this.fetchFilePromise(`q${questionIndex}_background.${fileNameExtension}`, zip, filePath));
                }
                if ((question as DragAndDropQuestion).dragItems) {
                    (question as DragAndDropQuestion).dragItems?.forEach((dragItem, drag_index) => {
                        if (dragItem.pictureFilePath) {
                            const filePath = dragItem.pictureFilePath;
                            const fileNameExtension = filePath.split('.').last();
                            // A drag item picture is stored under its filename alone, so the question-scoped path it is served under has to be assembled from the two ids.
                            const downloadPath =
                                question.id !== undefined && dragItem.id !== undefined
                                    ? `drag-and-drop/questions/${question.id}/drag-items/${dragItem.id}/${filePath.substring(filePath.lastIndexOf('/') + 1)}`
                                    : filePath;
                            filePromises.push(this.fetchFilePromise(`q${questionIndex}_dragItem-${drag_index}.${fileNameExtension}`, zip, downloadPath));
                        }
                    });
                }
            }
            this.findImagesInMarkdown(JSON.stringify(question)).forEach((embeddedImage) => {
                filePromises.push(this.fetchFilePromise(`q${questionIndex}_${embeddedImage[1]}`, zip, embeddedImage[2]));
            });
        });
        if (filePromises.length === 0) {
            downloadFile(blob, (fileName ?? 'quiz') + '.json');
            return;
        }
        zip.file((fileName ?? 'quiz') + '.json', blob);
        downloadZipFromFilePromises(zip, filePromises, fileName);
    }

    findImagesInMarkdown(description: string) {
        // Will return all matches of ![file_name](path), will group file_name and path
        const embeddedImageRegex = /!\[(.+?)\]\((.+?)\)/g;
        return [...description.matchAll(embeddedImageRegex)];
    }

    /**
     * This method fetches a file through the file Service, zips it and pushes it to the provided list of file Promises
     * @param fileName the name of the file to be zipped
     * @param zip a ZipBuilder instance
     * @param filePath the internal path of the file to be fetched
     */
    async fetchFilePromise(fileName: string, zip: ZipBuilder, filePath: string) {
        return this.fileService
            .getFile(filePath)
            .then((fileResult) => {
                zip.file(fileName, fileResult);
            })
            .catch((error) => {
                throw new Error(`File with name: ${fileName} at path: ${filePath} could not be fetched` + error);
            });
    }

    /**
     * Evaluates the QuizStatus for a given quiz
     *
     * @param quizExercise the quiz exercise to get the status of
     * @return the status of the quiz
     */
    getStatus(quizExercise: QuizExercise) {
        if (!quizExercise.visibleToStudents) {
            return QuizStatus.INVISIBLE;
        }
        if (quizExercise.quizEnded) {
            return QuizStatus.OPEN_FOR_PRACTICE;
        }
        if (quizExercise.quizBatches && quizExercise.quizBatches.some((batch) => batch.started)) {
            return QuizStatus.ACTIVE;
        }
        return QuizStatus.VISIBLE;
    }

    /**
     * Applies the client-side preparation every loaded exercise receives, whatever its type: parsed categories, the
     * current user's access rights, and the title the breadcrumbs show.
     *
     * @param quizExercise the converted exercise
     * @returns the same exercise, prepared
     */
    private prepareForClient<T extends Omit<QuizExercise, 'quizQuestions'>>(quizExercise: T): T {
        ExerciseService.parseExerciseCategories(quizExercise);
        this.accountService.setAccessRightsForExerciseAndReferencedCourse(quizExercise);
        this.exerciseService.sendExerciseTitleToTitleService(quizExercise);
        return quizExercise;
    }
}
