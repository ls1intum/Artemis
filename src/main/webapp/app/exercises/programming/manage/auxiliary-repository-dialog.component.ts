import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Exercise } from 'app/entities/exercise.model';
import { SCORE_PATTERN } from 'app/app.constants';

import { AuxiliaryRepositoryService } from 'app/exercises/programming/manage/auxiliary-repository.service';
import { AuxiliaryRepository } from 'app/entities/auxiliary-repository-model';

@Component({
    selector: 'jhi-auxiliary-repository-dialog',
    templateUrl: './auxiliary-repository-dialog.component.html',
})
export class AuxiliaryRepositoryDialogComponent implements OnInit {
    readonly SCORE_PATTERN = SCORE_PATTERN;

    @Input() exercise: Exercise;

    auxiliaryRepository: AuxiliaryRepository;
    isSaving = false;

    repositoryNamePattern = /^(?!(solution|exercise|tests)\b)\b\w+$/;

    constructor(
        private participationService: ParticipationService,
        private auxiliaryRepositoryService: AuxiliaryRepositoryService,
        private activeModal: NgbActiveModal,
        private eventManager: JhiEventManager,
    ) {}

    /**
     * Initialize Component by calling a helper that generates an initial manual result.
     */
    ngOnInit() {
        this.initializeForResultCreation();
    }

    /**
     * Initialize result with initial manual result.
     */
    initializeForResultCreation() {}

    /**
     * Close modal window.
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Add manual feedbacks to the result and create external submission.
     */
    save() {
        //alert(this.exercise.id)
        alert(this.auxiliaryRepository.name);
        this.isSaving = true;
        this.subscribeToSaveResponse(this.auxiliaryRepositoryService.save(this.exercise, this.auxiliaryRepository));
    }

    /**
     * If http request is successful, pass it to onSaveSuccess, otherwise call onSaveError.
     * @param { Observable<HttpResponse<Result>> } result - Observable of Http request
     */
    private subscribeToSaveResponse(result: Observable<HttpResponse<AuxiliaryRepository>>) {
        result.subscribe(
            (res) => this.onSaveSuccess(res),
            () => this.onSaveError(),
        );
    }

    /**
     * Close modal window, indicate saving is done and broadcast that manual result is added.
     * @param { HttpResponse<Result> } result - Result of successful http request
     */
    onSaveSuccess(result: HttpResponse<AuxiliaryRepository>) {
        this.activeModal.close(result.body);
        this.isSaving = false;
        this.eventManager.broadcast({ name: 'resultListModification', content: 'Added an AuxiliaryRepository' });
    }

    /**
     * Indicate that saving didn't work by setting isSaving to false.
     */
    onSaveError() {
        this.isSaving = false;
    }
}
