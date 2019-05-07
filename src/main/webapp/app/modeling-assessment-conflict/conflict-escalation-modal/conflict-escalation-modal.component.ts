import { Component, OnInit, Input } from '@angular/core';
import { User } from 'app/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-conflict-escalation-modal',
    templateUrl: './conflict-escalation-modal.component.html',
    styles: [],
})
export class ConflictEscalationModalComponent implements OnInit {
    @Input() tutorsEscalatingTo: User[];
    @Input() escalatedConflictsCount: number;

    constructor(public activeModal: NgbActiveModal) {}

    ngOnInit() {}
}
