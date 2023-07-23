import { Component, Input } from '@angular/core';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';

@Component({
    selector: 'jhi-suspicious-sessions',
    templateUrl: './suspicious-sessions.component.html',
    styleUrls: ['./suspicious-sessions.component.scss'],
})
export class SuspiciousSessionsComponent {
    @Input() suspiciousSessions: SuspiciousExamSessions;
}
