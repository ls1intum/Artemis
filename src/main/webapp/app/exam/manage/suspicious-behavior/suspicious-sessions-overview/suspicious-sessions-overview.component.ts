import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions.service';

@Component({
    selector: 'jhi-suspicious-sessions-overview',
    templateUrl: './suspicious-sessions-overview.component.html',
})
export class SuspiciousSessionsOverviewComponent implements OnInit {
    suspiciousSessions: SuspiciousExamSessions[] = [];

    constructor(private suspiciousSessionsService: SuspiciousSessionsService, private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.retrieveSuspiciousSessions();
    }

    private retrieveSuspiciousSessions() {
        const examId = Number(this.activatedRoute.snapshot.paramMap.get('examId'));
        const courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.suspiciousSessionsService.getSuspiciousSessions(courseId, examId).subscribe((res) => {
            this.suspiciousSessions = res;
        });
    }
}
