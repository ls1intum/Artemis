import { Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { merge, startWith, switchMap } from 'rxjs';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faPlus, faTableCellsLarge, faTrash } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiConfirmDialogComponent,
    TumAetUiConfirmationService,
    TumAetUiEmptyStateComponent,
    TumAetUiInputDirective,
    TumAetUiInputNumberComponent,
    TumAetUiMessageComponent,
    TumAetUiProgressBarComponent,
    TumAetUiSelectComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { MAX_DESCRIPTION_LENGTH, MAX_RUBRIC_CRITERIA, RUBRIC_WEIGHT_SUM, rubricLevelCount, rubricLevelKey } from 'app/presentation/shared/presentation-rubric.util';
import { levelPositionsHighestFirst } from 'app/presentation/shared/presentation.util';
import { RubricCriterionForm, RubricForm, createCriterionForm, createLevelForms, rubricTotalWeight } from 'app/presentation/manage/presentation-rubric-form';
import { PresentationLabelDirective } from 'app/presentation/manage/presentation-label.directive';

@Component({
    selector: 'jhi-presentation-rubric-editor',
    templateUrl: './presentation-rubric-editor.component.html',
    providers: [TumAetUiConfirmationService],
    imports: [
        ReactiveFormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiConfirmDialogComponent,
        TumAetUiEmptyStateComponent,
        TumAetUiInputDirective,
        TumAetUiInputNumberComponent,
        TumAetUiMessageComponent,
        TumAetUiProgressBarComponent,
        TumAetUiSelectComponent,
        PresentationLabelDirective,
    ],
})
export class PresentationRubricEditorComponent {
    private readonly confirmationService = inject(TumAetUiConfirmationService);
    private readonly translateService = inject(TranslateService);
    private readonly translationChanges = toSignal(merge(this.translateService.onLangChange, this.translateService.onTranslationChange));

    /** The rubric being edited; the enclosing dialog owns it to check its validity and read it when saving. */
    readonly form = input.required<RubricForm>();
    /** Whether the presentation has assessed instances; criteria can then no longer be added or removed. */
    readonly locked = input(false);
    /** Whether the errors of the whole rubric are shown, which the enclosing dialog decides after an attempt to save. */
    readonly showErrors = input(false);

    protected readonly faChevronDown = faChevronDown;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faPlus = faPlus;
    protected readonly faTableCellsLarge = faTableCellsLarge;
    protected readonly faTrash = faTrash;
    protected readonly maxCriteria = MAX_RUBRIC_CRITERIA;
    protected readonly maxDescriptionLength = MAX_DESCRIPTION_LENGTH;
    protected readonly weightSum = RUBRIC_WEIGHT_SUM;
    readonly scaleOptions = computed(() => {
        this.translationChanges();
        return Object.values(PresentationRubricScale).map((scale) => ({
            label: this.translateService.instant(`artemisApp.presentationAssessment.rubric.scale.${scale}`),
            value: scale,
        }));
    });

    /** The criteria whose levels are shown, tracked by form so that removing a criterion keeps the others as they were. */
    private readonly expandedCriteria = signal<ReadonlySet<RubricCriterionForm>>(new Set());
    /** Emits on every change of the rubric, including added and removed criteria, so that the computed values below follow the form. */
    private readonly formChange = toSignal(toObservable(this.form).pipe(switchMap((form) => form.events.pipe(startWith(undefined)))));

    protected readonly totalWeight = computed(() => {
        this.formChange();
        return rubricTotalWeight(this.form());
    });

    /** Whether the weights add up to 100 percent, miss it, or there is no criterion to weigh yet. */
    protected readonly totalState = computed<'valid' | 'invalid' | 'empty'>(() => {
        this.formChange();
        if (this.totalWeight() === RUBRIC_WEIGHT_SUM) {
            return 'valid';
        }
        return this.form().length > 0 ? 'invalid' : 'empty';
    });

    /** Each criterion with what the template shows of it besides its controls, in the order of the rubric. */
    protected readonly criteria = computed(() => {
        this.formChange();
        const expanded = this.expandedCriteria();
        return this.form().controls.map((criterion) => {
            const scale = criterion.controls.scale.value;
            const levelCount = rubricLevelCount(scale);
            return {
                criterion,
                expanded: expanded.has(criterion),
                positionsHighestFirst: levelPositionsHighestFirst(criterion.controls.levels.length),
                levelKeys: criterion.controls.levels.controls.map((_, position) => rubricLevelKey(scale, position)),
                scaleHint: { count: levelCount, lowestKey: rubricLevelKey(scale, 0), highestKey: rubricLevelKey(scale, levelCount - 1) },
            };
        });
    });

    protected toggleLevels(criterion: RubricCriterionForm): void {
        this.expandedCriteria.update((expanded) => {
            const next = new Set(expanded);
            if (!next.delete(criterion)) {
                next.add(criterion);
            }
            return next;
        });
    }

    /** Adds a criterion with the weight still missing to 100 percent, if any. */
    protected addCriterion(): void {
        const form = this.form();
        if (this.locked() || form.length >= MAX_RUBRIC_CRITERIA) {
            return;
        }
        const remainingWeight = RUBRIC_WEIGHT_SUM - rubricTotalWeight(form);
        form.push(createCriterionForm({ weight: remainingWeight > 0 ? remainingWeight : undefined }));
        form.markAsDirty();
    }

    protected removeCriterion(index: number): void {
        const form = this.form();
        if (this.locked()) {
            return;
        }
        const criterion = form.at(index);
        form.removeAt(index);
        form.markAsDirty();
        this.expandedCriteria.update((expanded) => {
            const next = new Set(expanded);
            next.delete(criterion);
            return next;
        });
    }

    /** Replaces the levels to match the chosen scale; level descriptions that would be lost are discarded only after a confirmation. */
    protected changeScale(criterion: RubricCriterionForm): void {
        const scale = criterion.controls.scale.value;
        const levels = criterion.controls.levels;
        if (levels.length === rubricLevelCount(scale)) {
            return;
        }
        const replaceLevels = () => criterion.setControl('levels', createLevelForms(scale));
        if (!levels.controls.some((level) => level.controls.description.value.trim())) {
            replaceLevels();
            return;
        }
        const previousScale = Object.values(PresentationRubricScale).find((candidate) => rubricLevelCount(candidate) === levels.length);
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.presentationAssessment.rubric.scaleChange.header'),
            message: this.translateService.instant('artemisApp.presentationAssessment.rubric.scaleChange.message'),
            acceptLabel: this.translateService.instant('artemisApp.presentationAssessment.rubric.scaleChange.confirm'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptSeverity: 'danger',
            accept: replaceLevels,
            reject: () => previousScale && criterion.controls.scale.setValue(previousScale),
        });
    }
}
