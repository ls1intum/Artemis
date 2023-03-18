import { Component, Input } from '@angular/core';
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
export class ExerciseImportFromFileComponent {
    @Input()
    exerciseType: ExerciseType;
    titleKey: string;
    fileForImport?: File;
    faUpload = faUpload;
    @Input()
    courseId?: number;

    constructor(private activeModal: NgbActiveModal, private alertService: AlertService) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    uploadExercise(exerciseType: ExerciseType) {
        const jsonRegex = new RegExp('.*.json');
        JSZip.loadAsync(this.fileForImport as File).then((zip) => {
            const jsonFiles = zip.file(jsonRegex);
            if (jsonFiles.length !== 1) {
                this.alertService.error('artemisApp.programmingExercise.importFromFile.noExerciseDetailsJsonAtRootLevel');
            }
            jsonFiles[0].async('string').then((exerciseDetails) => {
                let exercise: Exercise;
                switch (exerciseType) {
                    case ExerciseType.PROGRAMMING:
                        exercise = JSON.parse(exerciseDetails as string) as ProgrammingExercise;
                        this.handleProgrammingExercise(exercise);
                        break;
                    default:
                        this.alertService.error('artemisApp.exercise.importFromFile.notSupportedExerciseType', { exerciseType });
                        return;
                }
                exercise.id = undefined;
                exercise.zipFileForImport = this.fileForImport as File;

                this.openImport(exercise);
            });
        });
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

    setFileForExerciseImport(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            const exerciseFile = fileList[0];
            if (!exerciseFile.name.toLowerCase().endsWith('.zip')) {
                this.alertService.error('artemisApp.programmingExercise.importFromFile.fileExtensionError');
            } else if (exerciseFile.size > MAX_FILE_SIZE) {
                this.alertService.error('artemisApp.programmingExercise.importFromFile.FileTooBigError', { fileName: exerciseFile.name });
            } else {
                this.fileForImport = exerciseFile;
            }
        }
    }

    openImport(exercise: Exercise) {
        this.activeModal.close(exercise);
    }
}
