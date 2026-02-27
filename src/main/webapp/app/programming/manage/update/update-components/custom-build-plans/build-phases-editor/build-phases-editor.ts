import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { BuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { BuildPhaseEditor } from 'app/programming/manage/update/update-components/custom-build-plans/build-phases-editor/build-phase/build-phase-editor';

@Component({
    selector: 'jhi-build-phases-editor',
    imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, SelectModule, TextareaModule, FaIconComponent, BuildPhaseEditor],
    templateUrl: './build-phases-editor.html',
    styleUrl: './build-phases-editor.scss',
})
export class BuildPhasesEditor {
    protected readonly faPlus = faPlus;

    addPhase(): void {}

    deletePhase(id: number): void {}

    toggleExpanded(id: number): void {}

    moveUp(index: number): void {}

    moveDown(index: number): void {}

    getBuildPlanPhases(): BuildPlanPhases {
        return {} as BuildPlanPhases;
    }
}
