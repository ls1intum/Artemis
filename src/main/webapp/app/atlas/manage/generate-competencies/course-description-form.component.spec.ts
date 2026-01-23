import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { CourseDescriptionFormComponent } from 'app/atlas/manage/generate-competencies/course-description-form.component';
import { ReactiveFormsModule } from '@angular/forms';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisLogoButtonComponent } from 'app/iris/overview/iris-logo-button/iris-logo-button.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('CourseDescriptionFormComponent', () => {
    setupTestBed({ zoneless: true });
    let courseDescriptionComponentFixture: ComponentFixture<CourseDescriptionFormComponent>;
    let courseDescriptionComponent: CourseDescriptionFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule],
            declarations: [
                CourseDescriptionFormComponent,
                MockPipe(ArtemisTranslatePipe),
                IrisLogoButtonComponent,
                MockComponent(IrisLogoComponent),
                MockDirective(FeatureToggleDirective),
                MockDirective(TranslateDirective),
            ],
        })
            .compileComponents()
            .then(() => {
                courseDescriptionComponentFixture = TestBed.createComponent(CourseDescriptionFormComponent);
                courseDescriptionComponent = courseDescriptionComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        courseDescriptionComponentFixture.detectChanges();
        expect(courseDescriptionComponent).toBeDefined();
    });

    it('should submit successfully', () => {
        courseDescriptionComponentFixture.detectChanges();
        const description = 'I'.repeat(courseDescriptionComponent['DESCRIPTION_MIN'] + 1);
        const formSubmittedEmitSpy = vi.spyOn(courseDescriptionComponent.formSubmitted, 'emit');
        const generateButton = courseDescriptionComponentFixture.debugElement.nativeElement.querySelector('#generateButton > .jhi-btn');

        courseDescriptionComponent.courseDescriptionControl.setValue(description);
        expect(courseDescriptionComponent.isSubmitPossible).toBeTruthy();
        generateButton.click();

        expect(courseDescriptionComponent.hasBeenSubmitted).toBeTruthy();
        expect(formSubmittedEmitSpy).toHaveBeenCalledOnce();
    });

    it('should not allow submission for invalid values', () => {
        courseDescriptionComponentFixture.detectChanges();
        const descriptionTooShort = 'I'.repeat(courseDescriptionComponent['DESCRIPTION_MIN'] - 1);
        const descriptionTooLong = 'I'.repeat(courseDescriptionComponent['DESCRIPTION_MAX'] + 1);

        //submit should be disabled at the start due to empty description
        expect(courseDescriptionComponent.isSubmitPossible).toBeFalsy();

        courseDescriptionComponent.courseDescriptionControl.setValue(descriptionTooShort);
        expect(courseDescriptionComponent.isSubmitPossible).toBeFalsy();

        courseDescriptionComponent.courseDescriptionControl.setValue(descriptionTooLong);
        expect(courseDescriptionComponent.isSubmitPossible).toBeFalsy();
    });

    it('should update the description', () => {
        courseDescriptionComponentFixture.detectChanges();

        expect(courseDescriptionComponent.courseDescriptionControl.value).toEqual(courseDescriptionComponent.placeholder());

        const description = 'I'.repeat(courseDescriptionComponent['DESCRIPTION_MIN'] + 1);
        courseDescriptionComponent.setCourseDescription(description);
        expect(courseDescriptionComponent.courseDescriptionControl.value).toEqual(description);
    });
});
