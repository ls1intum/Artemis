import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject, model } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CourseCompetency, CourseCompetencyType, getIcon } from 'app/atlas/shared/entities/competency.model';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';
import { faFileImport, faLightbulb, faPencilAlt, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbProgressbar, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/atlas/manage/competency-management/import-all-competencies.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RouterModule } from '@angular/router';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

export interface SuggestedCompetency extends CourseCompetency {
    isSuggested?: boolean;
}

@Component({
    selector: 'jhi-competency-management-table',
    templateUrl: './competency-management-table.component.html',
    imports: [
        NgbProgressbar,
        NgbDropdown,
        NgbDropdownMenu,
        NgbDropdownToggle,
        NgbTooltip,
        HtmlForMarkdownPipe,
        TranslateDirective,
        FontAwesomeModule,
        DeleteButtonDirective,
        ArtemisTranslatePipe,
        RouterModule,
        ArtemisDatePipe,
    ],
})
export class CompetencyManagementTableComponent implements OnInit, OnDestroy {
    @Input() competencyType: CourseCompetencyType;

    courseId: number;
    courseCompetencies: CourseCompetency[] = [];
    standardizedCompetenciesEnabled = false;

    allCompetencies = model<CourseCompetency[]>([]);

    @Output() competencyDeleted = new EventEmitter<number>();

    // Injected services
    private readonly activatedRoute = inject(ActivatedRoute);

    service: CompetencyService | PrerequisiteService;
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    // Suggested competencies functionality
    suggestedCompetencies: SuggestedCompetency[] = [];
    showSuggestions = false;
    isLoadingSuggestions = false;

    private readonly competencyService: CompetencyService = inject(CompetencyService);
    private readonly prerequisiteService: PrerequisiteService = inject(PrerequisiteService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly modalService: NgbModal = inject(NgbModal);

    readonly faFileImport = faFileImport;
    readonly faPlus = faPlus;
    readonly faPencilAlt = faPencilAlt;
    readonly faTrash = faTrash;
    readonly faLightbulb = faLightbulb;

    readonly getIcon = getIcon;

    get displayedCompetencies(): SuggestedCompetency[] {
        const regular = this.courseCompetencies.map((comp) => ({ ...comp, isSuggested: false }));
        if (this.showSuggestions) {
            return [...regular, ...this.suggestedCompetencies];
        }
        return regular;
    }

    ngOnInit(): void {
        // Get courseId from route parameters
        this.activatedRoute.parent?.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
            this.loadData();
        });

        if (this.competencyType === CourseCompetencyType.COMPETENCY) {
            this.service = this.competencyService;
        } else {
            this.service = this.prerequisiteService;
        }
    }

    private loadData(): void {
        // For now, we'll just initialize empty data
        // In a real implementation, this would load course competencies
        this.courseCompetencies = [];
        this.allCompetencies.set([]);
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.complete();
    }

    /**
     * Toggle the display of suggested competencies
     */
    toggleSuggestions(): void {
        if (!this.showSuggestions && this.suggestedCompetencies.length === 0) {
            this.loadSuggestions();
        }
        this.showSuggestions = !this.showSuggestions;
    }

    /**
     * Load competency suggestions
     */
    private loadSuggestions(): void {
        this.isLoadingSuggestions = true;

        // Use a mock description for now - in a real implementation,
        // this would come from course description or user input
        const mockDescription = 'Programming course covering object-oriented concepts, data structures, and algorithms';

        this.competencyService.getSuggestedCompetencies(mockDescription).subscribe({
            next: (response) => {
                if (response.body?.competencies) {
                    this.suggestedCompetencies = response.body.competencies.map((comp) => ({
                        ...comp,
                        isSuggested: true,
                    }));
                }
                this.isLoadingSuggestions = false;
            },
            error: (error: HttpErrorResponse) => {
                this.isLoadingSuggestions = false;
                // Optionally show a subtle notification
                this.alertService.warning('artemisApp.competency.suggestions.loadError');
            },
        });
    }

    /**
     * Accept a suggested competency and add it to the course
     */
    acceptSuggestedCompetency(suggestedCompetency: SuggestedCompetency): void {
        const competency = { ...suggestedCompetency };
        delete competency.isSuggested;
        delete competency.id; // Remove ID so it gets a new one when created

        this.competencyService.create(competency, this.courseId).subscribe({
            next: (response) => {
                if (response.body) {
                    // Add to regular competencies
                    this.courseCompetencies.push(response.body);
                    this.allCompetencies.update((competencies) => [...competencies, response.body!]);

                    // Remove from suggestions
                    this.suggestedCompetencies = this.suggestedCompetencies.filter((s) => s.title !== suggestedCompetency.title);

                    this.alertService.success('artemisApp.competency.created');
                }
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Dismiss a suggested competency
     */
    dismissSuggestedCompetency(suggestedCompetency: SuggestedCompetency): void {
        this.suggestedCompetencies = this.suggestedCompetencies.filter((s) => s.title !== suggestedCompetency.title);
    }

    deleteCompetency(competencyId: number): void {
        this.service.delete(competencyId, this.courseId).subscribe({
            next: () => {
                this.competencyDeleted.emit(competencyId);
                this.allCompetencies.update((competencies) => competencies.filter((comp) => comp.id !== competencyId));
            },
            error: (res: HttpErrorResponse) => this.dialogErrorSource.next(res.message),
        });
    }

    openImportAllModal(): void {
        const modalRef = this.modalService.open(ImportAllCompetenciesComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.competencyType = this.competencyType;

        modalRef.result
            .then((result: ImportAllFromCourseResult) => {
                if (result) {
                    // Handle import results
                    this.alertService.success('artemisApp.competency.importAll.success');
                    // Refresh competencies list would go here
                }
            })
            .catch(() => {
                // Modal was dismissed
            });
    }
}
