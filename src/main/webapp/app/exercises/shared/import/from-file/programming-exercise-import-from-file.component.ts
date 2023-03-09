import { Component, Input } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { AlertService } from 'app/core/util/alert.service';
import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-exercise-import-from-file',
    templateUrl: './programming-exercise-import-from-file.component.html',
    styleUrls: ['./programming-exercise-import-from-file.component.scss'],
})
export class ProgrammingExerciseImportFromFileComponent {
    @Input()
    exerciseType?: ExerciseType;
    titleKey: string;
    fileForImport?: File;
    faUpload = faUpload;
    @Input()
    courseId?: number;
    fromFile = true;

    constructor(private activeModal: NgbActiveModal, private alertService: AlertService, private programmingExerciseService: ProgrammingExerciseService) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    uploadExercise() {
        this.programmingExerciseService.uploadFileForImport(this.courseId as number, this.fileForImport as File).subscribe({
            next: (response: HttpResponse<ProgrammingExercise>) => {
                console.log(response.body);
                const exercise = response.body as ProgrammingExercise;
                exercise.course = response.body?.course;
                console.log(exercise);
                this.openImport(exercise);
            },
        });
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
