import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, input, signal } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { booleanLabel } from 'app/exercise/version-history/shared/version-history.utils';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';
import { PanelModule } from 'primeng/panel';
import { DividerModule } from 'primeng/divider';
import { TagModule } from 'primeng/tag';

/** A label/value pair for a plain-text or numeric metadata field. */
interface MetadataField {
    label: string;
    value?: string | number;
    /** Display-ready value: the raw value or a dash placeholder when missing. */
    displayValue: string | number;
}

/** A label/value pair for a date metadata field. */
interface MetadataDateField {
    label: string;
    value?: dayjs.Dayjs;
}

/**
 * Renders the exercise-type-agnostic portion of a version snapshot:
 * general settings, dates, categories, complaint flags, problem statement,
 * and grading instructions.
 *
 * Markdown sections (problem statement, grading instructions) are rendered
 * with task and PlantUML extension support and injected into the DOM as sanitized HTML.
 */
@Component({
    selector: 'jhi-exercise-version-shared-snapshot-metadata',
    templateUrl: './exercise-version-shared-snapshot-metadata.component.html',
    styleUrls: ['./exercise-version-shared-snapshot-metadata.component.scss'],
    imports: [PanelModule, DividerModule, TagModule, TranslateDirective, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseVersionSharedSnapshotMetadataComponent implements OnDestroy {
    private readonly markdownService = inject(ArtemisMarkdownService);
    private readonly translateService = inject(TranslateService);
    private readonly plantUmlWrapper = inject(ProgrammingExercisePlantUmlExtensionWrapper);
    private readonly taskWrapper = inject(ProgrammingExerciseTaskExtensionWrapper);

    private readonly injectableCallbacks: Array<() => void> = [];
    private readonly injectableContentFoundSubscription: Subscription;
    private plantUmlTimeoutId?: ReturnType<typeof setTimeout>;

    /** The full exercise snapshot to render. Triggers markdown re-rendering on change. */
    readonly snapshot = input.required<ExerciseSnapshotDTO>();

    /** Rendered problem statement HTML (sanitized via DOMPurify). */
    readonly problemStatement = signal<SafeHtml | undefined>(undefined);
    /** Rendered grading instructions HTML (sanitized via DOMPurify). */
    readonly gradingInstructions = signal<SafeHtml | undefined>(undefined);

    constructor() {
        this.injectableContentFoundSubscription = this.plantUmlWrapper.subscribeForInjectableElementsFound().subscribe((injectableCallback) => {
            this.injectableCallbacks.push(injectableCallback);
        });

        effect(() => {
            const snapshot = this.snapshot();
            this.renderMarkdownSections(snapshot);
        });
    }

    ngOnDestroy(): void {
        this.injectableContentFoundSubscription.unsubscribe();
        if (this.plantUmlTimeoutId !== undefined) {
            clearTimeout(this.plantUmlTimeoutId);
        }
    }

    /** General exercise properties (title, short name, difficulty, points, etc.). */
    readonly generalFields = computed<MetadataField[]>(() => {
        const snapshot = this.snapshot();
        return [
            this.toField('artemisApp.exercise.title', snapshot.title),
            this.toField('artemisApp.exercise.shortName', snapshot.shortName),
            this.toField('artemisApp.lecture.channelName', snapshot.channelName),
            this.toField('artemisApp.assessmentMode', this.translateEnum('artemisApp.AssessmentType', snapshot.assessmentType)),
            this.toField('artemisApp.exercise.difficulty', this.translateEnum('artemisApp.DifficultyLevel', snapshot.difficulty)),
            this.toField('artemisApp.exercise.mode', this.humanizeEnum(snapshot.mode)),
            this.toField('artemisApp.exercise.points', snapshot.maxPoints),
            this.toField('artemisApp.exercise.bonusPoints', snapshot.bonusPoints),
            this.toField('artemisApp.exercise.includedInOverallScore', this.getIncludedInScoreLabel(snapshot.includedInOverallScore)),
        ];
    });

    /** Exercise lifecycle dates (release, start, due, assessment due, example solution). */
    readonly dateFields = computed<MetadataDateField[]>(() => {
        const snapshot = this.snapshot();
        return [
            { label: 'artemisApp.exercise.releaseDate', value: this.toDate(snapshot.releaseDate) },
            { label: 'artemisApp.exercise.startDate', value: this.toDate(snapshot.startDate) },
            { label: 'artemisApp.exercise.dueDate', value: this.toDate(snapshot.dueDate) },
            { label: 'artemisApp.exercise.assessmentDueDate', value: this.toDate(snapshot.assessmentDueDate) },
            { label: 'artemisApp.exercise.exampleSolutionPublicationDate', value: this.toDate(snapshot.exampleSolutionPublicationDate) },
        ];
    });

    /** Boolean flags for feedback requests and complaint settings. */
    readonly flagFields = computed<MetadataField[]>(() => {
        const snapshot = this.snapshot();
        return [
            this.toField('artemisApp.programmingExercise.timeline.manualFeedbackRequests', booleanLabel(this.translateService, snapshot.allowFeedbackRequests)),
            this.toField(
                'artemisApp.programmingExercise.timeline.complaintOnAutomaticAssessment',
                booleanLabel(this.translateService, snapshot.allowComplaintsForAutomaticAssessments),
            ),
        ];
    });

    /** Exercise category tags, falling back to an empty array. */
    readonly categories = computed(() => this.snapshot().categories ?? []);

    /** Creates a metadata field with a pre-computed display value. */
    private toField(label: string, value?: string | number): MetadataField {
        return { label, value, displayValue: value ?? '-' };
    }

    /**
     * Converts the problem statement and grading instructions to sanitized HTML.
     *
     * Callbacks are cleared and exerciseId is reset before each render to
     * prevent cross-contamination between snapshots. PlantUML injectable
     * elements are activated on the next microtask.
     */
    private renderMarkdownSections(snapshot: ExerciseSnapshotDTO): void {
        if (this.plantUmlTimeoutId !== undefined) {
            clearTimeout(this.plantUmlTimeoutId);
        }
        this.injectableCallbacks.length = 0;
        this.plantUmlWrapper.setExerciseId(snapshot.id);

        this.problemStatement.set(this.renderMarkdown(snapshot.problemStatement));
        this.gradingInstructions.set(this.renderMarkdown(snapshot.gradingInstructions));

        this.plantUmlTimeoutId = setTimeout(() => {
            this.injectableCallbacks.forEach((callback) => callback());
        }, 0);
    }

    /** Renders markdown to sanitized SafeHtml with task and PlantUML extension support. */
    private renderMarkdown(markdown?: string): SafeHtml | undefined {
        if (!markdown?.trim()) {
            return undefined;
        }

        return this.markdownService.safeHtmlForMarkdown(markdown, [this.taskWrapper.getExtension(), this.plantUmlWrapper.getExtension()]);
    }

    /** Parses an ISO date string into a dayjs instance, or returns `undefined`. */
    private toDate(value?: string): dayjs.Dayjs | undefined {
        return value ? dayjs(value) : undefined;
    }

    /** Replaces underscores with spaces (e.g. `SOME_VALUE` → `SOME VALUE`). */
    private humanizeEnum(value?: string): string | undefined {
        return value ? value.replaceAll('_', ' ') : undefined;
    }

    /** Translates an enum value via i18n, falling back to humanized form if no key exists. */
    private translateEnum(prefix: string, value?: string): string | undefined {
        if (!value) {
            return undefined;
        }
        const key = `${prefix}.${value}`;
        const translated = this.translateService.instant(key);
        return translated === key ? this.humanizeEnum(value) : translated;
    }

    /** Maps an {@link IncludedInOverallScore} enum to a translated display label. */
    private getIncludedInScoreLabel(value?: IncludedInOverallScore): string | undefined {
        if (!value) {
            return undefined;
        }

        switch (value) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.translateService.instant('artemisApp.exercise.yes');
            case IncludedInOverallScore.NOT_INCLUDED:
                return this.translateService.instant('artemisApp.exercise.no');
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return this.translateService.instant('artemisApp.exercise.bonus');
            default:
                return this.humanizeEnum(value);
        }
    }
}
