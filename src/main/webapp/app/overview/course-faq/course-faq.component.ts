import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { Subject, Subscription } from 'rxjs';
import { faFilter, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { SidebarData } from 'app/types/sidebar';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseFaqAccordionComponent } from 'app/overview/course-faq/course-faq-accordion-component';
import { FAQ } from 'app/entities/faq.model';
import { FAQService } from 'app/faq/faq.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FAQCategory } from 'app/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/faq/faq.utils';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-course-faq',
    templateUrl: './course-faq.component.html',
    styleUrls: ['../course-overview.scss', 'course-faq.component.scss'],
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [ArtemisSharedComponentModule, ArtemisSharedModule, CourseFaqAccordionComponent, CustomExerciseCategoryBadgeComponent],
})
export class CourseFaqComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    private parentParamSubscription: Subscription;

    courseId: number;
    faqs: FAQ[];

    filteredFaqs: FAQ[];
    existingCategories: FAQCategory[];
    activeFilters = new Set<string>();

    sidebarData: SidebarData;
    hasCategories = false;
    isCollapsed = false;
    isProduction = true;
    isTestServer = false;

    readonly ButtonType = ButtonType;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private faqService: FAQService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
            this.loadFaqs();
            this.loadCourseExerciseCategories(this.courseId);
        });
    }

    private loadCourseExerciseCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
            this.hasCategories = existingCategories.length > 0;
        });
    }

    private loadFaqs() {
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

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.parentParamSubscription?.unsubscribe();
    }

    toggleFilters(category: string) {
        this.activeFilters = this.faqService.toggleFilter(category, this.activeFilters);
        this.applyFilters();
    }

    private applyFilters(): void {
        this.filteredFaqs = this.faqService.applyFilters(this.activeFilters, this.faqs);
    }
}
