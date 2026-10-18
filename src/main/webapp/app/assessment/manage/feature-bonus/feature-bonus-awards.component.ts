import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TumAetUiButtonDirective, TumAetUiFormFieldComponent, TumAetUiInputDirective, TumAetUiMessageComponent, TumAetUiTagComponent } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPen } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ConfirmIconComponent } from 'app/shared-ui/confirm-icon/confirm-icon.component';
import { FeatureBonusService } from 'app/assessment/shared/services/feature-bonus.service';
import { FeatureBonusAward, FeatureBonusAwardRequest } from 'app/assessment/shared/entities/feature-bonus.model';

const MAX_TITLE_LENGTH = 255;
const MAX_JUSTIFICATION_LENGTH = 2000;
const MAX_POINTS = 100;

/**
 * The feature bonus section of an assessment: the tutor holding the assessment awards points for features the student or team
 * implemented beyond the task. The points are stored right away and never change the score of the exercise; the course converts
 * them into a bonus on the final grade.
 */
@Component({
    selector: 'jhi-feature-bonus-awards',
    templateUrl: './feature-bonus-awards.component.html',
    styleUrls: ['./feature-bonus-awards.component.scss'],
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        ConfirmIconComponent,
        TumAetUiButtonDirective,
        TumAetUiFormFieldComponent,
        TumAetUiInputDirective,
        TumAetUiMessageComponent,
        TumAetUiTagComponent,
    ],
})
export class FeatureBonusAwardsComponent {
    private readonly featureBonusService = inject(FeatureBonusService);

    protected readonly faPen = faPen;
    protected readonly MAX_TITLE_LENGTH = MAX_TITLE_LENGTH;
    protected readonly MAX_JUSTIFICATION_LENGTH = MAX_JUSTIFICATION_LENGTH;
    protected readonly MAX_POINTS = MAX_POINTS;

    readonly participationId = input.required<number>();
    readonly readOnly = input(false);

    readonly awards = signal<FeatureBonusAward[]>([]);
    readonly busy = signal(false);

    /** The award the form currently edits; undefined while the form adds a new one. */
    readonly editedAwardId = signal<number | undefined>(undefined);
    readonly title = signal('');
    readonly justification = signal('');
    readonly points = signal<number | undefined>(undefined);

    readonly totalPoints = computed(() => this.awards().reduce((sum, award) => sum + award.points, 0));
    readonly formValid = computed(() => {
        const points = this.points();
        const title = this.title().trim();
        return title.length > 0 && title.length <= MAX_TITLE_LENGTH && points !== undefined && points > 0 && points <= MAX_POINTS;
    });

    constructor() {
        effect(() => {
            const participationId = this.participationId();
            untracked(() => this.loadAwards(participationId));
        });
    }

    private loadAwards(participationId: number) {
        this.resetForm();
        this.featureBonusService.getAwards(participationId).subscribe((awards) => this.awards.set(awards));
    }

    /**
     * Stores the award the form holds: a new one, or the changes to the one being edited.
     */
    save() {
        if (!this.formValid() || this.busy()) {
            return;
        }
        const request: FeatureBonusAwardRequest = { title: this.title().trim(), justification: this.justification().trim() || undefined, points: this.points()! };
        const editedAwardId = this.editedAwardId();
        const save$ =
            editedAwardId === undefined
                ? this.featureBonusService.createAward(this.participationId(), request)
                : this.featureBonusService.updateAward(this.participationId(), editedAwardId, request);
        this.busy.set(true);
        save$.subscribe({
            next: (savedAward) => {
                this.awards.update((awards) => (editedAwardId === undefined ? [...awards, savedAward] : awards.map((award) => (award.id === savedAward.id ? savedAward : award))));
                this.resetForm();
                this.busy.set(false);
            },
            error: () => this.busy.set(false),
        });
    }

    edit(award: FeatureBonusAward) {
        this.editedAwardId.set(award.id);
        this.title.set(award.title);
        this.justification.set(award.justification ?? '');
        this.points.set(award.points);
    }

    delete(award: FeatureBonusAward) {
        this.busy.set(true);
        this.featureBonusService.deleteAward(this.participationId(), award.id).subscribe({
            next: () => {
                this.awards.update((awards) => awards.filter((existing) => existing.id !== award.id));
                if (this.editedAwardId() === award.id) {
                    this.resetForm();
                }
                this.busy.set(false);
            },
            error: () => this.busy.set(false),
        });
    }

    resetForm() {
        this.editedAwardId.set(undefined);
        this.title.set('');
        this.justification.set('');
        this.points.set(undefined);
    }
}
