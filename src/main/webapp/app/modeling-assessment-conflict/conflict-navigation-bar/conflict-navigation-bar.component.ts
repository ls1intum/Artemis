import { Component, OnInit, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { Conflict } from 'app/modeling-assessment-editor/conflict.model';

@Component({
    selector: 'jhi-conflict-navigation-bar',
    templateUrl: './conflict-navigation-bar.component.html',
    styleUrls: ['./conflict-navigation-bar.component.scss'],
})
export class ConflictNavigationBarComponent implements OnInit, OnChanges {
    private conflictIndex = 0;

    @Input() conflicts: Conflict[];
    @Input() enableSubmit: boolean;
    @Output() selectedConflictChanged = new EventEmitter<number>();
    @Output() save = new EventEmitter();
    @Output() submit = new EventEmitter();
    constructor() {}

    ngOnInit() {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.conflicts) {
            this.conflictIndex = 0;
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
}
