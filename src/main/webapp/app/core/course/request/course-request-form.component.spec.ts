import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';

import { CourseRequestFormComponent } from 'app/core/course/request/course-request-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

describe('CourseRequestFormComponent', () => {
    let component: CourseRequestFormComponent;
    let fixture: ComponentFixture<CourseRequestFormComponent>;
    let form: FormGroup;

    beforeEach(async () => {
        const fb = new FormBuilder();
        form = fb.group({
            title: ['', [Validators.required, Validators.maxLength(255)]],
            semester: ['WS25/26', [Validators.required]],
            startDate: [undefined],
            endDate: [undefined],
            testCourse: [false],
            reason: ['', [Validators.required]],
        });

        await TestBed.configureTestingModule({
            imports: [
                CourseRequestFormComponent,
                ReactiveFormsModule,
                TranslateModule.forRoot(),
                MockComponent(FormDateTimePickerComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseRequestFormComponent);
        component = fixture.componentInstance;

        // Set required inputs using component input setters
        fixture.componentRef.setInput('form', form);
        fixture.componentRef.setInput('semesters', ['WS25/26', 'SS26', 'WS26/27']);
        fixture.componentRef.setInput('dateRangeInvalid', false);
        fixture.componentRef.setInput('idPrefix', '');
        fixture.componentRef.setInput('showReasonPlaceholder', true);

        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should use idPrefix for element IDs', () => {
        fixture.componentRef.setInput('idPrefix', 'edit');
        fixture.detectChanges();

        expect(component.idPrefix()).toBe('edit');
    });

    it('should handle dateRangeInvalid input', () => {
        fixture.componentRef.setInput('dateRangeInvalid', true);
        fixture.detectChanges();

        expect(component.dateRangeInvalid()).toBeTrue();
    });

    it('should handle showReasonPlaceholder input', () => {
        fixture.componentRef.setInput('showReasonPlaceholder', false);
        fixture.detectChanges();

        expect(component.showReasonPlaceholder()).toBeFalse();
    });
});
