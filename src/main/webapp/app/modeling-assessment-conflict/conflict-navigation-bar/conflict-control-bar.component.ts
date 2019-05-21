import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { Conflict, ConflictingResult } from 'app/modeling-assessment-editor/conflict.model';
import { ConflictResolutionState } from 'app/modeling-assessment-editor/conflict-resolution-state.enum';
import { JhiAlertService } from 'ng-jhipster';
import { Feedback } from 'app/entities/feedback';

@Component({
    selector: 'jhi-conflict-control-bar',
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
    @Output() submit = new EventEmitter<Conflict[]>();
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
        this.submit.emit(this.getEscalatedConflicts());
    }

    updateSelectedConflict() {
        this.selectedConflictChanged.emit(this.conflictIndex);
    }

    private getEscalatedConflicts() {
        let escalatedConflicts = new Array<Conflict>();
        for (let i = 0; i < this.conflictResolutionStates.length; i++) {
            if (this.conflictResolutionStates[i] === ConflictResolutionState.ESCALATED) {
                escalatedConflicts.push(this.conflicts[i]);
            }
        }
        return escalatedConflicts;
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
