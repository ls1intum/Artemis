import { Component, EventEmitter, Output } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { DifficultyLevel, ExerciseType } from 'app/entities/exercise.model';
import { DifficultyFilterOptions, ExerciseTypeFilterOptions } from 'app/shared/sidebar/sidebar.component';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

@Component({
    selector: 'jhi-exercise-filter-modal',
    templateUrl: './exercise-filter-modal.component.html',
    // styleUrls: ['./exercise-filter.component.scss'],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule],
})
export class ExerciseFilterModalComponent {
    readonly faFilter = faFilter;

    @Output() filterApplied = new EventEmitter<SidebarData>();

    // TODO x has a light blue border when opening the modal

    form: FormGroup;

    model: any;

    sidebarData?: SidebarData;

    typeFilters?: ExerciseTypeFilterOptions;
    difficultyFilters?: DifficultyFilterOptions;

    possibleCategories: ExerciseCategory[] = [];

    constructor(private activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        // TODO filter exercise types to exercise types that are used
        // TODO filter difficulties to difficulties that have been used
        const existingDifficulties = this.sidebarData?.ungroupedData
            ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.difficulty !== undefined)
            .map((sidebarElement: SidebarCardElement) => sidebarElement.difficulty);
        this.difficultyFilters?.filter((difficulty) => existingDifficulties?.includes(difficulty.value));
        // TODO do not display difficulties selection if not enough selection options

        this.possibleCategories =
            this.sidebarData?.ungroupedData
                ?.filter((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories !== undefined)
                .flatMap((sidebarElement: SidebarCardElement) => sidebarElement.exercise?.categories || []) ?? [];
    }

    closeModal(): void {
        this.activeModal.close();
    }

    applyFilter(): void {
        this.applyTypeFilter();
        this.applyDifficultyFilter();

        this.filterApplied.emit(this.sidebarData);
        this.closeModal();
    }

    private applyTypeFilter() {
        if (!this.typeFilters) {
            return;
        }
        const searchedTypes: ExerciseType[] = this.typeFilters?.filter((type) => type.checked).map((type) => type.value);
        if (searchedTypes.length === 0) {
            return;
        }

        if (this.sidebarData?.groupedData) {
            for (const groupedDataKey in this.sidebarData.groupedData) {
                this.sidebarData.groupedData[groupedDataKey].entityData = this.sidebarData.groupedData[groupedDataKey].entityData.filter(
                    (sidebarElement) => sidebarElement?.exercise?.type && searchedTypes.includes(sidebarElement.exercise.type),
                );
            }
        }
    }

    private applyDifficultyFilter() {
        if (!this.difficultyFilters) {
            return;
        }
        const searchedDifficulties: DifficultyLevel[] = this.difficultyFilters?.filter((difficulty) => difficulty.checked).map((difficulty) => difficulty.value);
        if (searchedDifficulties.length === 0) {
            return;
        }

        if (this.sidebarData?.groupedData) {
            for (const groupedDataKey in this.sidebarData.groupedData) {
                this.sidebarData.groupedData[groupedDataKey].entityData = this.sidebarData.groupedData[groupedDataKey].entityData.filter(
                    (sidebarElement) => sidebarElement.difficulty && searchedDifficulties.includes(sidebarElement.difficulty),
                );
            }
        }

        // TODO do we need to filter the ungrouped data aswell?
        if (this.sidebarData?.ungroupedData) {
            this.sidebarData.ungroupedData = this.sidebarData?.ungroupedData.filter(
                (sidebarElement: SidebarCardElement) => sidebarElement.difficulty && searchedDifficulties.includes(sidebarElement.difficulty),
            );
        }
    }
}
