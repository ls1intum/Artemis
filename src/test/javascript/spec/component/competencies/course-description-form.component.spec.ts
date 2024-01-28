import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { CourseDescriptionFormComponent } from 'app/course/competencies/parse-description/course-description-form.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ReactiveFormsModule } from '@angular/forms';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('CourseDescriptionFormComponent', () => {
    let courseDescriptionComponentFixture: ComponentFixture<CourseDescriptionFormComponent>;
    let courseDescriptionComponent: CourseDescriptionFormComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbTooltipMocksModule],
            declarations: [
                CourseDescriptionFormComponent,
                MockPipe(ArtemisTranslatePipe),
                ButtonComponent,
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
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        courseDescriptionComponentFixture.detectChanges();
        expect(courseDescriptionComponent).toBeDefined();
    });

    it('should submit successfully', () => {
        courseDescriptionComponentFixture.detectChanges();
        const description = 'I'.repeat(courseDescriptionComponent['DESCRIPTION_MIN'] + 1);
        const formSubmittedEmitSpy = jest.spyOn(courseDescriptionComponent.formSubmitted, 'emit');
        const generateButton = courseDescriptionComponentFixture.debugElement.nativeElement.querySelector('#generateButton > .jhi-btn');

        courseDescriptionComponent.courseDescriptionControl.setValue(description);
        expect(courseDescriptionComponent.isSubmitPossible).toBeTrue();
        generateButton.click();

        expect(courseDescriptionComponent.hasBeenSubmitted).toBeTrue();
        expect(formSubmittedEmitSpy).toHaveBeenCalledOnce();
    });

    it('should not allow submission for invalid values', () => {
        courseDescriptionComponentFixture.detectChanges();
        const descriptionTooShort = 'I'.repeat(courseDescriptionComponent['DESCRIPTION_MIN'] - 1);
        const descriptionTooLong = 'I'.repeat(courseDescriptionComponent['DESCRIPTION_MAX'] + 1);

        //submit should be disabled at the start due to empty description
        expect(courseDescriptionComponent.isSubmitPossible).toBeFalse();

        courseDescriptionComponent.courseDescriptionControl.setValue(descriptionTooShort);
        expect(courseDescriptionComponent.isSubmitPossible).toBeFalse();

        courseDescriptionComponent.courseDescriptionControl.setValue(descriptionTooLong);
        expect(courseDescriptionComponent.isSubmitPossible).toBeFalse();
    });
});
