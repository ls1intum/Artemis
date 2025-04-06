import { Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject, model } from '@angular/core';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { AlertService } from 'app/shared/service/alert.service';
import { CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyType, getIcon } from 'app/atlas/shared/entities/competency.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { filter, map } from 'rxjs/operators';
import { onError } from 'app/shared/util/global.utils';
import { Subject } from 'rxjs';
import { faFileImport, faPencilAlt, faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { ImportAllCompetenciesComponent, ImportAllFromCourseResult } from 'app/atlas/manage/competency-management/import-all-competencies.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RouterModule } from '@angular/router';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-competency-management-table',
    templateUrl: './competency-management-table.component.html',
    imports: [
        NgbProgressbar,
        NgbDropdown,
        NgbDropdownMenu,
        NgbDropdownToggle,
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
    @Input() courseId: number;
    @Input() courseCompetencies: CourseCompetency[] = [];
    @Input() competencyType: CourseCompetencyType;
    @Input() standardizedCompetenciesEnabled: boolean;

    allCompetencies = model.required<CourseCompetency[]>();

    @Output() competencyDeleted = new EventEmitter<number>();

    service: CompetencyService | PrerequisiteService;
    private dialogErrorSource = new Subject<string>();
    dialogError = this.dialogErrorSource.asObservable();

    // Injected services
    private readonly competencyService: CompetencyService = inject(CompetencyService);
    private readonly prerequisiteService: PrerequisiteService = inject(PrerequisiteService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly modalService: NgbModal = inject(NgbModal);

    readonly faFileImport = faFileImport;
    readonly faPlus = faPlus;
    readonly faPencilAlt = faPencilAlt;
    readonly faTrash = faTrash;

    readonly getIcon = getIcon;

    ngOnInit(): void {
        if (this.competencyType === CourseCompetencyType.COMPETENCY) {
            this.service = this.competencyService;
        } else {
            this.service = this.prerequisiteService;
        }
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Opens a modal for selecting a course to import all competencies from.
     */
    openImportAllModal() {
        const modalRef = this.modalService.open(ImportAllCompetenciesComponent, { size: 'lg', backdrop: 'static' });
        //unary operator is necessary as otherwise courseId is seen as a string and will not match.
        modalRef.componentInstance.disabledIds = [+this.courseId];
        modalRef.componentInstance.competencyType = this.competencyType;
        modalRef.result.then((result: ImportAllFromCourseResult) => {
            const courseTitle = result.courseForImportDTO.title ?? '';

            this.service
                .importAll(this.courseId, result.courseForImportDTO.id!, result.importRelations)
                .pipe(
                    filter((res: HttpResponse<Array<CompetencyWithTailRelationDTO>>) => res.ok),
                    map((res: HttpResponse<Array<CompetencyWithTailRelationDTO>>) => res.body),
                )
                .subscribe({
                    next: (res: Array<CompetencyWithTailRelationDTO>) => {
                        if (res.length > 0) {
                            this.alertService.success(`artemisApp.${this.competencyType}.importAll.success`, { noOfCompetencies: res.length, courseTitle: courseTitle });
                            this.updateDataAfterImportAll(res);
                        } else {
                            this.alertService.warning(`artemisApp.${this.competencyType}.importAll.warning`, { courseTitle: courseTitle });
                        }
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });
        });
    }

    /**
     * Updates the component and its relation chart with the new data from the importAll modal
     * @param res Array of DTOs containing the new competencies and relations
     * @private
     */
    updateDataAfterImportAll(res: Array<CompetencyWithTailRelationDTO>) {
        const importedCompetencies = res.map((dto) => dto.competency).filter((element): element is CourseCompetency => !!element);
        const newCourseCompetencies = importedCompetencies.filter((competency) => !this.courseCompetencies.some((existingCompetency) => existingCompetency.id === competency.id));
        this.courseCompetencies.push(...newCourseCompetencies);
        this.allCompetencies.update((allCourseCompetencies) => allCourseCompetencies.concat(importedCompetencies));
    }

    /**
     * Delete a competency (and its relations)
     *
     * @param competencyId the id of the competency
     */
    deleteCompetency(competencyId: number) {
        this.service.delete(competencyId, this.courseId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.competencyDeleted.next(competencyId);
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
