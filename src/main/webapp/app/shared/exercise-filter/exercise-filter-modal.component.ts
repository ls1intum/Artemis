import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from 'app/forms/forms.module';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

@Component({
    selector: 'jhi-exercise-filter-modal',
    templateUrl: './exercise-filter-modal.component.html',
    // styleUrls: ['./exercise-filter.component.scss'],
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule, FontAwesomeModule, ArtemisSharedCommonModule, ArtemisSharedComponentModule],
})
export class ExerciseFilterModalComponent {
    readonly faFilter = faFilter;

    form: FormGroup;

    exerciseTypes = [
        { name: 'Programming', value: 'programming' },
        { name: 'Quiz', value: 'quiz' },
        { name: 'Modeling', value: 'modeling' },
        { name: 'Text', value: 'text' },
        { name: 'File Upload', value: 'file-upload' },
    ];

    difficulties = [
        { name: 'Easy', value: 'easy' },
        { name: 'Medium', value: 'medium' },
        { name: 'Hard', value: 'hard' },
    ];

    constructor(private activeModal: NgbActiveModal) {}

    // ngOnInit(): void {
    //     // TODO filter exercise types to exercise types that are used
    //     // TODO filter difficulties to difficulties that have been used
    //     let test = 'test';
    // }

    closeModal(): void {
        this.activeModal.close();
    }

    applyFilter(): void {}
}
