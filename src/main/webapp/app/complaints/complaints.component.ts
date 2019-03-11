import { Component, OnInit, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './complaints.component.html',
    providers: [JhiAlertService]
})
export class ComplaintsComponent implements OnInit {
    complaintText = '';
    @Input() resultId: number;

    constructor(
        private jhiAlertService: JhiAlertService
    ) { }

    ngOnInit(): void {
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }
}
