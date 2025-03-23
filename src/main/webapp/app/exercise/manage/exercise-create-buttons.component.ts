import { Component, Input, OnInit, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { faFileImport, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { getIcon } from 'app/exercise/entities/exercise.model';
import { Exercise, ExerciseType } from 'app/exercise/entities/exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-exercise-create-buttons',
    templateUrl: './exercise-create-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective],
})
export class ExerciseCreateButtonsComponent implements OnInit {
    private router = inject(Router);
    private modalService = inject(NgbModal);

    @Input() course: Course;
    @Input() exerciseType: ExerciseType;

    translationLabel: string;

    faPlus = faPlus;
    faFileImport = faFileImport;

    getExerciseTypeIcon = getIcon;

    ngOnInit(): void {
        if (this.exerciseType === ExerciseType.FILE_UPLOAD) {
            this.translationLabel = 'fileUpload';
        } else {
            this.translationLabel = this.exerciseType;
        }
    }

    openImportModal() {
        const modalRef = this.modalService.open(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exerciseType = this.exerciseType;
        modalRef.result.then((result: Exercise) => {
            this.router.navigate(['course-management', this.course.id, this.exerciseType + '-exercises', result.id, 'import']);
        });
    }
}
