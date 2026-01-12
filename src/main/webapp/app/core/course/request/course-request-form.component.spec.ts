import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';

import { CourseRequestFormComponent } from 'app/core/course/request/course-request-form.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { regexValidator } from 'app/shared/form/shortname-validator.directive';

describe('CourseRequestFormComponent', () => {
    let component: CourseRequestFormComponent;
    let fixture: ComponentFixture<CourseRequestFormComponent>;
    let form: FormGroup;

    beforeEach(async () => {
        const fb = new FormBuilder();
        form = fb.group({
            title: ['', [Validators.required, Validators.maxLength(255)]],
            shortName: ['', [Validators.required, Validators.minLength(3), regexValidator(SHORT_NAME_PATTERN)]],
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

    it('should generate short name from title and semester', () => {
        form.patchValue({
            title: 'Introduction To Programming',
            semester: 'WS25/26',
        });

        component.generateShortName();

        // "ITP" from title + "2526" from "WS25/26"
        expect(form.get('shortName')?.value).toBe('ITP2526');
    });

    it('should generate short name with minimum length padding', () => {
        form.patchValue({
            title: 'AI',
            semester: '',
        });

        component.generateShortName();

        // 'AI' as a single word contributes only 'A' (first letter of each word)
        // Then padding: 'A' + 'CRS'.substring(0, 3-1) = 'A' + 'CR' = 'ACR'
        expect(form.get('shortName')?.value).toBe('ACR');
    });

    it('should generate short name with all digits from semester', () => {
        form.patchValue({
            title: 'Test Course',
            semester: 'WS25/26',
        });

        component.generateShortName();

        // "TC" from title + "2526" from "WS25/26"
        expect(form.get('shortName')?.value).toBe('TC2526');
    });

    it('should generate short name with summer semester', () => {
        form.patchValue({
            title: 'Data Structures',
            semester: 'SS25',
        });

        component.generateShortName();

        // "DS" from title + "25" from "SS25"
        expect(form.get('shortName')?.value).toBe('DS25');
    });

    it('should generate short name ignoring non-alphanumeric first characters', () => {
        form.patchValue({
            title: '123 Test Course',
            semester: 'WS25/26',
        });

        component.generateShortName();

        // "1TC" from title (1 from "123", T from "Test", C from "Course") + "2526"
        expect(form.get('shortName')?.value).toBe('1TC2526');
    });

    it('should generate short name with empty title', () => {
        form.patchValue({
            title: '',
            semester: 'WS25/26',
        });

        component.generateShortName();

        // No title letters, just semester digits, but needs minimum 3 chars
        // "2526" from semester
        expect(form.get('shortName')?.value).toBe('2526');
    });

    it('should generate short name with empty semester', () => {
        form.patchValue({
            title: 'Programming',
            semester: '',
        });

        component.generateShortName();

        // Just "P" from title, needs padding to 3 chars
        expect(form.get('shortName')?.value).toBe('PCR');
    });

    it('should emit formChange event when generating short name', () => {
        const formChangeSpy = jest.spyOn(component.formChange, 'emit');

        form.patchValue({
            title: 'Test Course',
            semester: 'WS25/26',
        });

        component.generateShortName();

        expect(formChangeSpy).toHaveBeenCalled();
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
