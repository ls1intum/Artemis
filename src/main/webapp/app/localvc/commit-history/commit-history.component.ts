import { Component, ContentChild, TemplateRef } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter, switchMap } from 'rxjs/operators';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { programmingExerciseFail, programmingExerciseSuccess } from 'app/guided-tour/tours/course-exercise-detail-tour';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { getFirstResultWithComplaintFromResults } from 'app/entities/submission.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Complaint } from 'app/entities/complaint.model';
import { SubmissionPolicyService } from 'app/exercises/programming/manage/services/submission-policy.service';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/entities/course.model';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';

@Component({
    selector: 'jhi-commit-history',
    templateUrl: './commit-history.component.html',
    styleUrl: './commit-history.component.scss',
})
export class CommitHistoryComponent {
    readonly PROGRAMMING = ExerciseType.PROGRAMMING;
    readonly dayjs = dayjs;
    currentUser: User;

    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    public exerciseId: number;
    public courseId: number;
    public course: Course;
    public exercise?: Exercise;
    public resultWithComplaint?: Result;
    public latestRatedResult?: Result;
    public complaint?: Complaint;
    public sortedHistoryResults: Result[];
    public exerciseCategories: ExerciseCategory[];
    private participationUpdateListener: Subscription;
    private submissionSubscription: Subscription;
    studentParticipations: StudentParticipation[] = [];
    gradedStudentParticipation?: StudentParticipation;
    practiceStudentParticipation?: StudentParticipation;
    isAfterAssessmentDueDate: boolean;
    allowComplaintsForAutomaticAssessments: boolean;
    public gradingCriteria: GradingCriterion[];
    isExamExercise: boolean;
    submissionPolicy: SubmissionPolicy;
    availableExerciseHints: ExerciseHint[];
    activatedExerciseHints: ExerciseHint[];
    irisSettings?: IrisSettings;

    // extension points, see shared/extension-point
    @ContentChild('overrideStudentActions') overrideStudentActions: TemplateRef<any>;

    /**
     * variables are only for testing purposes(noVersionControlAndContinuousIntegrationAvailable)
     */
    public inProductionEnvironment: boolean;

    constructor(
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private participationWebsocketService: ParticipationWebsocketService,
        private participationService: ParticipationService,
        private route: ActivatedRoute,
        private profileService: ProfileService,
        private guidedTourService: GuidedTourService,
        private alertService: AlertService,
        private programmingExerciseSubmissionPolicyService: SubmissionPolicyService,
        private complaintService: ComplaintService,
        private plagiarismCaseService: PlagiarismCasesService,
        private exerciseHintService: ExerciseHintService,
        private courseService: CourseManagementService,
        private irisSettingsService: IrisSettingsService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.exerciseId = parseInt(params['exerciseId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
            this.courseService.find(this.courseId).subscribe((courseResponse) => (this.course = courseResponse.body!));
            this.accountService.identity().then((user: User) => {
                this.currentUser = user;
            });
            this.loadExercise();
        });

        // Checks if the current environment is production
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.inProductionEnvironment = profileInfo.inProduction;
            }
        });
    }

    ngOnDestroy() {
        if (this.participationUpdateListener) {
            this.participationUpdateListener.unsubscribe();
            if (this.studentParticipations) {
                this.studentParticipations.forEach((participation) => {
                    this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(participation.id!, this.exercise!);
                });
            }
        }
        if (this.submissionSubscription) {
            this.submissionSubscription.unsubscribe();
        }
    }

    loadExercise() {
        this.exercise = undefined;
        this.studentParticipations = this.participationWebsocketService.getParticipationsForExercise(this.exerciseId);
        this.updateStudentParticipations();
        this.resultWithComplaint = getFirstResultWithComplaintFromResults(this.gradedStudentParticipation?.results);
        this.exerciseService.getExerciseDetails(this.exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
            this.handleNewExercise(exerciseResponse.body!);
            this.loadComplaintAndLatestRatedResult();
        });
    }

    handleNewExercise(newExercise: Exercise) {
        this.exercise = newExercise;

        this.filterUnfinishedResults(this.exercise.studentParticipations);
        this.mergeResultsAndSubmissionsForParticipations();
        this.isAfterAssessmentDueDate = !this.exercise.assessmentDueDate || dayjs().isAfter(this.exercise.assessmentDueDate);
        this.exerciseCategories = this.exercise.categories ?? [];
        this.allowComplaintsForAutomaticAssessments = false;

        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = this.exercise as ProgrammingExercise;
            const isAfterDateForComplaint =
                !this.exercise.dueDate ||
                (hasExerciseDueDatePassed(this.exercise, this.gradedStudentParticipation) &&
                    (!programmingExercise.buildAndTestStudentSubmissionsAfterDueDate || dayjs().isAfter(programmingExercise.buildAndTestStudentSubmissionsAfterDueDate)));

            this.allowComplaintsForAutomaticAssessments = !!programmingExercise.allowComplaintsForAutomaticAssessments && isAfterDateForComplaint;
            this.programmingExerciseSubmissionPolicyService.getSubmissionPolicyOfProgrammingExercise(this.exerciseId).subscribe((submissionPolicy) => {
                this.submissionPolicy = submissionPolicy;
            });

            this.profileService
                .getProfileInfo()
                .pipe(
                    filter((profileInfo) => profileInfo?.activeProfiles?.includes('iris')),
                    switchMap(() => this.irisSettingsService.getCombinedProgrammingExerciseSettings(this.exercise!.id!)),
                )
                .subscribe((settings) => {
                    this.irisSettings = settings;
                });
        }

        this.subscribeForNewResults();
    }

    /**
     * Filters out any unfinished Results
     */
    private filterUnfinishedResults(participations?: StudentParticipation[]) {
        participations?.forEach((participation: Participation) => {
            if (participation.results) {
                participation.results = participation.results.filter((result: Result) => result.completionDate);
            }
        });
    }

    sortResults() {
        if (this.studentParticipations?.length) {
            this.studentParticipations.forEach((participation) => participation.results?.sort(this.resultSortFunction));
            this.sortedHistoryResults = this.studentParticipations.flatMap((participation) => participation.results ?? []).sort(this.resultSortFunction);
        }
    }

    private resultSortFunction = (a: Result, b: Result) => {
        const aValue = dayjs(a.completionDate!).valueOf();
        const bValue = dayjs(b.completionDate!).valueOf();
        return aValue - bValue;
    };

    mergeResultsAndSubmissionsForParticipations() {
        // if there are new student participation(s) from the server, we need to update this.studentParticipation
        if (this.exercise?.studentParticipations?.length) {
            this.studentParticipations = this.participationService.mergeStudentParticipations(this.exercise.studentParticipations);
            this.exercise.studentParticipations = this.studentParticipations;
            this.updateStudentParticipations();
            this.sortResults();
            // Add exercise to studentParticipation, as the result component is dependent on its existence.
            this.studentParticipations.forEach((participation) => (participation.exercise = this.exercise));
        } else if (this.studentParticipations?.length && this.exercise) {
            // otherwise we make sure that the student participation in exercise is correct
            this.exercise.studentParticipations = this.studentParticipations;
        }
    }

    subscribeForNewResults() {
        if (this.exercise && this.studentParticipations?.length) {
            this.studentParticipations.forEach((participation) => {
                this.participationWebsocketService.addParticipation(participation, this.exercise);
            });
            if (this.latestRatedResult) {
                if (this.latestRatedResult.successful) {
                    this.guidedTourService.enableTourForExercise(this.exercise, programmingExerciseSuccess, true);
                } else {
                    this.guidedTourService.enableTourForExercise(this.exercise, programmingExerciseFail, true);
                }
            }
        }
        this.participationUpdateListener = this.participationWebsocketService.subscribeForParticipationChanges().subscribe((changedParticipation: StudentParticipation) => {
            if (changedParticipation && this.exercise && changedParticipation.exercise?.id === this.exercise.id) {
                // Notify student about late submission result
                if (
                    changedParticipation.exercise?.dueDate &&
                    hasExerciseDueDatePassed(changedParticipation.exercise, changedParticipation) &&
                    changedParticipation.id === this.gradedStudentParticipation?.id &&
                    // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                    changedParticipation.results?.length! > this.gradedStudentParticipation?.results?.length!
                ) {
                    this.alertService.success('artemisApp.exercise.lateSubmissionResultReceived');
                }
                if (this.studentParticipations?.some((participation) => participation.id === changedParticipation.id)) {
                    this.exercise.studentParticipations = this.studentParticipations.map((participation) =>
                        participation.id === changedParticipation.id ? changedParticipation : participation,
                    );
                } else {
                    this.exercise.studentParticipations = [...this.studentParticipations, changedParticipation];
                }
                this.updateStudentParticipations();
                this.mergeResultsAndSubmissionsForParticipations();

                if (ExerciseType.PROGRAMMING === this.exercise?.type) {
                    this.exerciseHintService.getActivatedExerciseHints(this.exerciseId).subscribe((activatedRes?: HttpResponse<ExerciseHint[]>) => {
                        this.activatedExerciseHints = activatedRes!.body!;

                        this.exerciseHintService.getAvailableExerciseHints(this.exerciseId).subscribe((availableRes?: HttpResponse<ExerciseHint[]>) => {
                            // filter out the activated hints from the available hints
                            this.availableExerciseHints = availableRes!.body!.filter(
                                (availableHint) => !this.activatedExerciseHints.some((activatedHint) => availableHint.id === activatedHint.id),
                            );
                            const filteredAvailableExerciseHints = this.availableExerciseHints.filter((hint) => hint.displayThreshold !== 0);
                            if (filteredAvailableExerciseHints.length) {
                                this.alertService.info('artemisApp.exerciseHint.availableHintsAlertMessage', {
                                    taskName: filteredAvailableExerciseHints.first()?.programmingExerciseTask?.taskName,
                                });
                            }
                        });
                    });
                }
            }
        });
    }

    private updateStudentParticipations() {
        this.gradedStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, false);
        this.practiceStudentParticipation = this.participationService.getSpecificStudentParticipation(this.studentParticipations, true);
    }

    /**
     * Loads and stores the complaint if any exists. Furthermore, loads the latest rated result and stores it.
     */
    loadComplaintAndLatestRatedResult(): void {
        if (!this.gradedStudentParticipation?.submissions?.[0] || !this.sortedHistoryResults?.length) {
            return;
        }
        this.complaintService.findBySubmissionId(this.gradedStudentParticipation!.submissions![0].id!).subscribe({
            next: (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            error: (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        });

        if (this.exercise!.type === ExerciseType.MODELING || this.exercise!.type === ExerciseType.TEXT) {
            return;
        }

        const ratedResults = this.gradedStudentParticipation?.results?.filter((result: Result) => result.rated).sort(this.resultSortFunction);
        if (ratedResults) {
            const latestResult = ratedResults.last();
            if (latestResult) {
                latestResult.participation = this.gradedStudentParticipation;
            }
            this.latestRatedResult = latestResult;
        }
    }

    private onError(error: string) {
        this.alertService.error(error);
    }
}
