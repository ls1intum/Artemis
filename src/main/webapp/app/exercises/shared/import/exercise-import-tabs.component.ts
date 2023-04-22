import { Component, Input } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-import-tabs',
    templateUrl: './exercise-import-tabs.component.html',
})
export class ExerciseImportTabsComponent {
    activeTab = 1;
    @Input() exerciseType: ExerciseType;
}
