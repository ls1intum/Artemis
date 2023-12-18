import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
@Component({
    selector: 'jhi-course-description',
    templateUrl: './course-description.component.html',
})
export class CourseDescriptionComponent implements OnInit {
    @Output()
    formSubmitted: EventEmitter<string> = new EventEmitter<string>();
    form: FormGroup;

    constructor(private formBuilder: FormBuilder) {}

    ngOnInit(): void {
        this.form = this.formBuilder.group({
            courseDescription: [undefined as string | undefined, [Validators.required, Validators.minLength(100), Validators.maxLength(10000)]],
        });
    }

    submitForm() {
        this.formSubmitted.emit(this.form.value.courseDescription);
    }
}
