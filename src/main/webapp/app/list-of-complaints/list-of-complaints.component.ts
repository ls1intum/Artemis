import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint/complaint.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { ExerciseType } from 'app/entities/exercise/exercise.model';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/programming-assessment/manual-result/programming-assessment-manual-result-dialog.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { cloneDeep } from 'lodash';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './list-of-complaints.component.html',
    providers: [JhiAlertService],
})
export class ListOfComplaintsComponent implements OnInit {
    public complaints: Complaint[] = [];
    public hasStudentInformation = false;
    public complaintType: ComplaintType;
    ComplaintType = ComplaintType;

    private courseId: number;
    private exerciseId: number;
    private tutorId: number;

    complaintsSortingPredicate = 'id';
    complaintsReverseOrder = false;
    complaintsToShow: Complaint[] = [];
    showAddressedComplaints = false;

    loading = true;

    constructor(
        private complaintService: ComplaintService,
        private jhiAlertService: JhiAlertService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        this.route.queryParams.subscribe(queryParams => {
            this.courseId = Number(queryParams['courseId']);
            this.exerciseId = Number(queryParams['exerciseId']);
            this.tutorId = Number(queryParams['tutorId']);
        });
        this.route.data.subscribe(data => (this.complaintType = data.complaintType));
        this.loadComplaints();
    }

    loadComplaints() {
        let complaintResponse: Observable<HttpResponse<Complaint[]>>;

        if (this.tutorId) {
            if (this.courseId) {
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId, this.complaintType);
            } else {
                complaintResponse = this.complaintService.findAllByTutorIdForExerciseId(this.tutorId, this.exerciseId, this.complaintType);
            }
        } else {
            if (this.courseId) {
                complaintResponse = this.complaintService.findAllByCourseId(this.courseId, this.complaintType);
            } else {
                complaintResponse = this.complaintService.findAllByExerciseId(this.exerciseId, this.complaintType);
            }
        }

        complaintResponse.subscribe(
            res => {
                this.complaints = res.body!;
                this.complaintsToShow = this.complaints.filter(complaint => complaint.accepted === undefined);

                if (this.complaints.length > 0 && this.complaints[0].student) {
                    this.hasStudentInformation = true;
                }
            },
            (err: HttpErrorResponse) => this.onError(err.message),
            () => (this.loading = false),
        );
    }

    openAssessmentEditor(complaint: Complaint) {
        if (!complaint || !complaint.result || !complaint.result.participation || !complaint.result.submission) {
            return;
        }

        const studentParticipation = complaint.result.participation as StudentParticipation;
        const exercise = studentParticipation.exercise;
        const submissionId = complaint.result.submission.id;

        if (!exercise || !exercise.type || !submissionId) {
            return;
        }

        let route: string;
        if (exercise.type === ExerciseType.TEXT) {
            route = `/text/${exercise.id}/assessment/${submissionId}`;
        } else if (exercise.type === ExerciseType.MODELING) {
            route = `/modeling-exercise/${exercise.id}/submissions/${submissionId}/assessment`;
        } else if (exercise.type === ExerciseType.FILE_UPLOAD) {
            route = `/file-upload-exercise/${exercise.id}/submission/${submissionId}/assessment`;
        } else if (exercise.type === ExerciseType.PROGRAMMING) {
            const modalRef: NgbModalRef = this.modalService.open(ProgrammingAssessmentManualResultDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
            modalRef.componentInstance.participationId = studentParticipation.id;
            modalRef.componentInstance.exercise = exercise;
            modalRef.componentInstance.result = cloneDeep(complaint.result);
            modalRef.componentInstance.onResultModified.subscribe(() => this.loadComplaints());
            modalRef.result.then(
                _ => this.loadComplaints(),
                () => {},
            );
            return;
        }
        this.router.navigate([route!]);
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error('error.http.400', null, undefined);
    }

    back() {
        this.location.back();
    }

    callback() {}

    triggerAddressedComplaints() {
        this.showAddressedComplaints = !this.showAddressedComplaints;

        if (this.showAddressedComplaints) {
            this.complaintsToShow = this.complaints;
        } else {
            this.complaintsToShow = this.complaints.filter(complaint => complaint.accepted === undefined);
        }
    }

    shouldHighlightComplaint(complaint: Complaint) {
        // Reviewed complaints shouldn't be highlight
        if (complaint.accepted !== undefined) {
            return false;
        }

        const complaintSubmittedTime = complaint.submittedTime;
        if (complaintSubmittedTime) {
            return moment().diff(complaintSubmittedTime, 'days') > 7; // We highlight complaints older than a week
        }

        return false;
    }
}
