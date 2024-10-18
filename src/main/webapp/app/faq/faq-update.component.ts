import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faBan, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { Faq, FaqState } from 'app/entities/faq.model';
import { FaqService } from 'app/faq/faq.service';
import { TranslateService } from '@ngx-translate/core';
import { FaqCategory } from 'app/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/faq/faq.utils';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisCategorySelectorModule } from 'app/shared/category-selector/category-selector.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-faq-update',
    templateUrl: './faq-update.component.html',
    styleUrls: ['./faq-update.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisMarkdownEditorModule, ArtemisCategorySelectorModule],
})
export class FaqUpdateComponent implements OnInit {
    faq: Faq;
    isSaving: boolean;
    isAllowedToSave: boolean;
    existingCategories: FaqCategory[];
    faqCategories: FaqCategory[];
    courseId: number;
    isAtLeastInstructor: boolean = false;
    domainActionsDescription = [new FormulaAction()];

    // Icons
    readonly faQuestionCircle = faQuestionCircle;
    readonly faSave = faSave;
    readonly faBan = faBan;

    private alertService = inject(AlertService);
    private faqService = inject(FaqService);
    private activatedRoute = inject(ActivatedRoute);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private router = inject(Router);
    private translateService = inject(TranslateService);
    private accountService = inject(AccountService);

    ngOnInit() {
        this.isSaving = false;
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.activatedRoute.parent?.data.subscribe((data) => {
            // Create a new faq to use unless we fetch an existing faq
            const faq = data['faq'];
            this.faq = faq ?? new Faq();
            const course = data['course'];
            if (course) {
                this.faq.course = course;
                this.loadCourseFaqCategories(course.id);
                this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(course);
            }
            this.faqCategories = faq?.categories ? faq.categories : [];
        });
        this.validate();
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state and we edited an existing faq
     * Returns to the overview page if there is no previous state and we created a new faq
     */

    previousState() {
        this.navigationUtilService.navigateBack(['course-management', this.courseId, 'faqs']);
    }
    /**
     * Save the changes on a faq
     * This function is called by pressing save after creating or editing a faq
     */
    save() {
        this.isSaving = true;
        this.faq.faqState = this.isAtLeastInstructor ? FaqState.ACCEPTED : FaqState.PROPOSED;
        if (this.faq.id !== undefined) {
            this.subscribeToSaveResponse(this.faqService.update(this.courseId, this.faq));
        } else {
            this.subscribeToSaveResponse(this.faqService.create(this.courseId, this.faq));
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
            this.faqService.find(this.courseId, faq.id!).subscribe({
                next: (response: HttpResponse<Faq>) => {
                    this.isSaving = false;
                    const faqBody = response.body;
                    if (faqBody) {
                        this.faq = faqBody;
                    }
                    this.showSuccessAlert(faq);

                    this.router.navigate(['course-management', this.courseId, 'faqs']);
                },
            });
        } else {
            this.showSuccessAlert(faq);
            this.router.navigate(['course-management', this.courseId, 'faqs']);
        }
    }

    private showSuccessAlert(faq: Faq): void {
        let messageKey: string;

        if (this.isAtLeastInstructor) {
            messageKey = faq.id ? 'artemisApp.faq.updated' : 'artemisApp.faq.created';
        } else {
            messageKey = faq.id ? 'artemisApp.faq.proposedChange' : 'artemisApp.faq.proposed';
        }
        this.alertService.success(messageKey, { id: faq.questionTitle });
    }

    /**
     * Action on unsuccessful faq creation or edit
     * @param errorRes the errorRes handed to the alert service
     */
    protected onSaveError(errorRes: HttpErrorResponse) {
        this.isSaving = false;
        if (errorRes.error?.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
    }

    updateCategories(categories: FaqCategory[]) {
        this.faq.categories = categories;
        this.faqCategories = categories;
    }

    private loadCourseFaqCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }

    validate() {
        if (this.faq.questionTitle && this.faq.questionAnswer) {
            this.isAllowedToSave = this.faq.questionTitle?.trim().length > 0 && this.faq.questionAnswer?.trim().length > 0;
        } else {
            this.isAllowedToSave = false;
        }
    }

    handleMarkdownChange(markdown: string): void {
        this.faq.questionAnswer = markdown;
        this.validate();
    }
}
