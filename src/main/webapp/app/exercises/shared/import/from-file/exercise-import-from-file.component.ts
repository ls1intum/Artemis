import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { AlertService } from 'app/core/util/alert.service';
import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import JSZip from 'jszip';

@Component({
    selector: 'jhi-exercise-import-from-file',
    templateUrl: './exercise-import-from-file.component.html',
})
export class ExerciseImportFromFileComponent implements OnInit {
    @Input()
    exerciseType: ExerciseType;
    titleKey: string;
    fileForImport?: File;
    faUpload = faUpload;
    @Input()
    exercise: Exercise;

    constructor(private activeModal: NgbActiveModal, private alertService: AlertService) {}

    ngOnInit(): void {
        this.titleKey =
            this.exerciseType === ExerciseType.FILE_UPLOAD ? `artemisApp.fileUploadExercise.importFromFile.title` : `artemisApp.${this.exerciseType}Exercise.importFromFile.title`;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    /** uploads the zip file and extracts the minimal information required to fill the exercise-update component, it's async, so one can conveniently use await **/
    async uploadExercise() {
        const jsonRegex = new RegExp('.*.json');
        const zip = await JSZip.loadAsync(this.fileForImport as File);
        const jsonFiles = zip.file(jsonRegex);
        if (jsonFiles.length !== 1) {
            this.alertService.error('artemisApp.programmingExercise.importFromFile.noExerciseDetailsJsonAtRootLevel');
            return;
        }
        const exerciseDetails = await jsonFiles[0].async('string');

        const exerciseJson = JSON.parse(exerciseDetails) as Exercise;
        if (exerciseJson.type !== this.exerciseType) {
            this.alertService.error('artemisApp.exercise.importFromFile.exerciseTypeDoesntMatch');
            return;
        }
        switch (this.exerciseType) {
            case ExerciseType.PROGRAMMING:
                this.exercise = JSON.parse(exerciseDetails as string) as ProgrammingExercise;
                this.handleProgrammingExercise(this.exercise);
                break;
            default:
                this.alertService.error('artemisApp.exercise.importFromFile.notSupportedExerciseType', {
                    exerciseType: this.exerciseType,
                });
                return;
        }
        this.exercise.id = undefined;
        this.exercise.zipFileForImport = this.fileForImport as File;

        this.openImport(this.exercise);
    }

    private handleProgrammingExercise(exercise: ProgrammingExercise) {
        if (
            exercise.programmingLanguage === ProgrammingLanguage.HASKELL ||
            exercise.programmingLanguage === ProgrammingLanguage.OCAML ||
            exercise.programmingLanguage === ProgrammingLanguage.SWIFT
        ) {
            this.alertService.error('artemisApp.programmingExercise.importFromFile.notSupportedProgrammingLanguage', { programmingLanguage: exercise.programmingLanguage });
        }
    }

    /** sets the zip file that is selected in the file input dialgo **/
    setFileForExerciseImport(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            if (fileList.length != 1) {
                this.alertService.error('artemisApp.programmingExercise.importFromFile.fileCountError');
                return;
            }
            const exerciseFile = fileList[0];
            if (!exerciseFile.name.toLowerCase().endsWith('.zip')) {
                this.alertService.error('artemisApp.programmingExercise.importFromFile.fileExtensionError');
                return;
            } else if (exerciseFile.size > MAX_FILE_SIZE) {
                this.alertService.error('artemisApp.programmingExercise.importFromFile.fileTooBigError', { fileName: exerciseFile.name });
                return;
            } else {
                this.fileForImport = exerciseFile;
            }
        }
    }

    openImport(exercise: Exercise) {
        this.activeModal.close(exercise);
    }
}
