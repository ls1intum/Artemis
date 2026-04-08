import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs from 'dayjs/esm';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { TranslateService } from '@ngx-translate/core';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { normalizeCategoryArray, normalizeCategoryEntry } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot-shared.mapper';

/**
 * Single field conflict between current editor state and incoming snapshot.
 */
export interface ExerciseMetadataConflictItem {
    field: string;
    labelKey: string;
    currentValue: unknown;
    incomingValue: unknown;
}

/**
 * Resolution decision for a single conflicting field.
 */
export interface ExerciseMetadataConflictDecision {
    field: string;
    useIncoming: boolean;
}

/**
 * Result payload returned by the conflict resolution modal.
 */
export interface ExerciseMetadataConflictModalResult {
    decisions: ExerciseMetadataConflictDecision[];
}

/**
 * Data passed to the dynamic dialog via DynamicDialogConfig.
 */
export interface ExerciseMetadataConflictModalData {
    conflicts: ExerciseMetadataConflictItem[];
    author: UserPublicInfoDTO;
    versionId: number;
    exerciseId?: number;
    exerciseType?: ExerciseType;
}

@Component({
    selector: 'jhi-exercise-metadata-conflict-modal',
    templateUrl: './exercise-metadata-conflict-modal.component.html',
    styleUrls: ['./exercise-metadata-conflict-modal.component.scss'],
    imports: [NgClass, TranslateDirective, CustomExerciseCategoryBadgeComponent, ButtonModule, TableModule],
})
export class ExerciseMetadataConflictModalComponent implements OnInit {
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);
    private readonly translateService = inject(TranslateService);

    readonly conflicts = signal<ExerciseMetadataConflictItem[]>([]);
    readonly author = signal<UserPublicInfoDTO | undefined>(undefined);
    readonly versionId = signal<number | undefined>(undefined);
    readonly decisions = signal<Record<string, boolean>>({});
    readonly exerciseId = signal<number | undefined>(undefined);
    readonly exerciseType = signal<ExerciseType | undefined>(undefined);

    readonly authorName = computed(() => {
        const author = this.author();
        if (!author) {
            return '';
        }
        const fullName = [author.firstName, author.lastName].filter(Boolean).join(' ');
        return author.name ?? (fullName || author.login || '');
    });

    readonly allLocal = computed(() => {
        const d = this.decisions();
        const conflicts = this.conflicts();
        if (conflicts.length === 0) {
            return true;
        }
        return conflicts.every((conflict) => d[conflict.field] === false);
    });

    readonly allIncoming = computed(() => {
        const d = this.decisions();
        const conflicts = this.conflicts();
        if (conflicts.length === 0) {
            return false;
        }
        return conflicts.every((conflict) => d[conflict.field] === true);
    });

    ngOnInit(): void {
        const data = this.dialogConfig.data as ExerciseMetadataConflictModalData | undefined;
        if (data) {
            this.setConflicts(data.conflicts);
            this.author.set(data.author);
            this.versionId.set(data.versionId);
            this.exerciseId.set(data.exerciseId);
            this.exerciseType.set(data.exerciseType);
        }
    }

    /**
     * Initializes conflicts and decision state for the modal.
     */
    setConflicts(conflicts: ExerciseMetadataConflictItem[]): void {
        this.conflicts.set(conflicts);
        const nextDecisions: Record<string, boolean> = {};
        for (const conflict of conflicts) {
            nextDecisions[conflict.field] = false;
        }
        this.decisions.set(nextDecisions);
    }

    /**
     * Sets all conflict decisions to the given value.
     */
    setAllDecisions(useIncoming: boolean): void {
        const updated: Record<string, boolean> = {};
        for (const conflict of this.conflicts()) {
            updated[conflict.field] = useIncoming;
        }
        this.decisions.set(updated);
    }

    /**
     * Updates the decision state for a single field.
     */
    updateDecision(field: string, value: boolean): void {
        const updated = Object.assign({}, this.decisions());
        updated[field] = value;
        this.decisions.set(updated);
    }

    /**
     * Emits the chosen decisions and closes the modal.
     */
    applySelections(): void {
        const decisions = this.conflicts().map((conflict) => ({
            field: conflict.field,
            useIncoming: Boolean(this.decisions()[conflict.field]),
        }));
        this.dialogRef.close({ decisions } satisfies ExerciseMetadataConflictModalResult);
    }

    /**
     * Emits decisions that keep all local values and closes the modal.
     */
    keepLocalChanges(): void {
        const decisions = this.conflicts().map((conflict) => ({
            field: conflict.field,
            useIncoming: false,
        }));
        this.dialogRef.close({ decisions } satisfies ExerciseMetadataConflictModalResult);
    }

    /**
     * Formats values for display in the conflict tiles.
     */
    formatValue(value: unknown): string {
        if (value === undefined || value === null) {
            return '\u2014';
        }
        if (dayjs.isDayjs(value)) {
            return value.format('YYYY-MM-DD HH:mm');
        }
        // Fallback for dayjs-like objects that fail isDayjs() due to module duplication
        if (typeof value === 'object' && value !== null && typeof (value as any).format === 'function') {
            const formatted = (value as any).format('YYYY-MM-DD HH:mm');
            if (typeof formatted === 'string') {
                return formatted;
            }
        }
        if (typeof value === 'string') {
            return this.formatIncludedInOverallScore(value) ?? value;
        }
        if (typeof value === 'number' || typeof value === 'boolean') {
            return String(value);
        }
        try {
            return JSON.stringify(value);
        } catch {
            return String(value);
        }
    }

    /**
     * Returns true when the conflict field represents build configuration.
     */
    isBuildConfigField(field: string): boolean {
        return field === 'programmingData.buildConfig';
    }

    /**
     * Returns true when the conflict field represents categories.
     */
    isCategoriesField(field: string): boolean {
        return field === 'categories';
    }

    /**
     * Returns true when the conflict field represents competency links.
     */
    isCompetencyLinksField(field: string): boolean {
        return field === 'competencyLinks';
    }

    /**
     * Returns true when the conflict field represents grading criteria.
     */
    isGradingCriteriaField(field: string): boolean {
        return field === 'gradingCriteria';
    }

    /**
     * Maps grading criteria values into a display-friendly structure.
     */
    toGradingCriteriaDisplay(value: unknown): Array<{ title: string; instructions: GradingInstruction[] }> {
        const criteria = value as GradingCriterion[] | undefined;
        if (!criteria || !Array.isArray(criteria)) {
            return [];
        }
        return criteria.map((criterion) => ({
            title: criterion.title || this.translateService.instant('artemisApp.exercise.metadataSync.untitledCriterion'),
            instructions: criterion.structuredGradingInstructions ?? [],
        }));
    }

    /**
     * Maps build config values into a list of entries for each tile.
     */
    getBuildConfigValues(configValue: unknown): Array<{ labelKey: string; value: unknown }> {
        const config = (configValue as ProgrammingExerciseBuildConfig | undefined) ?? undefined;
        const entries = [
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.sequentialTestRuns', key: 'sequentialTestRuns' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.buildPlanConfiguration', key: 'buildPlanConfiguration' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.buildScript', key: 'buildScript' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.checkoutSolutionRepository', key: 'checkoutSolutionRepository' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.testCheckoutPath', key: 'testCheckoutPath' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.assignmentCheckoutPath', key: 'assignmentCheckoutPath' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.solutionCheckoutPath', key: 'solutionCheckoutPath' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.timeoutSeconds', key: 'timeoutSeconds' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.dockerFlags', key: 'dockerFlags' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.theiaImage', key: 'theiaImage' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.allowBranching', key: 'allowBranching' },
            { labelKey: 'artemisApp.exercise.metadataSync.fields.buildConfig.branchRegex', key: 'branchRegex' },
        ] as const;

        return entries.map((entry) => ({
            labelKey: entry.labelKey,
            value: config ? config[entry.key as keyof ProgrammingExerciseBuildConfig] : undefined,
        }));
    }

    /**
     * Normalizes category values into a list of exercise categories.
     */
    toCategoryEntries(value: unknown): ExerciseCategory[] {
        if (!value) {
            return [];
        }
        if (Array.isArray(value)) {
            return normalizeCategoryArray(value);
        }
        if (typeof value === 'string') {
            try {
                const parsed = JSON.parse(value);
                if (parsed !== undefined) {
                    return this.toCategoryEntries(parsed);
                }
            } catch {
                // not JSON, try comma-split
            }
            return normalizeCategoryArray(value.split(','));
        }
        const entry = normalizeCategoryEntry(value);
        return entry ? [entry] : [];
    }

    /**
     * Maps competency links to display-friendly entries.
     */
    toCompetencyDisplay(value: unknown): Array<{ title: string; weight: number | undefined }> {
        const links = value as CompetencyExerciseLink[] | undefined;
        if (!links || !Array.isArray(links)) {
            return [];
        }
        return links
            .map((link) => ({
                title: link.competency?.title ?? '',
                weight: link.weight,
            }))
            .filter((entry) => entry.title);
    }

    /**
     * Translates IncludedInOverallScore enum values to user-friendly labels.
     */
    private formatIncludedInOverallScore(value: string): string | undefined {
        switch (value) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.translateService.instant('artemisApp.exercise.yes');
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return this.translateService.instant('artemisApp.exercise.bonus');
            case IncludedInOverallScore.NOT_INCLUDED:
                return this.translateService.instant('artemisApp.exercise.no');
            default:
                return undefined;
        }
    }
}
