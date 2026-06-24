import { Component, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { TeamsExportButtonComponent } from '../teams-import-dialog/teams-export-button.component';
import { TeamsImportButtonComponent } from '../teams-import-dialog/teams-import-button.component';
import { TeamUpdateButtonComponent } from '../team-update-dialog/team-update-button.component';
import { TeamDeleteButtonComponent } from '../team-update-dialog/team-delete-button.component';
import { TeamStudentsListComponent } from '../team-participate/team-students-list.component';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { SelectButton } from 'primeng/selectbutton';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

export enum FilterProp {
    ALL = 'all',
    OWN = 'own',
}

/** Team augmented with a pre-computed search string for student names and logins. */
type TeamRow = Team & { studentsSearchText: string };

@Component({
    selector: 'jhi-teams',
    templateUrl: './teams.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        TeamsExportButtonComponent,
        TeamsImportButtonComponent,
        TeamUpdateButtonComponent,
        RouterLink,
        TeamStudentsListComponent,
        TeamDeleteButtonComponent,
        TableViewComponent,
        SelectButton,
        ArtemisTranslatePipe,
    ],
})
export class TeamsComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private exerciseService = inject(ExerciseService);
    private teamService = inject(TeamService);
    private accountService = inject(AccountService);

    readonly FilterProp = FilterProp;

    readonly filterOptions: { labelKey: string; value: FilterProp }[] = [
        { labelKey: 'artemisApp.team.filters.all', value: FilterProp.ALL },
        { labelKey: 'artemisApp.team.filters.own', value: FilterProp.OWN },
    ];

    teams = signal<Team[]>([]);
    readonly teamsForTable = computed<TeamRow[]>(() =>
        this.teams().map((team) => {
            // Clone via Object.assign rather than object spread, per the repository TypeScript guidelines.
            const row = Object.assign({} as TeamRow, team);
            row.studentsSearchText = team.students?.flatMap((student) => [student.login, student.name].filter(Boolean)).join(' ') ?? '';
            return row;
        }),
    );
    teamCriteria: { filterProp: FilterProp } = { filterProp: FilterProp.ALL };
    exercise = signal<Exercise | undefined>(undefined);

    /** Number of teams currently visible in the table after the search filter; undefined until the table reports it. */
    readonly filteredTeamsSize = signal<number | undefined>(undefined);

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading = signal(false);

    currentUser = signal<User | undefined>(undefined);
    isAdmin = signal(false);

    readonly idTemplate = viewChild<CellTemplateRef<TeamRow>>('idTemplate');
    readonly shortNameTemplate = viewChild<CellTemplateRef<TeamRow>>('shortNameTemplate');
    readonly ownerTemplate = viewChild<CellTemplateRef<TeamRow>>('ownerTemplate');
    readonly studentsTemplate = viewChild<CellTemplateRef<TeamRow>>('studentsTemplate');

    readonly tableOptions: TableViewOptions = {
        lazy: false,
        searchPlaceholder: 'artemisApp.exercise.searchForTeams',
        globalFilterFields: ['name', 'shortName', 'owner.name', 'owner.login', 'studentsSearchText'],
        striped: true,
    };

    readonly columns = computed<ColumnDef<TeamRow>[]>(() => [
        { field: 'id', headerKey: 'global.field.id', sort: true, width: '4rem', templateRef: this.idTemplate() },
        { field: 'name', headerKey: 'artemisApp.team.name.label', sort: true, width: '8rem' },
        { field: 'shortName', headerKey: 'artemisApp.team.shortName.label', sort: true, width: '8rem', templateRef: this.shortNameTemplate() },
        { field: 'owner.name', headerKey: 'artemisApp.team.tutor', sort: true, width: '10rem', templateRef: this.ownerTemplate() },
        { field: 'students', headerKey: 'artemisApp.team.students', sort: false, templateRef: this.studentsTemplate() },
    ]);

    constructor() {
        this.accountService.identity().then((user: User) => {
            this.currentUser.set(user);
            this.isAdmin.set(this.accountService.isAdmin());
        });
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        this.initTeamFilter();
        this.loadAll();
    }

    /**
     * Life cycle hook to indicate component destruction is done
     */
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Load all team components
     */
    loadAll() {
        this.route.params.subscribe((params) => {
            this.isLoading.set(true);
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                this.exercise.set(exerciseResponse.body!);
                const teamOwnerId = this.teamCriteria.filterProp === FilterProp.OWN ? this.currentUser()?.id : undefined;
                this.teamService.findAllByExerciseId(params['exerciseId'], teamOwnerId).subscribe((teamsResponse) => {
                    this.teams.set(teamsResponse.body!);
                    this.isLoading.set(false);
                });
            });
        });
    }

    /**
     * Initializes the team filter based on the query param "filter"
     */
    initTeamFilter() {
        switch (this.route.snapshot.queryParamMap.get('filter')) {
            case FilterProp.OWN:
                this.teamCriteria.filterProp = FilterProp.OWN;
                break;
            default:
                this.teamCriteria.filterProp = FilterProp.ALL;
                break;
        }
    }

    /**
     * Updates the criteria by which to filter teams and the filter query param, then reloads teams
     * @param filter New filter prop value
     */
    updateTeamFilter(filter: FilterProp) {
        this.teamCriteria.filterProp = filter;
        this.router.navigate([], { relativeTo: this.route, queryParams: { filter }, queryParamsHandling: 'merge', replaceUrl: true });
        this.loadAll();
    }

    /**
     * Called when a team has been added or was updated by TeamUpdateButtonComponent
     *
     * @param team Team that was added or updated
     */
    onTeamUpdate(team: Team) {
        this.upsertTeam(team);
    }

    /**
     * Called when a team has been deleted by TeamDeleteButtonComponent
     *
     * @param team Team that was deleted
     */
    onTeamDelete(team: Team) {
        this.deleteTeam(team);
    }

    /**
     * Called when teams were imported from another team exercise
     *
     * @param teams All teams that this exercise has now after the import
     */
    onTeamsImport(teams: Team[]) {
        this.teams.set(teams);
    }

    /**
     * Called by jhi-table-view whenever the search filter changes; keeps the header count in sync with
     * the number of teams actually shown in the table.
     * @param filteredSize Number of teams visible after the table search filter
     */
    onFilteredTeamsSizeChange(filteredSize: number) {
        this.filteredTeamsSize.set(filteredSize);
    }

    /**
     * If team does not yet exists in teams, it is appended.
     * If team already exists in teams, it is updated.
     *
     * @param team Team that is added or updated
     */
    private upsertTeam(team: Team) {
        const teams = this.teams();
        const index = teams.findIndex((t) => t.id === team.id);
        if (index === -1) {
            this.teams.set([...teams, team]);
        } else {
            this.teams.set(teams.map((t, i) => (i === index ? team : t)));
        }
    }

    /**
     * Deleted an existing team from teams.
     *
     * @param team Team that is deleted
     */
    private deleteTeam(team: Team) {
        this.teams.set(this.teams().filter((t) => t.id !== team.id));
    }
}
