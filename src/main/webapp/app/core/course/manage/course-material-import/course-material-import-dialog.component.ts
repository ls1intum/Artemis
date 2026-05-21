import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CourseSummaryDTO } from 'app/core/course/shared/entities/course-summary.model';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal, onError } from 'app/shared/util/global.utils';
import { CourseMaterialImportService } from './course-material-import.service';
import { CourseMaterialImportOptions, CourseMaterialImportOptionsDTO, createDefaultImportOptions } from './course-material-import.model';
import { CourseForImportDTO } from 'app/core/course/shared/entities/course.model';
import { CourseForImportDTOPagingService } from 'app/core/course/shared/services/course-for-import-dto-paging-service';
import { SearchTermPageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { AlertService } from 'app/shared/service/alert.service';
import { lastValueFrom } from 'rxjs';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';

type DialogStep = 'selectCourse' | 'selectOptions';

@Component({
    selector: 'jhi-course-material-import-dialog',
    imports: [
        FormsModule,
        DialogModule,
        ButtonModule,
        CheckboxModule,
        TableModule,
        InputTextModule,
        ProgressSpinnerModule,
        TranslateDirective,
        PaginatorModule,
        ArtemisTranslatePipe,
    ],
    templateUrl: './course-material-import-dialog.component.html',
    styleUrl: './course-material-import-dialog.component.scss',
})
export class CourseMaterialImportDialogComponent {
    private readonly translateService = inject(TranslateService);
    private readonly importService = inject(CourseMaterialImportService);
    private readonly courseSearchService = inject(CourseForImportDTOPagingService);
    private readonly alertService = inject(AlertService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    // Input for target course ID
    readonly targetCourseId = input.required<number>();

    // Output event when import is started
    readonly importStarted = output<CourseMaterialImportOptionsDTO>();

    // Dialog visibility state
    readonly show = signal<boolean>(false);

    // Current step in the dialog
    readonly currentStep = signal<DialogStep>('selectCourse');

    // Course selection state
    readonly searchTerm = signal<string>('');
    readonly courses = signal<CourseForImportDTO[]>([]);
    readonly isLoadingCourses = signal<boolean>(false);
    readonly selectedCourse = signal<CourseForImportDTO | undefined>(undefined);
    readonly first = signal<number>(0);
    readonly rows = signal<number>(10);
    readonly totalRecords = signal<number>(0);

    // Source course summary state
    readonly sourceSummary = signal<CourseSummaryDTO | undefined>(undefined);
    readonly isLoadingSummary = signal<boolean>(false);

    // Import options as signals
    readonly importExercises = signal<boolean>(false);
    readonly importLectures = signal<boolean>(false);
    readonly importExams = signal<boolean>(false);
    readonly importCompetencies = signal<boolean>(false);
    readonly importTutorialGroups = signal<boolean>(false);
    readonly importFaqs = signal<boolean>(false);

    // Import in progress state
    readonly isImporting = signal<boolean>(false);

    // Computed signals for UI state - check if source has content
    readonly hasExercises = computed(() => (this.sourceSummary()?.numberOfExercises ?? 0) > 0);
    readonly hasLectures = computed(() => (this.sourceSummary()?.numberOfLectures ?? 0) > 0);
    readonly hasExams = computed(() => (this.sourceSummary()?.numberOfExams ?? 0) > 0);
    readonly hasCompetencies = computed(() => (this.sourceSummary()?.numberOfCompetencies ?? 0) > 0);
    readonly hasTutorialGroups = computed(() => (this.sourceSummary()?.numberOfTutorialGroups ?? 0) > 0);
    readonly hasFaqs = computed(() => (this.sourceSummary()?.numberOfFaqs ?? 0) > 0);

    // Check if at least one import option is selected
    readonly canImport = computed(
        () =>
            (this.importExercises() && this.hasExercises()) ||
            (this.importLectures() && this.hasLectures()) ||
            (this.importExams() && this.hasExams()) ||
            (this.importCompetencies() && this.hasCompetencies()) ||
            (this.importTutorialGroups() && this.hasTutorialGroups()) ||
            (this.importFaqs() && this.hasFaqs()),
    );

    // Dynamic header title based on current step
    readonly headerTitle = computed(() => {
        this.currentLocale(); // Trigger reactivity on locale change
        const step = this.currentStep();
        if (step === 'selectCourse') {
            return this.translateService.instant('artemisApp.course.import.selectCourse');
        }
        return this.translateService.instant('artemisApp.course.import.selectOptions');
    });

    constructor() {
        // Load courses when dialog opens
        effect(() => {
            if (this.show()) {
                untracked(() => this.loadCourses());
            }
        });
    }

    /**
     * Opens the dialog and resets state.
     */
    open(): void {
        this.reset();
        this.show.set(true);
    }

    /**
     * Closes the dialog.
     */
    close(): void {
        this.show.set(false);
        this.reset();
    }

    /**
     * Resets all dialog state to initial values.
     */
    private reset(): void {
        this.currentStep.set('selectCourse');
        this.searchTerm.set('');
        this.courses.set([]);
        this.selectedCourse.set(undefined);
        this.sourceSummary.set(undefined);
        this.first.set(0);
        this.totalRecords.set(0);

        // Reset import options
        const defaults = createDefaultImportOptions();
        this.importExercises.set(defaults.importExercises);
        this.importLectures.set(defaults.importLectures);
        this.importExams.set(defaults.importExams);
        this.importCompetencies.set(defaults.importCompetencies);
        this.importTutorialGroups.set(defaults.importTutorialGroups);
        this.importFaqs.set(defaults.importFaqs);
    }

    /**
     * Loads courses for selection, excluding the target course.
     */
    async loadCourses(): Promise<void> {
        try {
            this.isLoadingCourses.set(true);
            const searchState: SearchTermPageableSearch = {
                searchTerm: this.searchTerm(),
                page: Math.floor(this.first() / this.rows()) + 1,
                sortedColumn: 'TITLE',
                sortingOrder: SortingOrder.ASCENDING,
                pageSize: this.rows(),
            };
            const result = await lastValueFrom(this.courseSearchService.search(searchState));

            // Filter out the target course
            const filteredCourses = result.resultsOnPage?.filter((course) => course.id !== this.targetCourseId()) ?? [];
            this.courses.set(filteredCourses);

            // Calculate total records (excluding target course if on this page)
            const targetOnPage = result.resultsOnPage?.some((course) => course.id === this.targetCourseId()) ?? false;
            const adjustedTotal = targetOnPage ? (result.numberOfPages ?? 1) * this.rows() - 1 : (result.numberOfPages ?? 1) * this.rows();
            this.totalRecords.set(adjustedTotal);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoadingCourses.set(false);
        }
    }

    /**
     * Handles search input changes.
     */
    onSearchChange(): void {
        this.first.set(0);
        this.loadCourses();
    }

    /**
     * Handles pagination changes.
     */
    onPageChange(event: PaginatorState): void {
        this.first.set(event.first ?? 0);
        this.rows.set(event.rows ?? 10);
        this.loadCourses();
    }

    /**
     * Selects a course and moves to the options step.
     */
    async selectCourse(course: CourseForImportDTO): Promise<void> {
        this.selectedCourse.set(course);
        await this.loadSummary();
        this.currentStep.set('selectOptions');
    }

    /**
     * Loads the summary for the selected source course.
     */
    private async loadSummary(): Promise<void> {
        const course = this.selectedCourse();
        if (!course?.id) return;

        try {
            this.isLoadingSummary.set(true);
            const summary = await lastValueFrom(this.importService.getImportSummary(this.targetCourseId(), course.id));
            this.sourceSummary.set(summary);
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isLoadingSummary.set(false);
        }
    }

    /**
     * Goes back to course selection step.
     */
    backToCourseSelection(): void {
        this.currentStep.set('selectCourse');
        this.selectedCourse.set(undefined);
        this.sourceSummary.set(undefined);
    }

    /**
     * Gets the current import options.
     */
    private getImportOptions(): CourseMaterialImportOptions {
        return {
            importExercises: this.importExercises() && this.hasExercises(),
            importLectures: this.importLectures() && this.hasLectures(),
            importExams: this.importExams() && this.hasExams(),
            importCompetencies: this.importCompetencies() && this.hasCompetencies(),
            importTutorialGroups: this.importTutorialGroups() && this.hasTutorialGroups(),
            importFaqs: this.importFaqs() && this.hasFaqs(),
        };
    }

    /**
     * Executes the import operation.
     */
    async executeImport(): Promise<void> {
        const course = this.selectedCourse();
        if (!course?.id || !this.canImport()) return;

        const options: CourseMaterialImportOptionsDTO = {
            sourceCourseId: course.id,
            ...this.getImportOptions(),
        };

        try {
            this.isImporting.set(true);
            await lastValueFrom(this.importService.importMaterial(this.targetCourseId(), options));
            this.importStarted.emit(options);
            this.alertService.success('artemisApp.course.import.success');
            this.close();
        } catch (error) {
            onError(this.alertService, error);
        } finally {
            this.isImporting.set(false);
        }
    }
}
