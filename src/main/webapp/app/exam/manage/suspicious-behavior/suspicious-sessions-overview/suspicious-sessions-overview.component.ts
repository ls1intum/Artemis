import { Component, OnInit } from '@angular/core';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';
import { cloneDeep } from 'lodash-es';

@Component({
    selector: 'jhi-suspicious-sessions-overview',
    templateUrl: './suspicious-sessions-overview.component.html',
})
export class SuspiciousSessionsOverviewComponent implements OnInit {
    suspiciousSessions: SuspiciousExamSessions[] = [];

    ngOnInit(): void {
        this.suspiciousSessions = cloneDeep(history.state.suspiciousSessions);
    }
}
