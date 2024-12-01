import { Component, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Participation } from 'app/entities/participation/participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Subscription, forkJoin } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise.utils';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { faCodeBranch, faComment, faDownload, faFilter, faFolderOpen, faListAlt, faSync } from '@fortawesome/free-solid-svg-icons';
import { faFileCode } from '@fortawesome/free-regular-svg-icons';
import { Range } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { isManualResult } from 'app/exercises/shared/result/result.utils';

/**
 * Filter properties for a result
 */
export enum FilterProp {
    ALL = 'All',
    SUCCESSFUL = 'Successful',
    UNSUCCESSFUL = 'Unsuccessful',
    BUILD_FAILED = 'BuildFailed',
    MANUAL = 'Manual',
    AUTOMATIC = 'Automatic',
    LOCKED = 'Locked',
}

@Component({
    selector: 'jhi-exercise-scores',
    templateUrl: './exercise-scores.component.html',
    providers: [ExerciseCacheService],
    encapsulation: ViewEncapsulation.None,
})
export class ExerciseScoresComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly FilterProp = FilterProp;
    readonly ExerciseType = ExerciseType;
    readonly FeatureToggle = FeatureToggle;
    readonly AssessmentType = AssessmentType;
    // represents all intervals selectable in the score distribution on the exercise statistics
    readonly scoreRanges = [
        new Range(0, 10),
        new Range(10, 20),
        new Range(20, 30),
        new Range(30, 40),
        new Range(40, 50),
        new Range(50, 60),
        new Range(60, 70),
        new Range(70, 80),
        new Range(80, 90),
        new Range(90, 100),
    ];

    @ViewChild('exportPopover')
    private exportPopover: NgbPopover;

    course: Course;
    exercise: Exercise;
    paramSub: Subscription;
    reverse: boolean;
    participations: Participation[] = [];
    filteredParticipations: Participation[] = [];
    eventSubscriber: Subscription;
    newManualResultAllowed: boolean;
    rangeFilter?: Range;

    resultCriteria: { filterProp: FilterProp } = { filterProp: FilterProp.ALL };
    participationsPerFilter: Map<FilterProp, number> = new Map();

    isLoading: boolean;

    afterDueDate = false;

    localVCEnabled = false;

    // Icons
    faDownload = faDownload;
    faSync = faSync;
    faFolderOpen = faFolderOpen;
    faListAlt = faListAlt;
    faCodeBranch = faCodeBranch;
    farFileCode = faFileCode;
    faFilter = faFilter;
    faComment = faComment;

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
        private profileService: ProfileService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private participationService: ParticipationService,
    ) {}

    /**
     * Fetches the course and exercise from the server
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.isLoading = true;
            const findCourse = this.courseService.find(params['courseId']);
            const findExercise = this.exerciseService.find(params['exerciseId']);
            const filterValue = this.route.snapshot.queryParamMap.get('scoreRangeFilter');
            if (filterValue) {
                this.rangeFilter = this.scoreRanges[Number(filterValue)];
            }

            forkJoin([findCourse, findExercise]).subscribe(([courseRes, exerciseRes]) => {
                this.course = courseRes.body!;
                this.exercise = exerciseRes.body!;
                this.afterDueDate = !!this.exercise.dueDate && dayjs().isAfter(this.exercise.dueDate);
                // After both calls are done, the loading flag is removed. If the exercise is not a programming exercise, only the result call is needed.
                this.participationService.findAllParticipationsByExercise(this.exercise.id!, true).subscribe((participationsResponse) => {
                    this.handleNewParticipations(participationsResponse);
                });

                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
            });
        });
    }

    getExerciseParticipationsLink(participationId: number): string[] {
        return this.exercise.exerciseGroup
            ? [
                  '/course-management',
                  this.course.id!.toString(),
                  'exams',
                  this.exercise.exerciseGroup!.exam!.id!.toString(),
                  'exercise-groups',
                  this.exercise.exerciseGroup!.id!.toString(),
                  this.exercise.type + '-exercises',
                  this.exercise.id!.toString(),
                  'participations',
                  participationId.toString(),
              ]
            : [
                  '/course-management',
                  this.course.id!.toString(),
                  this.exercise.type + '-exercises',
                  this.exercise.id!.toString(),
                  'participations',
                  participationId.toString(),
                  'submissions',
              ];
    }

    private handleNewParticipations(participationsResponse: HttpResponse<Participation[]>) {
        this.participations = participationsResponse.body ?? [];
        this.participations.forEach((participation) => {
            participation.results?.forEach((result, index) => {
                participation.results![index].durationInMinutes = dayjs(result.completionDate).diff(participation.initializationDate, 'seconds');
            });
            // sort the results from old to new.
            // the result of the first correction round will be at index 0,
            // the result of a complaints or the second correction at index 1.
            participation.results?.sort((result1, result2) => (result1.id ?? 0) - (result2.id ?? 0));
            const resultsWithoutAthena = participation.results?.filter((result) => result.assessmentType !== AssessmentType.AUTOMATIC_ATHENA);
            if (resultsWithoutAthena?.length != 0) {
                if (resultsWithoutAthena?.[0].submission) {
                    participation.submissions = [resultsWithoutAthena?.[0].submission];
                } else if (participation.results?.[0].submission) {
                    participation.submissions = [participation.results?.[0].submission];
                }
            } else {
                participation.results = undefined;
            }
        });
        this.filteredParticipations = this.filterByScoreRange(this.participations);
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            const programmingExercise = this.exercise as ProgrammingExercise;
            if (programmingExercise.projectKey) {
                this.profileService.getProfileInfo().subscribe((profileInfo) => {
                    this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
                });
            }
        }

        for (const filter of Object.values(FilterProp)) {
            if (this.isFilterRelevantForConfiguration(filter)) {
                this.participationsPerFilter.set(filter, this.filteredParticipations.filter((participation) => this.filterParticipationsByProp(participation, filter)).length);
            }
        }

        this.isLoading = false;
    }

    /**
     * Updates the criteria by which to filter results
     * @param newValue New filter prop value
     */
    updateParticipationFilter(newValue: FilterProp) {
        this.isLoading = true;
        setTimeout(() => {
            this.resultCriteria.filterProp = newValue;
            this.isLoading = false;
        });
    }

    /**
     * Predicate used to filter participations by the current filter prop setting
     * @param participation Participation for which to evaluate the predicate
     * @param filterProp the filter that should be used to determine if the participation should be included or excluded
     */
    filterParticipationsByProp = (participation: Participation, filterProp = this.resultCriteria.filterProp): boolean => {
        const latestResult = participation.results?.last();
        switch (filterProp) {
            case FilterProp.SUCCESSFUL:
                return !!latestResult?.successful;
            case FilterProp.UNSUCCESSFUL:
                return !latestResult?.successful;
            case FilterProp.BUILD_FAILED:
                return !!(participation.submissions?.[0] && (participation.submissions?.[0] as ProgrammingSubmission).buildFailed);
            case FilterProp.MANUAL:
                return !!latestResult && isManualResult(latestResult);
            case FilterProp.AUTOMATIC:
                return latestResult?.assessmentType === AssessmentType.AUTOMATIC;
            case FilterProp.LOCKED:
                return !!latestResult && !latestResult.completionDate;
            case FilterProp.ALL:
            default:
                return true;
        }
    };

    isFilterRelevantForConfiguration(filterProp: FilterProp): boolean {
        switch (filterProp) {
            case FilterProp.BUILD_FAILED:
                return this.exercise.type === ExerciseType.PROGRAMMING;
            case FilterProp.MANUAL:
            case FilterProp.AUTOMATIC:
                return this.newManualResultAllowed || !!this.exercise.allowComplaintsForAutomaticAssessments;
            case FilterProp.LOCKED:
                return this.newManualResultAllowed && !!this.exercise.isAtLeastInstructor;
            default:
                return true;
        }
    }

    /**
     * Returns the build plan id for a participation
     * @param participation Participation for which to return the build plan id
     */
    buildPlanId(participation: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation)?.buildPlanId;
    }

    /**
     * Returns the project key of the exercise
     */
    projectKey(): string {
        return (this.exercise as ProgrammingExercise).projectKey!;
    }

    /**
     * Returns the link to the repository of a participation
     * @param participation Participation for which to get the link for
     */
    getRepositoryLink(participation: Participation) {
        return (participation! as ProgrammingExerciseStudentParticipation).userIndependentRepositoryUri;
    }

    /**
     * Exports the names of exercise participants as a csv file
     */
    exportNames() {
        if (this.participations.length) {
            const rows: string[] = [];
            this.participations.forEach((participation, index) => {
                const studentParticipation = participation as StudentParticipation;
                const { participantName } = studentParticipation;
                if (studentParticipation.team) {
                    if (index === 0) {
                        rows.push('data:text/csv;charset=utf-8,Team Name,Team Short Name,Students');
                    }
                    const { name, shortName, students } = studentParticipation.team;
                    rows.push(`${name},${shortName},"${students?.map((s) => s.name).join(', ')}"`);
                } else {
                    rows.push(index === 0 ? `data:text/csv;charset=utf-8,${participantName}` : participantName!);
                }
            });
            this.resultService.triggerDownloadCSV(rows, 'results-names.csv');
        }
    }

    /**
     * Formats the participations in the autocomplete overlay.
     *
     * @param participation
     */
    searchParticipationFormatter = (participation: Participation): string => {
        const studentParticipation = participation as StudentParticipation;
        if (studentParticipation.student) {
            const { login, name } = studentParticipation.student;
            return `${login} (${name})`;
        } else if (studentParticipation.team) {
            return formatTeamAsSearchResult(studentParticipation.team);
        }
        return '';
    };

    /**
     * Converts a result object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param participation
     */
    searchTextFromParticipation = (participation: Participation): string => {
        return (participation as StudentParticipation).participantIdentifier || '';
    };

    /**
     * Triggers a re-fetch of the results from the server
     */
    refresh() {
        this.isLoading = true;
        this.participations = [];
        this.filteredParticipations = [];
        this.participationService.findAllParticipationsByExercise(this.exercise.id!, true).subscribe((participationsResponse) => {
            this.handleNewParticipations(participationsResponse);
        });
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.programmingSubmissionService.unsubscribeAllWebsocketTopics(this.exercise);
    }

    /**
     * filters the displayable participations based on the given range filter
     * @param participations all participations for the given exercise
     * @returns participations falling into the given score range
     */
    filterByScoreRange(participations: Participation[]): Participation[] {
        if (!this.rangeFilter) {
            return participations;
        }
        let filterFunction;
        // If the range to filter against is [90%, 100%], a score of 100% also satisfies this range
        if (this.rangeFilter.upperBound === 100) {
            filterFunction = (participation: Participation) => {
                const result = participation.results?.last();
                return !!result?.score && result?.score >= this.rangeFilter!.lowerBound && result.score <= this.rangeFilter!.upperBound;
            };
        } else {
            // For any other range, the score must be strictly below the upper bound
            filterFunction = (participation: Participation) => {
                const result = participation.results?.last();
                return result?.score !== undefined && result.score >= this.rangeFilter!.lowerBound && result.score < this.rangeFilter!.upperBound;
            };
        }

        return participations.filter(filterFunction);
    }

    /**
     * resets the score range filter
     */
    resetFilterOptions(): void {
        this.rangeFilter = undefined;
        this.filteredParticipations = this.participations;
        this.resultCriteria.filterProp = FilterProp.ALL;
    }

    /**
     * Close popover for export options, since it would obstruct the newly opened modal
     */
    closeExportPopover() {
        this.exportPopover?.close();
    }
}
