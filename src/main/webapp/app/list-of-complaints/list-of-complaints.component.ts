import { Component, OnInit, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint } from 'app/entities/complaint';
import { Result } from 'app/entities/result';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './list-of-complaints.component.html',
    providers: [JhiAlertService],
})
export class ListOfComplaintsComponent implements OnInit {
    public complaints: Complaint[] = [];

    private courseId: number;
    private exerciseId: number;
    private tutorId: number;

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.route.queryParams.subscribe(queryParams => {
            this.courseId = Number(queryParams.get('courseId'));
            this.exerciseId = Number(queryParams.get('exerciseId'));
            this.tutorId = Number(queryParams.get('exerciseId'));
        });

        let complaintResponse: Observable<HttpResponse<Complaint[]>>;

        if (this.tutorId) {
            if (this.courseId) {
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId);
            } else {
                complaintResponse = this.complaintService.findAllByTutorIdForExerciseId(this.tutorId, this.exerciseId);
            }
        } else {
            if (this.courseId) {
                complaintResponse = this.complaintService.findAllByCourseId(this.courseId);
            } else {
                complaintResponse = this.complaintService.findAllByExerciseId(this.exerciseId);
            }
        }

        complaintResponse.subscribe(res => (this.complaints = res.body), (err: HttpErrorResponse) => this.onError(err.message));
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error('error.http.400', null, null);
    }
}
