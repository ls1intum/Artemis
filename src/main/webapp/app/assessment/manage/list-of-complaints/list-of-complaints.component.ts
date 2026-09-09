import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { Observable, Subscription, combineLatestWith } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SortService } from 'app/foundation/service/sort.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { TranslateService } from '@ngx-translate/core';
import { onError } from 'app/foundation/util/global.utils';
import { getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { faExclamationTriangle, faFolderOpen, faSort } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';

@Component({
    selector: 'jhi-complaint-list',
    templateUrl: './list-of-complaints.component.html',
    imports: [TranslateDirective, FormsModule, FaIconComponent, NgbTooltip, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisDurationFromSecondsPipe, SortDirective, SortByDirective],
    providers: [ArtemisDatePipe],
})
export class ListOfComplaintsComponent implements OnInit {
    complaintService = inject(ComplaintService);
    private alertService = inject(AlertService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private sortService = inject(SortService);
    private translateService = inject(TranslateService);
    private artemisDatePipe = inject(ArtemisDatePipe);
    private courseManagementService = inject(CourseManagementService);

    readonly ComplaintType = ComplaintType;

    // Async-loaded, template-bound state — signals so the list/loading state render under zoneless without markForCheck.
    readonly complaints = signal<Complaint[]>([]);
    readonly complaintType = signal<ComplaintType>(undefined!);

    private courseId!: number; // set in ngOnInit() from route params
    private exerciseId!: number; // set in ngOnInit() from route params
    private tutorId!: number; // set in ngOnInit() from route query params
    private examId?: number;
    readonly course = signal<Course>(undefined!);
    correctionRound?: number;
    complaintsSortingPredicate = 'id';
    complaintsReverseOrder = false;
    readonly complaintsToShow = signal<Complaint[]>([]);
    readonly showAddressedComplaints = signal(false);
    readonly allComplaintsForTutorLoaded = signal(false);
    readonly isLoadingAllComplaints = signal(false);
    readonly filterOption = signal<number | undefined>(undefined);
    readonly assessorFilter = signal<string | undefined>(undefined); // assessor login, only meaningful once allComplaintsForTutorLoaded()

    readonly loading = signal(true);
    // Cached so toggling "mine"/"all" after the first fetch is a pure client-side swap, no repeat request.
    // Cleared whenever the route identity (course/exercise/exam/tutor/type) changes.
    private ownComplaints?: Complaint[];
    private allTutorsComplaints?: Complaint[];
    private mineScopeSubscription?: Subscription;
    private allScopeSubscription?: Subscription;
    private routeIdentity?: string;
    private selectedComplaintScope: 'mine' | 'all' = 'mine';

    /** Distinct assessors among the currently loaded complaints, for the "all" scope's assessor filter. */
    readonly assessorOptions = computed(() => {
        const byLogin = new Map<string, string>();
        for (const complaint of this.complaints()) {
            const assessor = complaint.result?.assessor;
            byLogin.set(assessor?.login ?? '', assessor?.name ?? assessor?.login ?? '');
        }
        return Array.from(byLogin, ([login, name]) => ({ login, name })).sort((a, b) => a.name.localeCompare(b.name));
    });
    // Icons
    faSort = faSort;
    faFolderOpen = faFolderOpen;
    faExclamationTriangle = faExclamationTriangle;

    readonly FILTER_OPTION_ADDRESSED_COMPLAINTS = 4; // the number passed by the chart through the route indicating that only addressed complaints should be shown

    ngOnInit(): void {
        this.route.params.pipe(combineLatestWith(this.route.queryParams, this.route.data)).subscribe((result) => {
            const params = result[0];
            const queryParams = result[1];
            const data = result[2];

            const courseId = Number(params['courseId']);
            const exerciseId = Number(params['exerciseId']);
            const examId = Number(params['examId']);
            const tutorId = Number(queryParams['tutorId']);
            const complaintType = data.complaintType as ComplaintType;
            const identity = `${courseId}|${exerciseId}|${examId}|${tutorId}|${complaintType}`;

            if (this.routeIdentity !== undefined && this.routeIdentity !== identity) {
                this.resetComplaintScopeForRouteChange();
            }
            this.routeIdentity = identity;

            this.courseId = courseId;
            this.exerciseId = exerciseId;
            this.examId = examId;
            this.tutorId = tutorId;
            this.correctionRound = Number(queryParams['correctionRound']);
            if (queryParams['filterOption']) {
                this.filterOption.set(Number(queryParams['filterOption']));
            }

            this.complaintType.set(complaintType);

            this.loadComplaints();
        });
    }

    /** Drop mine/all caches and cancel in-flight fetches so a prior route cannot update this one. */
    private resetComplaintScopeForRouteChange = (): void => {
        this.mineScopeSubscription?.unsubscribe();
        this.mineScopeSubscription = undefined;
        this.allScopeSubscription?.unsubscribe();
        this.allScopeSubscription = undefined;
        this.ownComplaints = undefined;
        this.allTutorsComplaints = undefined;
        this.selectedComplaintScope = 'mine';
        this.allComplaintsForTutorLoaded.set(false);
        this.isLoadingAllComplaints.set(false);
        this.assessorFilter.set(undefined);
        this.filterOption.set(undefined);
        this.showAddressedComplaints.set(false);
    };

    loadComplaints() {
        let complaintResponse: Observable<HttpResponse<ComplaintDTO[]>>;

        if (this.tutorId) {
            if (this.exerciseId) {
                complaintResponse = this.complaintService.findAllByTutorIdForExerciseId(this.tutorId, this.exerciseId, this.complaintType());
            } else if (this.examId) {
                // TODO make exam complaints visible for tutors too
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId, this.complaintType());
            } else {
                complaintResponse = this.complaintService.findAllByTutorIdForCourseId(this.tutorId, this.courseId, this.complaintType());
            }
        } else {
            if (this.exerciseId) {
                complaintResponse = this.complaintService.findAllByExerciseId(this.exerciseId, this.complaintType());
            } else if (this.examId) {
                complaintResponse = this.complaintService.findAllByCourseIdAndExamId(this.courseId, this.examId);
            } else {
                complaintResponse = this.complaintService.findAllByCourseId(this.courseId, this.complaintType());
            }
        }
        this.mineScopeSubscription?.unsubscribe();
        this.mineScopeSubscription = this.subscribeToComplaintResponse(complaintResponse, 'mine');
        this.courseManagementService.find(this.courseId).subscribe((response) => {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            this.course.set(response?.body!);
        });
    }

    /**
     * Always updates the Mine/All cache for {@link scope}. Only writes the displayed list when that scope
     * is still selected and {@link routeIdentity} still matches the request's identity.
     */
    subscribeToComplaintResponse(complaintResponse: Observable<HttpResponse<ComplaintDTO[]>>, scope: 'mine' | 'all'): Subscription {
        const identity = this.routeIdentity;
        return complaintResponse.subscribe({
            next: (res) => {
                const complaints = res.body?.map((complaintDTO) => this.complaintService.convertComplaintFromServerInList(complaintDTO)) ?? [];
                if (scope === 'mine') {
                    this.ownComplaints = complaints;
                } else {
                    this.allTutorsComplaints = complaints;
                }
                if (identity !== this.routeIdentity || scope !== this.selectedComplaintScope) {
                    return;
                }
                this.complaints.set(complaints);
                if (scope === 'all') {
                    this.allComplaintsForTutorLoaded.set(true);
                }
                this.applyComplaintFilter();
            },
            error: (error: HttpErrorResponse) => {
                if (identity !== this.routeIdentity) {
                    return;
                }
                if (scope === 'all') {
                    this.isLoadingAllComplaints.set(false);
                    this.selectedComplaintScope = 'mine';
                }
                onError(this.alertService, error);
            },
            complete: () => {
                if (identity !== this.routeIdentity) {
                    return;
                }
                if (scope === 'all') {
                    this.isLoadingAllComplaints.set(false);
                } else {
                    this.loading.set(false);
                }
            },
        });
    }

    /**
     * Re-applies the addressed/unaddressed filter to the currently loaded {@link complaints} and updates {@link complaintsToShow}.
     */
    private applyComplaintFilter(): void {
        if (this.filterOption() === this.FILTER_OPTION_ADDRESSED_COMPLAINTS) {
            this.showAddressedComplaints.set(true);
        }

        let filtered: Complaint[];
        if (!this.showAddressedComplaints()) {
            filtered = this.complaints().filter((complaint) => complaint.accepted === undefined);
        } else if (this.filterOption() === this.FILTER_OPTION_ADDRESSED_COMPLAINTS) {
            filtered = this.complaints().filter((complaint) => complaint.accepted !== undefined);
        } else {
            filtered = this.complaints();
        }

        const assessorLogin = this.assessorFilter();
        if (assessorLogin !== undefined) {
            filtered = filtered.filter((complaint) => (complaint.result?.assessor?.login ?? '') === assessorLogin);
        }

        this.complaintsToShow.set(filtered);
    }

    openAssessmentEditor(complaint: Complaint) {
        if (!complaint || !complaint.result || !complaint.result.submission) {
            return;
        }

        const studentParticipation = complaint.result.submission.participation as StudentParticipation;
        const exercise = studentParticipation.exercise;
        const submissionId = complaint.result.submission.id;
        if (!exercise || !exercise.type || !submissionId) {
            return;
        }
        this.correctionRound = this.correctionRound || 0;
        if (this.complaintType() == ComplaintType.COMPLAINT && complaint.accepted) {
            this.correctionRound += 1;
        }
        const url = getLinkToSubmissionAssessment(
            exercise.type,
            this.courseId,
            exercise.id!,
            studentParticipation.id,
            submissionId,
            undefined, // even if the list of complaints are part of an exam, the assessment of non-exam exercises gets executed
            undefined,
            complaint.result.id,
        );
        void this.router.navigate(url, { queryParams: { 'correction-round': this.correctionRound } });
    }

    sortRows() {
        const sorted = [...this.complaintsToShow()];
        switch (this.complaintsSortingPredicate) {
            case 'responseTime':
                this.sortService.sortByFunction(sorted, (complaint) => this.complaintService.getResponseTimeInSeconds(complaint), this.complaintsReverseOrder);
                break;
            case 'lockStatus':
                this.sortService.sortByFunction(sorted, (complaint) => this.calculateComplaintLockStatus(complaint), this.complaintsReverseOrder);
                break;
            default:
                this.sortService.sortByProperty(sorted, this.complaintsSortingPredicate, this.complaintsReverseOrder);
        }
        this.complaintsToShow.set(sorted);
    }

    triggerAddressedComplaints() {
        this.showAddressedComplaints.update((value) => !value);

        if (!this.showAddressedComplaints()) {
            this.filterOption.set(undefined);
        }
        this.applyComplaintFilter();
    }

    /**
     * Switches between the tutor's own complaints and all complaints in the course.
     * The "all" list is fetched from the server once and cached, so switching back and forth afterwards is instant.
     */
    setComplaintScope(scope: 'mine' | 'all') {
        const wantAll = scope === 'all';
        if ((wantAll ? 'all' : 'mine') === this.selectedComplaintScope) {
            return;
        }

        if (!wantAll) {
            this.allScopeSubscription?.unsubscribe();
            this.allScopeSubscription = undefined;
            this.isLoadingAllComplaints.set(false);
            this.selectedComplaintScope = 'mine';
            this.assessorFilter.set(undefined);
            this.complaints.set(this.ownComplaints ?? []);
            this.allComplaintsForTutorLoaded.set(false);
            this.applyComplaintFilter();
            return;
        }

        this.selectedComplaintScope = 'all';
        if (this.allTutorsComplaints) {
            this.complaints.set(this.allTutorsComplaints);
            this.allComplaintsForTutorLoaded.set(true);
            this.applyComplaintFilter();
            return;
        }

        if (this.ownComplaints === undefined) {
            this.ownComplaints = this.complaints();
        }
        this.isLoadingAllComplaints.set(true);
        this.allScopeSubscription?.unsubscribe();
        this.allScopeSubscription = this.subscribeToComplaintResponse(
            this.complaintService.findAllWithoutStudentInformationForCourseId(this.courseId, this.complaintType()),
            'all',
        );
    }

    onAssessorFilterChange(login: string | undefined) {
        this.assessorFilter.set(login);
        this.applyComplaintFilter();
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
        this.complaintsToShow.set(complaints.filter((complaint) => complaint.accepted === undefined));
    }

    resetFilterOptions(): void {
        this.updateFilteredComplaints(this.complaints());
        this.showAddressedComplaints.set(false);
        this.filterOption.set(undefined);
    }
}
