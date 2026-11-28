import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiTableDirective, TumAetUiTableSortEvent, TumAetUiTableSortableColumnComponent } from '@tumaet/ui-angular/table-directive';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { LOAD_SHARING_COURSES, SharingCourse } from './sharing-course';
import { SortService } from 'app/foundation/service/sort.service';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { SharingInfo, ShoppingBasket } from './sharing.model';
import { faPlus } from '@fortawesome/free-solid-svg-icons/faPlus';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingExerciseSharingService } from './programming-exercise-sharing.service';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { combineLatest, take } from 'rxjs';
import { finalize } from 'rxjs/operators';

/**
 * controls the import of an exercise from the sharing platform.
 */
@Component({
    selector: 'jhi-sharing',
    templateUrl: './sharing.component.html',
    styleUrls: ['./sharing.css'],
    imports: [TumAetUiButtonDirective, RouterLink, FormsModule, TranslateDirective, FaIconComponent, TumAetUiTableDirective, TumAetUiTableSortableColumnComponent],
})
export class SharingComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private loadCourses = inject(LOAD_SHARING_COURSES);
    private sortService = inject(SortService);
    private programmingExerciseSharingService = inject(ProgrammingExerciseSharingService);
    private alertService = inject(AlertService);

    // Icons
    protected readonly faPlus = faPlus;

    readonly courses = signal<SharingCourse[]>([]);
    readonly coursesLoading = signal(true);

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly reverseSorting = signal(false);
    readonly sortColumn = signal('id');

    readonly shoppingBasket = signal<ShoppingBasket | undefined>(undefined);
    /**
     * holder for all data needed to import the exercise
     */
    sharingInfo: SharingInfo = new SharingInfo();

    readonly selectedCourse = signal<SharingCourse | undefined>(undefined);

    getBasketTokenExpiryDate(): Date {
        const tokenValidUntil = this.shoppingBasket()?.tokenValidUntil;
        if (tokenValidUntil) {
            return new Date(tokenValidUntil);
        }
        return new Date();
    }

    /**
     * loads all courses from courseService
     */
    loadAll() {
        this.coursesLoading.set(true);
        this.loadCourses()
            .pipe(finalize(() => this.coursesLoading.set(false)))
            .subscribe({
                next: (courses) => this.courses.set(courses),
                error: () => this.alertService.error('artemisApp.sharing.error.loadingCourses'),
            });
    }

    onCourseSelected(course: SharingCourse): void {
        this.selectedCourse.set(course);
    }

    courseId(): number {
        return this.selectedCourse()?.id ?? 0;
    }

    onExerciseSelected(index: number): void {
        this.sharingInfo.exercisePosition = index;
    }

    /**
     * Returns the unique identifier for items in the collection
     * @param index - Index of a course in the collection
     * @param item - Current course
     */
    trackId(index: number, item: SharingCourse) {
        return item.id ?? index;
    }

    /** applies the sort of a clicked column header of the course table */
    onSort(event: TumAetUiTableSortEvent) {
        this.sortColumn.set(event.field);
        this.reverseSorting.set(event.order === 1);
        this.sortRows();
    }

    /**
     * sorts the course table
     */
    sortRows() {
        const sortedCourses = [...this.courses()];
        this.sortService.sortByProperty(sortedCourses, this.sortColumn(), this.reverseSorting());
        this.courses.set(sortedCourses);
    }

    /**
     * finally navigates to the import page
     */
    navigateToImportFromSharing() {
        const importBaseRoute = ['/course-management', this.courseId(), 'programming-exercises'];
        importBaseRoute.push('import-from-sharing');

        // check for missing parameters (technically this could never happen, because the button is disabled then)
        if (
            !this.sharingInfo.basketToken ||
            !this.sharingInfo.apiBaseURL ||
            !this.sharingInfo.returnURL ||
            !this.sharingInfo.checksum ||
            this.sharingInfo.exercisePosition === undefined ||
            this.sharingInfo.exercisePosition === null
        ) {
            this.alertService.error('artemisApp.sharing.error.missingParameters');
            return;
        }
        this.router
            .navigate(importBaseRoute, {
                queryParams: {
                    basketToken: this.sharingInfo.basketToken,
                    apiBaseURL: this.sharingInfo.apiBaseURL,
                    returnURL: this.sharingInfo.returnURL,
                    selectedExercise: this.sharingInfo.exercisePosition,
                    checksum: this.sharingInfo.checksum,
                },
            })
            .then((success) => {
                if (!success) {
                    this.alertService.error('artemisApp.sharing.error.navigation');
                }
            })
            .catch((error) => {
                this.alertService.error('artemisApp.sharing.error.navigation');
            });
    }

    get formattedExpiryDate(): string {
        return this.getBasketTokenExpiryDate().toLocaleString();
    }

    /**
     * Initialises the sharing page for import
     */
    ngOnInit(): void {
        combineLatest([this.route.params.pipe(take(1)), this.route.queryParams.pipe(take(1))]).subscribe(([params, qparams]: [Params, Params]) => {
            this.sharingInfo.basketToken = params['basketToken'];
            this.sharingInfo.returnURL = qparams['returnURL'];
            this.sharingInfo.apiBaseURL = qparams['apiBaseURL'];
            this.sharingInfo.checksum = qparams['checksum'];

            if (!this.sharingInfo.basketToken) {
                this.alertService.error('artemisApp.sharing.error.missingToken');
                return;
            }
            if (!this.sharingInfo.apiBaseURL || !this.sharingInfo.returnURL || !this.sharingInfo.checksum) {
                this.alertService.error('artemisApp.sharing.error.missingParameters');
                return;
            }

            this.loadAll();
            this.loadSharedExercises();
        });
    }

    /**
     * Loads the shared exercises from the sharing platform
     */
    private loadSharedExercises(): void {
        this.programmingExerciseSharingService.getSharedExercises(this.sharingInfo).subscribe({
            next: (res: ShoppingBasket) => {
                this.shoppingBasket.set(res);
            },
            error: () => {
                this.alertService.error('artemisApp.sharing.error.loadingBasket');
            },
        });
    }
}
