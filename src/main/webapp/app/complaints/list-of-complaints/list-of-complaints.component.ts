import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { AlertService } from 'app/core/alert/alert.service';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingAssessmentManualResultDialogComponent } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result-dialog.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { cloneDeep } from 'lodash';

@Component({
    // TODO this selector is used twice which is not good!!!
    selector: 'jhi-complaint-form',
    templateUrl: './list-of-complaints.component.html',
    providers: [],
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
        private jhiAlertService: AlertService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.exerciseId = Number(params['exerciseId']);
        });
        this.route.queryParams.subscribe((queryParams) => {
            this.tutorId = Number(queryParams['tutorId']);
        });
        this.route.data.subscribe((data) => (this.complaintType = data.complaintType));
        this.loadComplaints();
    }

    loadComplaints() {
        let complaintResponse: Observable<HttpResponse<Complaint[]>>;

        if (this.tutorId) {
            if (this.exerciseId) {
                complaintResponse = this.complaintService.findAllByTutorIdForExerciseId(this.tutorId, this.exerciseId, this.complaintType);
            } else {
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId, this.complaintType);
            }
        } else {
            if (this.exerciseId) {
                complaintResponse = this.complaintService.findAllByExerciseId(this.exerciseId, this.complaintType);
            } else {
                complaintResponse = this.complaintService.findAllByCourseId(this.courseId, this.complaintType);
            }
        }

        complaintResponse.subscribe(
            (res) => {
                this.complaints = res.body!;
                this.complaintsToShow = this.complaints.filter((complaint) => complaint.accepted === undefined);

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

        switch (exercise.type) {
            case ExerciseType.TEXT:
            case ExerciseType.MODELING:
            case ExerciseType.FILE_UPLOAD:
                const route = `/course-management/${this.courseId}/${exercise.type}-exercises/${exercise.id}/submissions/${submissionId}/assessment`;
                this.router.navigate([route]);
                return;
            case ExerciseType.PROGRAMMING:
                const modalRef: NgbModalRef = this.modalService.open(ProgrammingAssessmentManualResultDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
                modalRef.componentInstance.participationId = studentParticipation.id;
                modalRef.componentInstance.exercise = exercise;
                modalRef.componentInstance.result = cloneDeep(complaint.result);
                modalRef.componentInstance.onResultModified.subscribe(() => this.loadComplaints());
                modalRef.result.then(
                    (_) => this.loadComplaints(),
                    () => {},
                );
                return;
        }
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
            this.complaintsToShow = this.complaints.filter((complaint) => complaint.accepted === undefined);
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
