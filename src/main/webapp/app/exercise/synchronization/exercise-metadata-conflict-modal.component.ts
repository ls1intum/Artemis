import { Component, computed, inject, signal } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

export interface ExerciseMetadataConflictItem {
    field: string;
    labelKey: string;
    currentValue: unknown;
    incomingValue: unknown;
}

export interface ExerciseMetadataConflictDecision {
    field: string;
    useIncoming: boolean;
}

export interface ExerciseMetadataConflictModalResult {
    decisions: ExerciseMetadataConflictDecision[];
}

@Component({
    selector: 'jhi-exercise-metadata-conflict-modal',
    templateUrl: './exercise-metadata-conflict-modal.component.html',
    styleUrls: ['./exercise-metadata-conflict-modal.component.scss'],
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class ExerciseMetadataConflictModalComponent {
    activeModal = inject(NgbActiveModal);

    readonly conflicts = signal<ExerciseMetadataConflictItem[]>([]);
    readonly author = signal<UserPublicInfoDTO | undefined>(undefined);
    readonly versionId = signal<number | undefined>(undefined);
    readonly decisions = signal<Record<string, boolean>>({});

    readonly authorName = computed(() => {
        const author = this.author();
        if (!author) {
            return '';
        }
        const fullName = [author.firstName, author.lastName].filter(Boolean).join(' ');
        return author.name ?? (fullName || author.login || '');
    });

    readonly faExclamationTriangle = faExclamationTriangle;

    setConflicts(conflicts: ExerciseMetadataConflictItem[]): void {
        this.conflicts.set(conflicts);
        const nextDecisions: Record<string, boolean> = {};
        for (const conflict of conflicts) {
            nextDecisions[conflict.field] = false;
        }
        this.decisions.set(nextDecisions);
    }

    setAuthor(author: UserPublicInfoDTO): void {
        this.author.set(author);
    }

    setVersionId(versionId: number): void {
        this.versionId.set(versionId);
    }

    updateDecision(field: string, value: boolean): void {
        const updated = Object.assign({}, this.decisions());
        updated[field] = value;
        this.decisions.set(updated);
    }

    applySelections(): void {
        const decisions = this.conflicts().map((conflict) => ({
            field: conflict.field,
            useIncoming: Boolean(this.decisions()[conflict.field]),
        }));
        this.activeModal.close({ decisions } satisfies ExerciseMetadataConflictModalResult);
    }

    keepLocalChanges(): void {
        const decisions = this.conflicts().map((conflict) => ({
            field: conflict.field,
            useIncoming: false,
        }));
        this.activeModal.close({ decisions } satisfies ExerciseMetadataConflictModalResult);
    }

    close(): void {
        this.activeModal.dismiss();
    }

    formatValue(value: unknown): string {
        if (value === undefined || value === null) {
            return '—';
        }
        if (dayjs.isDayjs(value)) {
            return value.format('YYYY-MM-DD HH:mm');
        }
        if (typeof value === 'string') {
            return value;
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
}
