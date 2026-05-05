import { Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation, inject } from '@angular/core';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { RouterLink } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { faFolderOpen, faListAlt, faTable } from '@fortawesome/free-solid-svg-icons';
import { faFileCode } from '@fortawesome/free-regular-svg-icons';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { FeatureToggleLinkDirective } from 'app/foundation/feature-toggle/feature-toggle-link.directive';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { IrisReviewAssessmentButtonComponent } from 'app/iris/overview/understanding-assessment/shared/iris-assessment-button/iris-review-assessment-button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

@Component({
    selector: 'jhi-iris-assessment-review-exercise',
    templateUrl: './iris-assessment-review-exercise.component.html',
    providers: [ExerciseCacheService],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        FaIconComponent,
        FormsModule,
        RouterLink,
        NgxDatatableModule,
        CodeButtonComponent,
        FeatureToggleLinkDirective,
        IrisReviewAssessmentButtonComponent,
        FaIconComponent,
        DataTableComponent,
    ],
})
export class IrisAssessmentReviewExerciseComponent implements OnInit {
    @Input()
    participations: ProgrammingExerciseStudentParticipation[] = [];
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    course!: Course;
    @Input()
    isLoading: boolean = false;

    @Output()
    refresh = new EventEmitter<void>();

    protected readonly faFolderOpen = faFolderOpen;
    protected readonly faListAlt = faListAlt;
    protected readonly faFileCode = faFileCode;
    protected readonly faTable = faTable;
    protected readonly RepositoryType = RepositoryType;
    protected readonly ExerciseType = ExerciseType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly AssessmentType = AssessmentType;
    protected readonly submissionCountSortFieldProperty = 'submissionCount';
    protected readonly studentLoginSortFieldProperty = 'student.login';
    protected readonly verifiedScoreSortFieldProperty = 'irisVerifiedScore';
    protected readonly nameSortFieldProperty = 'student.name';

    private profileService = inject(ProfileService);

    localCIEnabled = true;

    ngOnInit() {
        this.localCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    }

    getExerciseParticipationsLink(participationId: number): string[] {
        return [
            '/course-management',
            this.course.id!.toString(),
            this.exercise.type + '-exercises',
            this.exercise.id!.toString(),
            'participations',
            participationId.toString(),
            'submissions',
        ];
    }

    /**
     * Returns the build plan id for a participation
     * @param participation Participation for which to return the build plan id
     */
    buildPlanId(participation: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation)?.buildPlanId;
    }

    /**
     * Returns the link to the repository of a participation
     * @param participation Participation for which to get the link for
     */
    getRepositoryLink(participation: Participation) {
        return (participation! as ProgrammingExerciseStudentParticipation).userIndependentRepositoryUri;
    }
}
