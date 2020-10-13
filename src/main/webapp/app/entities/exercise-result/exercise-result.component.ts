import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { IExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from './exercise-result.service';
import { ExerciseResultDeleteDialogComponent } from './exercise-result-delete-dialog.component';

@Component({
    selector: 'jhi-exercise-result',
    templateUrl: './exercise-result.component.html',
})
export class ExerciseResultComponent implements OnInit, OnDestroy {
    exerciseResults?: IExerciseResult[];
    eventSubscriber?: Subscription;

    constructor(protected exerciseResultService: ExerciseResultService, protected eventManager: JhiEventManager, protected modalService: NgbModal) {}

    loadAll(): void {
        this.exerciseResultService.query().subscribe((res: HttpResponse<IExerciseResult[]>) => (this.exerciseResults = res.body || []));
    }

    ngOnInit(): void {
        this.loadAll();
        this.registerChangeInExerciseResults();
    }

    ngOnDestroy(): void {
        if (this.eventSubscriber) {
            this.eventManager.destroy(this.eventSubscriber);
        }
    }

    trackId(index: number, item: IExerciseResult): number {
        // eslint-disable-next-line @typescript-eslint/no-unnecessary-type-assertion
        return item.id!;
    }

    registerChangeInExerciseResults(): void {
        this.eventSubscriber = this.eventManager.subscribe('exerciseResultListModification', () => this.loadAll());
    }

    delete(exerciseResult: IExerciseResult): void {
        const modalRef = this.modalService.open(ExerciseResultDeleteDialogComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseResult = exerciseResult;
    }
}
