import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from 'app/core/util/alert.service';
import { CompetencyGraphDTO } from 'app/entities/competency/learning-path.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-competency-graph-modal',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FontAwesomeModule, CompetencyGraphComponent, TranslateDirective],
    templateUrl: './competency-graph-modal.component.html',
    styleUrl: './competency-graph-modal.component.scss',
})
export class CompetencyGraphModalComponent {
    protected readonly closeIcon = faXmark;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);

    readonly learningPathId = input.required<number>();

    readonly isLoading = signal<boolean>(false);
    readonly competencyGraph = signal<CompetencyGraphDTO | undefined>(undefined);
    private readonly activeModal: NgbActiveModal = inject(NgbActiveModal);

    constructor() {
        effect(
            () => {
                const learningPathId = this.learningPathId();
                untracked(() => this.loadCompetencyGraph(learningPathId));
            },
            { allowSignalWrites: true },
        );
    }

    private async loadCompetencyGraph(learningPathId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const competencyGraph = await this.learningPathApiService.getLearningPathCompetencyGraph(learningPathId);
            this.competencyGraph.set(competencyGraph);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    closeModal(): void {
        this.activeModal.close();
    }

    static openCompetencyGraphModal(modalService: NgbModal, learningPathId: number): void {
        const modalRef = modalService.open(CompetencyGraphModalComponent, {
            size: 'xl',
            backdrop: 'static',
            windowClass: 'competency-graph-modal',
        });
        modalRef.componentInstance.learningPathId = signal<number>(learningPathId);
    }
}
