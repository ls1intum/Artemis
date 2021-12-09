import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { AlertService } from 'app/core/util/alert.service';
import { faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-edit-selected',
    templateUrl: './programming-exercise-edit-selected.component.html',
})
export class ProgrammingExerciseEditSelectedComponent implements OnInit {
    newProgrammingExercise: ProgrammingExercise;
    selectedProgrammingExercises: ProgrammingExercise[];

    isSaving = false;
    savedExercises = 0;
    failedExercises: string[] = [];
    failureOccurred = false;
    private translationBasePath = 'artemisApp.programmingExercise.';
    notificationText?: string;

    // Icons
    faSave = faSave;

    constructor(
        private activeModal: NgbActiveModal,
        private translateService: TranslateService,
        private alertService: AlertService,
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseSimulationService: ProgrammingExerciseSimulationService,
    ) {}

    ngOnInit(): void {
        this.notificationText = undefined;
        this.newProgrammingExercise = new ProgrammingExercise(undefined, undefined);
    }

    saveAll() {
        // If no release date is set, we warn the user.
        if (!this.newProgrammingExercise.releaseDate) {
            const confirmNoReleaseDate = this.translateService.instant(this.translationBasePath + 'noReleaseDateWarning');
            if (!window.confirm(confirmNoReleaseDate)) {
                return;
            }
        }
        this.isSaving = true;
        this.selectedProgrammingExercises.forEach((programmingExercise) => {
            programmingExercise = this.setNewValues(programmingExercise);
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.subscribeToSaveResponse(programmingExercise.title, this.programmingExerciseService.updateTimeline(programmingExercise, requestOptions));
        });
    }

    /**
     * Replace the programming exercise values with the new given ones
     * @param programmingExercise to update
     * @return the programming exercise with updated values
     */
    setNewValues(programmingExercise: ProgrammingExercise) {
        programmingExercise.releaseDate = this.newProgrammingExercise.releaseDate;
        programmingExercise.dueDate = this.newProgrammingExercise.dueDate;
        programmingExercise.buildAndTestStudentSubmissionsAfterDueDate = this.newProgrammingExercise.buildAndTestStudentSubmissionsAfterDueDate;
        programmingExercise.assessmentType = this.newProgrammingExercise.assessmentType;
        programmingExercise.assessmentDueDate = this.newProgrammingExercise.assessmentDueDate;
        return programmingExercise;
    }

    private subscribeToSaveResponse(exerciseTitle: string | undefined, result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe(
            () => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res, exerciseTitle),
        );
    }

    private onSaveSuccess() {
        this.savedExercises++;
        if (this.savedExercises === this.selectedProgrammingExercises.length) {
            this.isSaving = false;
            if (!this.failureOccurred) {
                this.activeModal.close();
            }
        }
    }

    private onSaveError(error: HttpErrorResponse, exerciseTitle?: string | undefined) {
        exerciseTitle = exerciseTitle ?? 'undefined exercise';
        this.failureOccurred = true;
        this.failedExercises.push(exerciseTitle);
        this.savedExercises++;
        if (this.savedExercises === this.selectedProgrammingExercises.length) {
            this.isSaving = false;
        }
        window.scrollTo(0, 0);
    }

    closeModal() {
        this.activeModal.close();
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
