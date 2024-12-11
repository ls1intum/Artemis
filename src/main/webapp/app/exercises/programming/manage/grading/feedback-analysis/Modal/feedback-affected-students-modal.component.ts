import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FeedbackAffectedStudentDTO, FeedbackAnalysisService, FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { PageableResult, PageableSearch, SortingOrder } from 'app/shared/table/pageable-table';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-affected-students-modal',
    templateUrl: './feedback-affected-students-modal.component.html',
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule],
    providers: [FeedbackAnalysisService],
    standalone: true,
})
export class AffectedStudentsModalComponent {
    exerciseId = input.required<number>();
    feedbackDetail = input.required<FeedbackDetail>();
    groupFeedback = input.required<boolean>();
    readonly participation = signal<PageableResult<FeedbackAffectedStudentDTO>>({ content: [], totalPages: 0, totalElements: 0 });
    readonly TRANSLATION_BASE = 'artemisApp.programmingExercise.configureGrading.feedbackAnalysis.affectedStudentsModal';

    page = signal<number>(1);
    pageSize = signal<number>(10);
    readonly collectionsSize = computed(() => this.participation().totalPages * this.pageSize());
    readonly faSpinner = faSpinner;
    readonly isLoading = signal<boolean>(false);

    activeModal = inject(NgbActiveModal);
    feedbackService = inject(FeedbackAnalysisService);
    alertService = inject(AlertService);

    constructor() {
        effect(() => {
            untracked(async () => {
                await this.loadAffected();
            });
        });
    }

    private async loadAffected() {
        const feedbackDetail = this.feedbackDetail();
        const pageable: PageableSearch = {
            page: this.page(),
            pageSize: this.pageSize(),
            sortedColumn: 'participationId',
            sortingOrder: SortingOrder.ASCENDING,
        };

        this.isLoading.set(true);
        try {
            const response = await this.feedbackService.getParticipationForFeedbackDetailText(this.exerciseId(), feedbackDetail.feedbackIds, feedbackDetail.testCaseName, pageable);
            this.participation.set(response);
        } catch (error) {
            this.alertService.error(this.TRANSLATION_BASE + '.error');
        } finally {
            this.isLoading.set(false);
        }
    }

    setPage(newPage: number): void {
        this.page.set(newPage);
        this.loadAffected();
    }
}
