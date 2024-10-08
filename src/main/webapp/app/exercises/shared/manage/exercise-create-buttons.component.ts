import { Component, Input, OnInit, inject } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { faFileImport, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { getIcon } from 'app/entities/exercise.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-exercise-create-buttons',
    templateUrl: './exercise-create-buttons.component.html',
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
