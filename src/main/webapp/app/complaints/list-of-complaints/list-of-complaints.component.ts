import { Component, OnInit } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { SortService } from 'app/shared/service/sort.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TranslateService } from '@ngx-translate/core';
import { onError } from 'app/shared/util/global.utils';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { faExclamationTriangle, faFolderOpen, faSort } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-complaint-list',
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
    private examId?: number;
    correctionRound?: number;
    complaintsSortingPredicate = 'id';
    complaintsReverseOrder = false;
    complaintsToShow: Complaint[] = [];
    showAddressedComplaints = false;
    allComplaintsForTutorLoaded = false;
    isLoadingAllComplaints = false;
    filterOption?: number;

    loading = true;
    // Icons
    faSort = faSort;
    faFolderOpen = faFolderOpen;
    faExclamationTriangle = faExclamationTriangle;

    readonly FilterOptionAddressedComplaints = 4; // the number passed by the chart through the route indicating that only addressed complaints should be shown

    constructor(
        public complaintService: ComplaintService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private router: Router,
        private modalService: NgbModal,
        private sortService: SortService,
        private translateService: TranslateService,
        private artemisDatePipe: ArtemisDatePipe,
    ) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.exerciseId = Number(params['exerciseId']);
            this.examId = Number(params['examId']);
        });
        this.route.queryParams.subscribe((queryParams) => {
            this.tutorId = Number(queryParams['tutorId']);
            this.correctionRound = Number(queryParams['correctionRound']);
        });
        this.route.data.subscribe((data) => (this.complaintType = data.complaintType));
        this.route.queryParams.subscribe((queryParams) => {
            if (queryParams['filterOption']) {
                this.filterOption = Number(queryParams['filterOption']);
            }
        });
        this.loadComplaints();
    }

    loadComplaints() {
        let complaintResponse: Observable<HttpResponse<Complaint[]>>;

        if (this.tutorId) {
            if (this.exerciseId) {
                complaintResponse = this.complaintService.findAllByTutorIdForExerciseId(this.tutorId, this.exerciseId, this.complaintType);
            } else if (this.examId) {
                // TODO make exam complaints visible for tutors too
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId, this.complaintType);
            } else {
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId, this.complaintType);
            }
        } else {
            if (this.exerciseId) {
                complaintResponse = this.complaintService.findAllByExerciseId(this.exerciseId, this.complaintType);
            } else if (this.examId) {
                complaintResponse = this.complaintService.findAllByCourseIdAndExamId(this.courseId, this.examId);
            } else {
                complaintResponse = this.complaintService.findAllByCourseId(this.courseId, this.complaintType);
            }
        }
        this.subscribeToComplaintResponse(complaintResponse);
    }

    subscribeToComplaintResponse(complaintResponse: Observable<HttpResponse<Complaint[]>>) {
        complaintResponse.subscribe({
            next: (res) => {
                this.complaints = res.body!;
                if (this.filterOption === this.FilterOptionAddressedComplaints) {
                    this.showAddressedComplaints = true;
                }

                if (!this.showAddressedComplaints) {
                    this.complaintsToShow = this.complaints.filter((complaint) => complaint.accepted === undefined);
                } else if (this.filterOption === this.FilterOptionAddressedComplaints) {
                    this.complaintsToShow = this.complaints.filter((complaint) => complaint.accepted !== undefined);
                } else {
                    this.complaintsToShow = this.complaints;
                }

                if (this.complaints.some((complaint) => complaint.student)) {
                    this.hasStudentInformation = true;
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
            complete: () => (this.loading = false),
        });
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
        this.correctionRound = this.correctionRound || 0;
        if (complaint.accepted) {
            this.correctionRound += 1;
        }
        const url = getLinkToSubmissionAssessment(
            exercise.type,
            this.courseId,
            exercise.id!,
            studentParticipation.id,
            submissionId,
            0, // even if the list of complaints are part of an exam, the assessment of non-exam exercises gets executed
            0,
            complaint.result.id,
        );
        this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound } });
    }

    sortRows() {
        switch (this.complaintsSortingPredicate) {
            case 'responseTime':
                this.sortService.sortByFunction(this.complaintsToShow, (complaint) => this.complaintService.getResponseTimeInSeconds(complaint), this.complaintsReverseOrder);
                break;
            case 'lockStatus':
                this.sortService.sortByFunction(this.complaintsToShow, (complaint) => this.calculateComplaintLockStatus(complaint), this.complaintsReverseOrder);
                break;
            default:
                this.sortService.sortByProperty(this.complaintsToShow, this.complaintsSortingPredicate, this.complaintsReverseOrder);
        }
    }

    triggerAddressedComplaints() {
        this.showAddressedComplaints = !this.showAddressedComplaints;

        if (this.showAddressedComplaints) {
            this.complaintsToShow = this.complaints;
        } else {
            this.resetFilterOptions();
        }
    }

    /**
     * Used to lazy-load all complaints from the server for a tutor or editor.
     */
    triggerShowAllComplaints() {
        this.isLoadingAllComplaints = true;
        let complaintResponse: Observable<HttpResponse<Complaint[]>>;
        complaintResponse = this.complaintService.findAllWithoutStudentInformationForCourseId(this.courseId, this.complaintType);
        this.subscribeToComplaintResponse(complaintResponse);
        this.isLoadingAllComplaints = false;
        this.allComplaintsForTutorLoaded = true;
    }

    calculateComplaintLockStatus(complaint: Complaint) {
        if (complaint.complaintResponse && this.complaintService.isComplaintLocked(complaint)) {
            if (this.complaintService.isComplaintLockedByLoggedInUser(complaint)) {
                const endDate = this.artemisDatePipe.transform(complaint.complaintResponse?.lockEndDate);
                return this.translateService.instant('artemisApp.locks.lockInformationYou', {
                    endDate,
                });
            } else {
                const endDate = this.artemisDatePipe.transform(complaint.complaintResponse?.lockEndDate);
                const user = complaint.complaintResponse?.reviewer?.login;

                return this.translateService.instant('artemisApp.locks.lockInformation', {
                    endDate,
                    user,
                });
            }
        } else {
            return this.translateService.instant('artemisApp.locks.notUnlocked');
        }
    }

    updateFilteredComplaints(complaints: Complaint[]) {
        this.complaintsToShow = complaints.filter((complaint) => complaint.accepted === undefined);
    }

    resetFilterOptions(): void {
        this.updateFilteredComplaints(this.complaints);
        this.showAddressedComplaints = false;
        this.filterOption = undefined;
    }
}
