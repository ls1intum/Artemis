import { Component, Input, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SuspiciousExamSessions } from 'app/entities/exam-session.model';
import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.service';

@Component({
    selector: 'jhi-suspicious-sessions',
    templateUrl: './suspicious-sessions.component.html',
    styleUrls: ['./suspicious-sessions.component.scss'],
})
export class SuspiciousSessionsComponent implements OnInit {
    suspiciousSessions: SuspiciousExamSessions[] = [];
    private courseId: number;
    private examId: number;

    constructor(private suspiciousSessionsService: SuspiciousSessionsService, private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.retrieveSuspiciousSessions();
    }

    private retrieveSuspiciousSessions() {
        this.examId = Number(this.activatedRoute.snapshot.paramMap.get('examId'));
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.suspiciousSessionsService.getSuspiciousSessions(this.courseId, this.examId).subscribe((res) => {
            this.suspiciousSessions = res;
        });
    }
}
