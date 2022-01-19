import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

import { TextHintService } from '../manage/text-hint.service';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { TextHint } from 'app/entities/hestia/text-hint-model';

/**
 * This component is a modal that shows the exercise's hints.
 */
@Component({
    selector: 'jhi-exercise-hint-student-dialog',
    templateUrl: './exercise-hint-student-dialog.component.html',
    styleUrls: ['./exercise-hint-student-dialog.scss'],
})
export class TextHintStudentDialogComponent {
    @Input() textHints: TextHint[];

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
            *ngIf="textHints && textHints.length"
            [icon]="farQuestionCircle"
            (click)="openModal()"
            class="hint-icon text-secondary"
            ngbTooltip="{{ 'artemisApp.exerciseHint.studentDialog.tooltip' | artemisTranslate }}"
        ></fa-icon>
    `,
    styleUrls: ['./exercise-hint-student-dialog.scss'],
})
export class TextHintStudentComponent implements OnInit, OnDestroy {
    @Input() exerciseId: number;
    @Input() textHints?: TextHint[];
    protected ngbModalRef: NgbModalRef | null;

    // Icons
    farQuestionCircle = faQuestionCircle;

    constructor(protected textHintService: TextHintService, protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    /**
     * Fetches all text hints for an exercise from the server
     */
    ngOnInit() {
        if (!this.textHints) {
            this.textHintService
                .findByExerciseId(this.exerciseId)
                .pipe(
                    map(({ body }) => body),
                    tap((hints: TextHint[]) => (this.textHints = hints)),
                    catchError(() => of()),
                )
                .subscribe();
        }
    }

    /**
     * Open the text hint student dialog (see component above).
     */
    openModal() {
        this.ngbModalRef = this.modalService.open(TextHintStudentDialogComponent as Component, { size: 'lg', backdrop: 'static' });
        this.ngbModalRef.componentInstance.textHints = this.textHints;
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
