import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Exercise } from 'app/entities/exercise.model';
import { AuxiliaryRepositoryService } from 'app/exercises/programming/manage/auxiliary-repository.service';
import { AuxiliaryRepository } from 'app/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-auxiliary-repository-dialog',
    templateUrl: './auxiliary-repository-dialog.component.html',
})
export class AuxiliaryRepositoryDialogComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;

    auxiliaryRepository: AuxiliaryRepository = new AuxiliaryRepository();
    isCreating = false;

    repositoryNamePattern = /^(?!(solution|exercise|tests)\b)\b\w+$/;

    constructor(
        private participationService: ParticipationService,
        private auxiliaryRepositoryService: AuxiliaryRepositoryService,
        private activeModal: NgbActiveModal,
        private eventManager: JhiEventManager,
    ) {}

    /**
     *
     */
    ngOnInit() {}

    /**
     * Close modal window.
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Create an auxiliary repository and add it to the template and solution build plan
     */
    create() {
        this.isCreating = true;
        this.subscribeToCreateResponse(this.auxiliaryRepositoryService.save(this.exercise, this.auxiliaryRepository));
    }

    /**
     * If http request is successful, pass it to onSaveSuccess, otherwise call onSaveError.
     * @param { Observable<HttpResponse<Result>> } result - Observable of Http request
     */
    private subscribeToCreateResponse(result: Observable<HttpResponse<AuxiliaryRepository>>) {
        result.subscribe(
            (res) => this.onCreateSuccess(res),
            () => this.onCreateError(),
        );
    }

    /**
     * Close modal window, indicate creating is done and broadcast that auxiliary repository is added.
     * @param { HttpResponse<Result> } result - Result of successful http request
     */
    onCreateSuccess(result: HttpResponse<AuxiliaryRepository>) {
        this.activeModal.close(result.body);
        this.isCreating = false;
        this.eventManager.broadcast({ name: 'repositoryAdded', content: 'Added an AuxiliaryRepository' });
        this.auxiliaryRepositoryService.updateAuxiliaryRepositories(this.exercise);
    }

    /**
     * Indicate that creating didn't work by setting isCreating to false.
     */
    onCreateError() {
        this.isCreating = false;
    }
}
