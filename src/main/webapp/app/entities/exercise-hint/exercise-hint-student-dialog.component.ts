import { Component, OnInit, Input, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';

@Component({
    selector: 'jhi-exercise-hint-student-dialog',
    templateUrl: './exercise-hint-student-dialog.component.html',
    styleUrls: ['./exercise-hint-student-dialog.scss'],
})
export class ExerciseHintStudentDialogComponent {
    @Input() exerciseHints: IExerciseHint[];

    constructor(public activeModal: NgbActiveModal) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }
}

@Component({
    selector: 'jhi-exercise-hint-student',
    template: `
        <fa-icon
            *ngIf="exerciseHints && exerciseHints.length"
            [icon]="['far', 'question-circle']"
            (click)="openModal()"
            class="hint-icon text-secondary"
            ngbTooltip="{{ 'artemisApp.exerciseHint.studentDialog.tooltip' | translate }}"
        ></fa-icon>
    `,
    styleUrls: ['./exercise-hint-student-dialog.scss'],
})
export class ExerciseHintStudentComponent implements OnInit, OnDestroy {
    @Input() exerciseId: number;
    exerciseHints: IExerciseHint[] | null;
    protected ngbModalRef: NgbModalRef | null;

    constructor(protected exerciseHintService: ExerciseHintService, protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    ngOnInit() {
        this.exerciseHintService
            .findByExerciseId(this.exerciseId)
            .pipe(
                map(({ body }) => body),
                tap((hints: IExerciseHint[]) => (this.exerciseHints = hints)),
                catchError(() => of()),
            )
            .subscribe();
    }

    openModal() {
        this.ngbModalRef = this.modalService.open(ExerciseHintStudentDialogComponent as Component, { size: 'lg', backdrop: 'static' });
        this.ngbModalRef.componentInstance.exerciseHints = this.exerciseHints;
        this.ngbModalRef.result.then(
            () => {
                this.ngbModalRef = null;
            },
            () => {
                this.ngbModalRef = null;
            },
        );
    }

    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
