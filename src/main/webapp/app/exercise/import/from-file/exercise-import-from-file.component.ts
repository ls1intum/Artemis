import { Component, computed, inject, input, output, signal } from '@angular/core';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular';
import { Exercise, ExerciseType, resetForImport } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { AlertService } from 'app/foundation/service/alert.service';
import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise, copyBuildConfigFromExerciseJson } from 'app/programming/shared/entities/programming-exercise.model';
import { strFromU8 } from 'fflate';
import { parseJson } from 'app/foundation/util/json.util';
import { ZipEntries, readZipEntries } from 'app/foundation/util/zip.util';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseImportDialogData } from '../exercise-import.component';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exercise-import-from-file',
    templateUrl: './exercise-import-from-file.component.html',
    imports: [TumAetUiButtonComponent, TranslateDirective, HelpIconComponent, ArtemisTranslatePipe],
})
export class ExerciseImportFromFileComponent {
    private readonly dialogRef = inject(TumAetUiDialogRef, { optional: true });
    private readonly dialogConfig = inject(TumAetUiDialogConfig, { optional: true });
    private readonly alertService = inject(AlertService);
    private readonly fileUploaderService = inject(FileUploaderService);

    exerciseType = input<ExerciseType | undefined>();
    /** The course to import into, which owns the uploaded problem statement images; for hosts that embed the component without dialog data. */
    readonly courseId = input<number | undefined>();
    /** Emits the exercise read from the file, for hosts that embed the component without a dialog reference. */
    readonly imported = output<Exercise>();
    private readonly selectedExerciseType = computed(() => (this.dialogConfig?.data as ExerciseImportDialogData | undefined)?.exerciseType ?? this.exerciseType());
    private readonly selectedCourseId = computed(() => (this.dialogConfig?.data as ExerciseImportDialogData | undefined)?.courseId ?? this.courseId());

    exercise?: Exercise;

    readonly fileForImport = signal<File | undefined>(undefined);
    /** Whether a file is being read, so a second click does not upload its images again. */
    readonly isImporting = signal(false);
    //Icons
    faUpload = faUpload;

    /** uploads the zip file and extracts the minimal information required to fill the exercise-update component, it's async, so one can conveniently use await **/
    async uploadExercise() {
        if (this.isImporting()) {
            return;
        }
        this.isImporting.set(true);
        try {
            await this.readExercise();
        } finally {
            this.isImporting.set(false);
        }
    }

    private async readExercise() {
        const exerciseType = this.selectedExerciseType();
        if (!exerciseType) {
            return;
        }

        const zipEntries = await readZipEntries(this.fileForImport() as File);
        const jsonFiles = Object.keys(zipEntries).filter((fileName) => fileName.endsWith('.json'));
        if (jsonFiles.length !== 1) {
            this.alertService.error('artemisApp.exercise.importFromFile.noExerciseDetailsJson');
            return;
        }
        const exerciseDetails = strFromU8(zipEntries[jsonFiles[0]]);

        const exerciseJson = parseJson<Exercise>(exerciseDetails);
        if (exerciseJson.type !== exerciseType) {
            this.alertService.error('artemisApp.exercise.importFromFile.exerciseTypeDoesntMatch');
            return;
        }
        if (exerciseType === ExerciseType.PROGRAMMING) {
            this.exercise = this.prepareProgrammingExercise(exerciseJson);
            this.exercise.zipFileForImport = this.fileForImport() as File;
        } else {
            // The other types open the editor of a new exercise, which creates it through the regular create endpoint.
            const exercise = exerciseJson;
            ExerciseService.parseExerciseCategories(exercise);
            resetForImport(exercise);
            // The channel of the source exercise would collide with the new one when importing into the same course.
            exercise.channelName = undefined;
            // Archives written before the export left out these ids would otherwise make the server update the source rows.
            for (const criterion of exercise.gradingCriteria ?? []) {
                criterion.id = undefined;
                criterion.structuredGradingInstructions?.forEach((instruction) => (instruction.id = undefined));
            }
            const filesFound =
                exerciseType === ExerciseType.QUIZ ? this.attachDragAndDropImages(exercise, zipEntries) : await this.uploadProblemStatementFiles(exercise, zipEntries);
            if (!filesFound) {
                return;
            }
            this.exercise = exercise;
        }
        this.exercise.id = undefined;

        this.openImport(this.exercise);
    }

    private prepareProgrammingExercise(progEx: ProgrammingExercise): ProgrammingExercise {
        // This is needed to make sure that old exported programming exercises can be imported
        if (!progEx.buildConfig) {
            const buildConfig = new ProgrammingExerciseBuildConfig();
            // Old exports serialized the build-config fields flat on the exercise object (before they were nested
            // under buildConfig). Those fields are disjoint from Exercise, so we view the parsed exercise as also
            // carrying the optional legacy build-config fields and copy them across.
            const raw = progEx as Exercise & Partial<ProgrammingExerciseBuildConfig>;
            hydrate(buildConfig, raw);
            progEx.buildConfig = copyBuildConfigFromExerciseJson(buildConfig);
        }
        progEx.auxiliaryRepositories?.forEach((repo) => (repo.id = undefined));

        // This ensures each entry is converted into a proper ExerciseCategory instance
        // so that category badges render correctly in the UI.
        ExerciseService.parseExerciseCategories(progEx);
        return progEx;
    }

    /**
     * Uploads the images of the problem statement, which the export puts into the folder {@code files}, and points the problem
     * statement at the uploaded copies. The server of the source may be another Artemis instance, so the old links would break.
     *
     * @return false if an upload failed, after showing an error
     */
    private async uploadProblemStatementFiles(exercise: Exercise, zipEntries: ZipEntries): Promise<boolean> {
        const statement = exercise.problemStatement;
        if (!statement) {
            return true;
        }
        // Load source-aware parsers only when importing a problem statement.
        const { markdownFileDestinations, markdownFileDestinationSuffix } = await import('./markdown-file-destinations');
        const destinations = markdownFileDestinations(statement);
        const replacements: { start: number; end: number; path: string }[] = [];
        for (const [entryName, data] of Object.entries(zipEntries)) {
            const fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
            if (!entryName.startsWith('files/') || !fileName) {
                continue;
            }
            const references = destinations
                .map((destination) => ({ destination, suffix: markdownFileDestinationSuffix(destination.url, fileName) }))
                .filter((reference): reference is { destination: (typeof destinations)[number]; suffix: string } => reference.suffix !== undefined);
            if (!references.length) {
                continue;
            }
            try {
                const response = await this.fileUploaderService.uploadMarkdownFile(new File([data], fileName), this.selectedCourseId());
                for (const { destination, suffix } of references) {
                    replacements.push({ start: destination.start, end: destination.end, path: response.path! + suffix });
                }
            } catch {
                // The localized alert identifies the failed file; the transport error provides no further action for the user.
                this.alertService.error('artemisApp.exercise.importFromFile.imageUploadFailed', { fileName });
                return false;
            }
        }
        // Apply from the end so changing a URL's length cannot shift any remaining source positions.
        exercise.problemStatement = statement;
        replacements.sort((a, b) => b.start - a.start);
        for (const replacement of replacements) {
            exercise.problemStatement = exercise.problemStatement.slice(0, replacement.start) + replacement.path + exercise.problemStatement.slice(replacement.end);
        }
        return true;
    }

    /**
     * Hands the drag-and-drop images in the zip to the question editors, which upload them as new files on save. The export puts the
     * images of a question into the folder {@code images-for-drag-and-drop-question-<question id>} under their stored file names.
     *
     * @return false if an image is missing, after showing an error
     */
    private attachDragAndDropImages(quizExercise: QuizExercise, zipEntries: ZipEntries): boolean {
        for (const question of quizExercise.quizQuestions ?? []) {
            if (question.type !== QuizQuestionType.DRAG_AND_DROP) {
                continue;
            }
            const dragAndDropQuestion = question as DragAndDropQuestion;
            const importedFiles = new Map<string, Blob>();
            const attach = (filePath: string | undefined): string | undefined => {
                if (!filePath) {
                    return filePath;
                }
                const fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                const data = zipEntries[`images-for-drag-and-drop-question-${question.id}/${fileName}`];
                if (!data) {
                    throw new Error(fileName);
                }
                importedFiles.set(fileName, new Blob([data]));
                return fileName;
            };
            try {
                dragAndDropQuestion.backgroundFilePath = attach(dragAndDropQuestion.backgroundFilePath);
                dragAndDropQuestion.dragItems?.forEach((dragItem) => (dragItem.pictureFilePath = attach(dragItem.pictureFilePath)));
            } catch (error) {
                this.alertService.error('artemisApp.exercise.importFromFile.missingImage', { fileName: (error as Error).message });
                return false;
            }
            dragAndDropQuestion.importedFiles = importedFiles;
        }
        return true;
    }

    /** sets the zip file that is selected in the file input dialog **/
    setFileForExerciseImport(event: Event): void {
        const fileInput = event.target as HTMLInputElement;
        if (fileInput.files?.length) {
            const fileList = fileInput.files;
            if (fileList.length != 1) {
                this.alertService.error('artemisApp.exercise.importFromFile.fileCountError');
                return;
            }
            const exerciseFile = fileList[0];
            if (!exerciseFile.name.toLowerCase().endsWith('.zip')) {
                this.alertService.error('artemisApp.exercise.importFromFile.fileTypeNotSupportedError');
                return;
            } else if (exerciseFile.size > MAX_FILE_SIZE) {
                this.alertService.error('artemisApp.exercise.importFromFile.fileTooBigError', { fileName: exerciseFile.name });
                return;
            } else {
                this.fileForImport.set(exerciseFile);
            }
        }
    }

    openImport(exercise: Exercise) {
        this.imported.emit(exercise);
        this.dialogRef?.close(exercise);
    }
}
