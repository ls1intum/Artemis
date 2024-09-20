import { Component, OnDestroy, OnInit } from '@angular/core';
import { FAQ } from 'app/entities/faq.model';
import { faEdit, faFile, faFileExport, faFileImport, faFilter, faPencilAlt, faPlus, faPuzzlePiece, faSort, faTrash } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { map } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute } from '@angular/router';
import { FAQService } from 'app/faq/faq.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { FAQCategory } from 'app/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/faq/faq.utils';
import { SortService } from 'app/shared/service/sort.service';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@Component({
    selector: 'jhi-faq',
    templateUrl: './faq.component.html',
    styleUrls: ['./faq.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, CustomExerciseCategoryBadgeComponent, ArtemisSharedComponentModule, ArtemisMarkdownModule],
})
export class FAQComponent implements OnInit, OnDestroy {
    faqs: FAQ[];
    filteredFaqs: FAQ[];
    existingCategories: FAQCategory[];
    courseId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    activeFilters = new Set<string>();
    predicate: string;
    ascending: boolean;

    irisEnabled = false;

    // Icons
    faEdit = faEdit;
    faPlus = faPlus;
    faFileImport = faFileImport;
    faFileExport = faFileExport;
    faTrash = faTrash;
    faPencilAlt = faPencilAlt;
    faFile = faFile;
    faPuzzlePiece = faPuzzlePiece;
    faFilter = faFilter;
    faSort = faSort;

    constructor(
        protected faqService: FAQService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private sortService: SortService,
    ) {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll();
        this.loadCourseFaqCategories(this.courseId);
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.complete();
    }

    trackId(index: number, item: FAQ) {
        return item.id;
    }

    deleteFaq(faqId: number) {
        this.faqService.delete(faqId).subscribe({
            next: () => this.handleDeleteSuccess(faqId),
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private handleDeleteSuccess(faqId: number) {
        this.faqs = this.faqs.filter((faq) => faq.id !== faqId);
        this.dialogErrorSource.next('');
        this.applyFilters();
    }

    toggleFilters(category: string) {
        this.activeFilters = this.faqService.toggleFilter(category, this.activeFilters);
        this.applyFilters();
    }

    private applyFilters(): void {
        this.filteredFaqs = this.faqService.applyFilters(this.activeFilters, this.faqs);
    }

    sortRows() {
        this.sortService.sortByProperty(this.filteredFaqs, this.predicate, this.ascending);
    }

    private loadAll() {
        this.faqService
            .findAllByCourseId(this.courseId)
            .pipe(map((res: HttpResponse<FAQ[]>) => res.body))
            .subscribe({
                next: (res: FAQ[]) => {
                    this.faqs = res;
                    this.applyFilters();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    private loadCourseFaqCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }
}
