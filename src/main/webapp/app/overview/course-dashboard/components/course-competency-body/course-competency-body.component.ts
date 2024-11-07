import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { JudgementOfLearningRatingComponent } from 'app/course/competencies/judgement-of-learning-rating/judgement-of-learning-rating.component';
import { faChalkboardUser, faCheckCircle, faCircleInfo, faListCheck } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningObjectType, LearningPathCompetencyDTO, LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { CompetencyJol, CompetencyJolResponseType, CompetencyProgress, getConfidence, getMastery, getProgress } from 'app/entities/competency.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-course-competency-body',
    standalone: true,
    imports: [JudgementOfLearningRatingComponent, FontAwesomeModule, TranslateDirective, CommonModule],
    templateUrl: './course-competency-body.component.html',
    styleUrl: './course-competency-body.component.scss',
})
export class CourseCompetencyBodyComponent {
    protected readonly LearningObjectType = LearningObjectType;

    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faListCheck = faListCheck;
    protected readonly faChalkboardUser = faChalkboardUser;

    private readonly learningPathApiService = inject(LearningPathApiService);
    private readonly alertService = inject(AlertService);
    private readonly courseCompetencyApiService = inject(CourseCompetencyApiService);

    readonly courseId = input.required<number>();
    readonly learningPathId = input.required<number>();
    readonly courseCompetencyId = input.required<number>();
    readonly courseCompetency = input.required<LearningPathCompetencyDTO>();
    readonly courseCompetencies = input.required<LearningPathCompetencyDTO[]>();

    readonly isLoading = signal<boolean>(false);
    readonly learningObjects = signal<LearningPathNavigationObjectDTO[]>([]);
    readonly jolRating = signal<CompetencyJolResponseType | undefined>(undefined);
    readonly currentJolRating = computed(() => this.jolRating()?.current?.jolValue);

    readonly userProgress = computed(
        () =>
            this.courseCompetency()?.userProgress ??
            <CompetencyProgress>{
                progress: 0,
                confidence: 1,
            },
    );

    readonly progress = computed(() => getProgress(this.userProgress()));
    readonly confidence = computed(() => getConfidence(this.userProgress()));
    readonly mastery = computed(() => getMastery(this.userProgress()));

    readonly promptForJolRating = computed(() => CompetencyJol.shouldPromptForJol(this.courseCompetency(), this.userProgress(), this.courseCompetencies()));

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            const learningPathId = this.learningPathId();
            const courseCompetencyId = this.courseCompetencyId();
            untracked(() => this.loadData(courseId, learningPathId, courseCompetencyId));
        });
    }

    private async loadData(courseId: number, learningPathId: number, courseCompetencyId: number) {
        try {
            this.isLoading.set(true);
            await Promise.all([this.loadJoL(courseId, courseCompetencyId), this.loadLearningObjects(learningPathId, courseCompetencyId)]);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    private async loadLearningObjects(learningPathId: number, courseCompetencyId: number): Promise<void> {
        const learningObjects = await this.learningPathApiService.getLearningPathCompetencyLearningObjects(learningPathId, courseCompetencyId);
        this.learningObjects.set(learningObjects);
    }

    private async loadJoL(courseId: number, courseCompetencyId: number) {
        const jol = await this.courseCompetencyApiService.getJoL(courseId, courseCompetencyId);
        this.jolRating.set(jol);
    }
}
