import { Component, OnInit, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { Conflict } from 'app/modeling-assessment-editor/conflict.model';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-conflict-navigation-bar',
    templateUrl: './conflict-control-bar.component.html',
    styleUrls: ['./conflict-control-bar.component.scss'],
})
export class ConflictControlBarComponent implements OnInit, OnChanges {
    private conflictIndex = 0;
    conflictsAllHandled = false;

    @Input() conflicts: Conflict[];
    @Input() conflictResolutionStates: ConflictResolutionState[];
    @Output() selectedConflictChanged = new EventEmitter<number>();
    @Output() save = new EventEmitter();
    @Output() submit = new EventEmitter();
    constructor(private jhiAlertService: JhiAlertService) {}

    ngOnInit() {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.conflicts) {
            this.conflictIndex = 0;
        }
        if (changes.conflictResolutionStates) {
            this.conflictResolutionStates = changes.conflictResolutionStates.currentValue;
            if (!this.conflictsAllHandled) {
                this.updateOverallResolutionState();
            }
        }
    }
    onNextConflict() {
        this.conflictIndex = this.conflictIndex < this.conflicts.length - 1 ? ++this.conflictIndex : this.conflictIndex;
        this.updateSelectedConflict();
    }

    onPrevConflict() {
        this.conflictIndex = this.conflictIndex > 0 ? --this.conflictIndex : this.conflictIndex;
        this.updateSelectedConflict();
    }

    onSave() {
        this.save.emit();
    }

    onSubmit() {
        this.submit.emit();
    }

    updateSelectedConflict() {
        this.selectedConflictChanged.emit(this.conflictIndex);
    }

    private updateOverallResolutionState() {
        for (const state of this.conflictResolutionStates) {
            if (state === ConflictResolutionState.UNHANDLED) {
                this.conflictsAllHandled = false;
                return;
            }
        }
        if (!this.conflictsAllHandled) {
            this.jhiAlertService.clear();
            this.jhiAlertService.success('modelingAssessmentConflict.messages.conflictsResolved');
        }
        this.conflictsAllHandled = true;
    }
}
