import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CompetencyFormData } from 'app/course/competencies/competency-form/competency-form.component';
import { Lecture } from 'app/entities/lecture.model';

@Component({ selector: 'jhi-competency-form', template: '' })
export class CompetencyFormStubComponent {
    @Input() formData: CompetencyFormData;
    @Input() courseId: number;
    @Input() isEditMode = false;
    @Input() lecturesOfCourseWithLectureUnits: Lecture[] = [];
    @Input() averageStudentScore?: number;
    @Output() formSubmitted: EventEmitter<CompetencyFormData> = new EventEmitter<CompetencyFormData>();
}
