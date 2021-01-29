import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ParticipantScoreDTO, ParticipantScoresService } from 'app/course/course-participant-scores/participant-scores.service';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { finalize } from 'rxjs/operators';

@Component({
    selector: 'jhi-course-participant-scores',
    templateUrl: './course-participant-scores.component.html',
})
export class CourseParticipantScoresComponent implements OnInit {
    courseId: number;
    isLoading: boolean;
    participantScores: ParticipantScoreDTO[] = [];

    constructor(private participantScoreService: ParticipantScoresService, private activatedRoute: ActivatedRoute, private alertService: JhiAlertService) {}

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    loadData() {
        this.isLoading = true;
        this.participantScoreService
            .findAllOfCourse(this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (participantScoresResponse) => {
                    this.participantScores = participantScoresResponse.body!;
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }

    extractParticipantName = (participantScoreDTO: ParticipantScoreDTO) => {
        if (participantScoreDTO.userName) {
            return `${participantScoreDTO.userName}`;
        } else {
            return `${participantScoreDTO.teamName}`;
        }
    };
}
