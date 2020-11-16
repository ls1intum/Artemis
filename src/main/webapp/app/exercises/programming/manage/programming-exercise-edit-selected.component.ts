import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseSimulationService } from 'app/exercises/programming/manage/services/programming-exercise-simulation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-programming-exercise-edit-selected',
    templateUrl: './programming-exercise-edit-selected.component.html',
})
export class ProgrammingExerciseEditSelectedComponent implements OnInit {
    newProgrammingExercise: ProgrammingExercise;
    selectedProgrammingExercises: ProgrammingExercise[];

    isSaving = false;
    private translationBasePath = 'artemisApp.programmingExercise.';
    notificationText: string | null;

    constructor(
        private activeModal: NgbActiveModal,
        private translateService: TranslateService,
        private jhiAlertService: JhiAlertService,
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseSimulationService: ProgrammingExerciseSimulationService,
    ) {}

    ngOnInit(): void {
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
            if (programmingExercise.id !== undefined) {
                const requestOptions = {} as any;
                if (this.notificationText) {
                    requestOptions.notificationText = this.notificationText;
                }
                this.subscribeToSaveResponse(this.programmingExerciseService.update(programmingExercise, requestOptions));
            } else if (programmingExercise.noVersionControlAndContinuousIntegrationAvailable) {
                // only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
                this.subscribeToSaveResponse(this.programmingExerciseSimulationService.automaticSetupWithoutConnectionToVCSandCI(programmingExercise));
            } else {
                this.subscribeToSaveResponse(this.programmingExerciseService.automaticSetup(programmingExercise));
            }
        });
        this.isSaving = false;
        this.activeModal.close();
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
        programmingExercise.assessmentDueDate = this.newProgrammingExercise.assessmentDueDate;
        return programmingExercise;
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe(
            () => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError(res),
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
    }

    private onSaveError(error: HttpErrorResponse) {
        const errorMessage = error.headers.get('X-artemisApp-alert')!;
        // TODO: this is a workaround to avoid translation not found issues. Provide proper translations
        const jhiAlert = this.jhiAlertService.error(errorMessage);
        jhiAlert.msg = errorMessage;
        this.isSaving = false;
        window.scrollTo(0, 0);
    }

    /**
     * Closes the modal in which the import component is opened by dismissing it
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
