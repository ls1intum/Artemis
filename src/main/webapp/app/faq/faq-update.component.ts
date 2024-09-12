import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementService } from '../course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faBan, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { Faq } from 'app/entities/faq.model';
import { FaqService } from 'app/faq/faq.service';

import { FaqCategory } from 'app/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/faq/faq.utils';

@Component({
    selector: 'jhi-faq-update',
    templateUrl: './faq-update.component.html',
    styleUrls: ['./faq-update.component.scss'],
})
export class FAQUpdateComponent implements OnInit {
    faq: Faq;
    isSaving: boolean;
    existingCategories: FaqCategory[] = [];
    exerciseCategories: FaqCategory[] = [];

    courses: Course[];

    domainActionsDescription = [new FormulaAction()];
    file: File;
    fileName: string;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faSave = faSave;
    faBan = faBan;

    constructor(
        protected alertService: AlertService,
        protected faqService: FaqService,
        protected courseService: CourseManagementService,
        protected activatedRoute: ActivatedRoute,
        private navigationUtilService: ArtemisNavigationUtilService,
        private router: Router,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.parent!.data.subscribe((data) => {
            // Create a new faq to use unless we fetch an existing faq
            const faq = data['faq'];
            this.faq = faq ?? new Faq();
            const course = data['course'];
            if (course) {
                this.faq.course = course;
                this.loadCourseFaqCategories(course.id);
            }
            if (faq.categories) {
                this.exerciseCategories = faq.categories;
            }
        });
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state and we edited an existing faq
     * Returns to the overview page if there is no previous state and we created a new faq
     */

    previousState() {
        this.navigationUtilService.navigateBack(['course-management', this.faq.course!.id!.toString(), 'faqs']);
    }
    /**
     * Save the changes on a faq
     * This function is called by pressing save after creating or editing a faq
     */
    save() {
        this.isSaving = true;
        if (this.faq.id !== undefined) {
            this.subscribeToSaveResponse(this.faqService.update(this.faq));
        } else {
            // Newly created faq must have a channel name, which cannot be undefined
            console.log(this.faq);
            this.subscribeToSaveResponse(this.faqService.create(this.faq));
        }
    }

    /**
     * @param result The Http response from the server
     */
    protected subscribeToSaveResponse(result: Observable<HttpResponse<Faq>>) {
        result.subscribe({
            next: (response: HttpResponse<Faq>) => this.onSaveSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onSaveError(error),
        });
    }

    /**
     * Action on successful faq creation or edit
     */
    protected onSaveSuccess(faq: Faq) {
        if (!this.faq.id) {
            this.faqService.find(faq.id!).subscribe({
                next: (response: HttpResponse<Faq>) => {
                    this.isSaving = false;
                    this.faq = response.body!;
                    this.alertService.success(`FAQ with title ${faq.questionTitle} was successfully created.`);
                },
            });
        } else {
            this.isSaving = false;
            this.router.navigate(['course-management', faq.course!.id, 'faqs']);
        }
    }

    /**
     * Action on unsuccessful faq creation or edit
     * @param errorRes the errorRes handed to the alert service
     */
    protected onSaveError(errorRes: HttpErrorResponse) {
        this.isSaving = false;
        if (errorRes.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
    }

    updateCategories(categories: FaqCategory[]) {
        this.faq.categories = categories;
        this.exerciseCategories = categories;
    }

    private loadCourseFaqCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }

    canSave() {
        return this.faq.questionTitle && this.faq.questionAnswer;
    }
}
