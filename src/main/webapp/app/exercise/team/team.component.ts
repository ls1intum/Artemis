import { Component, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Tooltip } from 'primeng/tooltip';
import { TeamUpdateButtonComponent } from './team-update-dialog/team-update-button.component';
import { TeamDeleteButtonComponent } from './team-update-dialog/team-delete-button.component';
import { TeamParticipationTableComponent } from './team-participation-table/team-participation-table.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-team',
    templateUrl: './team.component.html',
    styleUrls: ['./team.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        RouterLink,
        TranslateDirective,
        Tooltip,
        TeamUpdateButtonComponent,
        TeamDeleteButtonComponent,
        TeamParticipationTableComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        TableViewComponent,
    ],
})
export class TeamComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private exerciseService = inject(ExerciseService);
    private teamService = inject(TeamService);
    private accountService = inject(AccountService);
    private router = inject(Router);
    private courseStorageService = inject(CourseStorageService);

    team = signal<Team | undefined>(undefined);
    exercise = signal<Exercise | undefined>(undefined);
    isLoading = signal(false);
    isTransitioning = signal(false);

    currentUser = signal<User | undefined>(undefined);
    isAdmin = signal(false);
    readonly isTeamOwner = computed(() => {
        const currentUser = this.currentUser();
        const team = this.team();
        return currentUser !== undefined && team !== undefined && currentUser.id === team.owner?.id;
    });

    readonly studentsTableOptions: TableViewOptions = {
        lazy: false,
        paginated: false,
        showSearch: false,
        striped: true,
    };

    readonly studentsColumns: ColumnDef<User>[] = [
        { field: 'login', headerKey: 'artemisApp.team.detail.students.login', sort: true, width: '10rem' },
        { field: 'name', headerKey: 'artemisApp.team.detail.students.name', sort: true, width: '10rem' },
        { field: 'email', headerKey: 'artemisApp.team.detail.students.email', sort: true },
    ];

    constructor() {
        void this.accountService.identity().then((user: User | undefined) => {
            this.currentUser.set(user);
            this.isAdmin.set(this.accountService.isAdmin());
        });
    }

    /**
     * Fetches the exercise and team from the server
     */
    ngOnInit() {
        this.route.params.subscribe({
            next: (params) => {
                this.setLoadingState(true);
                this.exerciseService.find(params['exerciseId']).subscribe({
                    next: (exerciseResponse) => {
                        const exercise = exerciseResponse.body!;
                        this.exercise.set(exercise);
                        this.storeExercise(exercise, Number(params['courseId']));
                        this.teamService.find(this.exercise()!, params['teamId']).subscribe({
                            next: (teamResponse) => {
                                this.team.set(teamResponse.body!);
                                this.setLoadingState(false);
                            },
                            error: this.onLoadError,
                        });
                    },
                    error: this.onLoadError,
                });
            },
            error: this.onLoadError,
        });
    }

    private storeExercise(exercise: Exercise, routeCourseId: number): void {
        const courseId = exercise.course?.id ?? routeCourseId;
        const storedCourse = this.courseStorageService.getCourse(courseId);
        this.courseStorageService.updateCourse(
            cloneWith(storedCourse ?? new Course(), {
                id: courseId,
                exercises: [...(storedCourse?.exercises?.filter((storedExercise) => storedExercise.id !== exercise.id) ?? []), exercise],
            }),
        );
    }

    private setLoadingState(loading: boolean) {
        if (this.exercise() && this.team() && !this.isLoading()) {
            this.isTransitioning.set(loading);
        } else {
            this.isLoading.set(loading);
        }
    }

    private onLoadError = (error: HttpErrorResponse) => {
        this.alertService.error(error.message);
        this.isLoading.set(false);
    };

    /**
     * Called when the team was updated by TeamUpdateButtonComponent
     *
     * @param team Updated team
     */
    onTeamUpdate(team: Team) {
        this.team.set(team);
    }

    /**
     * Called when the team was deleted by TeamDeleteButtonComponent
     *
     * Navigates back to the overviews of teams for the exercise.
     */
    onTeamDelete() {
        const exercise = this.exercise();
        void this.router.navigate(['/course-management', exercise?.course?.id, 'exercises', exercise?.id, 'teams']);
    }
}
