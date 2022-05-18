import { Component, OnDestroy, OnInit, OnChanges, Input } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { faCircleQuestion } from '@fortawesome/free-solid-svg-icons';
import { ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';

@Component({
    selector: 'jhi-exercise-hint-button-overlay',
    templateUrl: './exercise-hint-button-overlay.component.html',
    styleUrls: ['./exercise-hint-button-overlay.component.scss'],
})
export class ExerciseHintButtonOverlayComponent implements OnInit, OnChanges, OnDestroy {
    @Input()
    exerciseHints: ExerciseHint[];

    hasUsed = false;

    faCircleQuestion = faCircleQuestion;
    ngbModalRef?: NgbModalRef;

    constructor(private modalService: NgbModal) {}

    ngOnInit(): void {
        this.hasUsed = this.areNewAvailableHintsExisting();
    }

    ngOnChanges(): void {
        this.hasUsed = this.areNewAvailableHintsExisting();
    }

    areNewAvailableHintsExisting(): boolean {
        return this.exerciseHints?.filter((hint) => hint.hasUsed)?.length > 0 ?? false;
    }

    ngOnDestroy(): void {
        this.exerciseHints = [];
    }

    openModal() {
        this.ngbModalRef = this.modalService.open(ExerciseHintStudentDialogComponent as Component, { size: 'lg', backdrop: 'static' });
        this.ngbModalRef.componentInstance.exerciseHints = this.exerciseHints;
        this.ngbModalRef.result.then(
            () => {
                this.ngbModalRef = undefined;
            },
            () => {
                this.ngbModalRef = undefined;
            },
        );
    }
}
