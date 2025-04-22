import { Component, Input } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgbNav, NgbNavContent, NgbNavItem, NgbNavItemRole, NgbNavLink, NgbNavLinkBase, NgbNavOutlet } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseImportComponent } from '../exercise-import.component';
import { ExerciseImportFromFileComponent } from '../from-file/exercise-import-from-file.component';

@Component({
    selector: 'jhi-exercise-import-tabs',
    templateUrl: './exercise-import-tabs.component.html',
    imports: [
        NgbNav,
        NgbNavItem,
        NgbNavItemRole,
        NgbNavLink,
        NgbNavLinkBase,
        TranslateDirective,
        NgbNavContent,
        ExerciseImportComponent,
        ExerciseImportFromFileComponent,
        NgbNavOutlet,
    ],
})
export class ExerciseImportTabsComponent {
    activeTab = 1;
    @Input() exerciseType: ExerciseType;
}
