import { Component, OnDestroy, OnInit } from '@angular/core';
import { Faq } from 'app/entities/faq.model';
import {
    faEdit,
    faFile,
    faFileExport,
    faFileImport,
    faFilter,
    faPencilAlt,
    faPlus,
    faPuzzlePiece,
    faSort,
    faTrash
} from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { map } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute } from '@angular/router';
import { FaqService } from 'app/faq/faq.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { FaqCategory } from 'app/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/faq/faq.utils';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-faq',
    templateUrl: './faq.component.html'

})

export class FAQComponent implements OnInit, OnDestroy {
    faqs: Faq[];
    filteredFaq: Faq[];
    existingCategories: FaqCategory[]
    courseId: number;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    activeFilters = new Set<String>();
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
        protected faqService: FaqService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private sortService: SortService,
    ) {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.loadAll()
        this.loadCourseExerciseCategories(this.courseId)
    }

    ngOnDestroy(): void {
        this.dialogErrorSource.unsubscribe();
    }

    trackId(index: number, item: Faq) {
        return item.id;
    }

    deleteFaq(faqId: number) {
        this.faqService.delete(faqId).subscribe({
            next: () =>
                this.handleDeleteSuccess(faqId),
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    private handleDeleteSuccess(faqId: number) {
        this.faqs = this.faqs.filter(faq => faq.id !== faqId);
        this.dialogErrorSource.next('');
        this.applyFilters();
    }

    toggleFilters(category: String) {
        this.activeFilters.has(category)? this.activeFilters.delete(category) : this.activeFilters.add(category)
        this.applyFilters();
    }

    sortRows() {
        this.sortService.sortByProperty(this.filteredFaq, this.predicate, this.ascending);
    }

    private loadAll() {
        this.faqService.findAllByCourseId(this.courseId)
        .pipe(
            map((res: HttpResponse<Faq[]>) => res.body),
        )
        .subscribe({
            next: (res: Faq[]) => {
                this.faqs = res;
                this.applyFilters()
            },
            error: (res: HttpErrorResponse) => onError(this.alertService, res),
        });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }


    private applyFilters(): void {
        if (this.activeFilters.size === 0) {
            // If no filters selected, show all faqs
            this.filteredFaq = this.faqs;
        } else {
            this.filteredFaq = this.faqs.filter((faq) => this.hasFilteredCategory(faq, this.activeFilters));
        }

    }

    public hasFilteredCategory(faq: Faq, filteredCategory: Set<String>){
        let categories = faq.categories?.map((category) => category.category)
        if(categories){
            return categories.some(category => filteredCategory.has(category!));
        }

    }
}
