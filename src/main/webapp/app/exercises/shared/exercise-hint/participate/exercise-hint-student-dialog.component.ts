import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

import { ExerciseHintService } from '../manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';

/**
 * This component is a modal that shows the exercise's hints.
 */
@Component({
    selector: 'jhi-exercise-hint-student-dialog',
    templateUrl: './exercise-hint-student-dialog.component.html',
    styleUrls: ['./exercise-hint-student-dialog.scss'],
})
export class ExerciseHintStudentDialogComponent {
    @Input() exerciseHints: ExerciseHint[];

    constructor(public activeModal: NgbActiveModal) {}

    /**
     * Dismisses the modal
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}

/**
 * This component is a question mark icon the user can click on the see the provided exercise's hints.
 */
@Component({
    selector: 'jhi-exercise-hint-student',
    template: `
        <fa-icon
            *ngIf="exerciseHints && exerciseHints.length"
            [icon]="farQuestionCircle"
            (click)="openModal()"
            class="hint-icon text-secondary"
            ngbTooltip="{{ 'artemisApp.exerciseHint.studentDialog.tooltip' | artemisTranslate }}"
        ></fa-icon>
    `,
    styleUrls: ['./exercise-hint-student-dialog.scss'],
})
export class ExerciseHintStudentComponent implements OnInit, OnDestroy {
    @Input() exerciseId: number;
    @Input() exerciseHints?: ExerciseHint[];
    protected ngbModalRef: NgbModalRef | null;

    // Icons
    farQuestionCircle = faQuestionCircle;

    constructor(protected exerciseHintService: ExerciseHintService, protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    /**
     * Fetches all exercise hints for an exercise from the server
     */
    ngOnInit() {
        if (!this.exerciseHints) {
            this.exerciseHintService
                .findByExerciseId(this.exerciseId)
                .pipe(
                    map(({ body }) => body),
                    tap((hints: ExerciseHint[]) => (this.exerciseHints = hints)),
                    catchError(() => of()),
                )
                .subscribe();
        }
    }

    /**
     * Open the exercise hint student dialog (see component above).
     */
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

    /**
     * Resets the modal ref
     */
    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
