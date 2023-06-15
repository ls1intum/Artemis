import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradingScale } from 'app/entities/grading-scale.model';
import { GradingSystemPresentationsComponent, PresentationType } from 'app/grading-system/grading-system-presentations/grading-system-presentations.component';
import { Course } from 'app/entities/course.model';
import { ModePickerComponent } from 'app/exercises/shared/mode-picker/mode-picker.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent } from 'ng-mocks';

describe('Grading System Presentations Component', () => {
    let component: GradingSystemPresentationsComponent;
    let componentFixture: ComponentFixture<GradingSystemPresentationsComponent>;

    const courseWithoutPresentations = {
        id: 2,
        presentationScore: 0,
    } as Course;

    const courseWithBasicPresentations = {
        id: 1,
        presentationScore: 2,
    } as Course;

    const courseWithGradedPresentation = {
        id: 3,
        presentationScore: 0,
    } as Course;

    const gradingScaleWithoutPresentations = {
        id: 1,
        course: courseWithoutPresentations,
    } as GradingScale;

    const gradingScaleWithBasicPresentations = {
        id: 2,
        course: courseWithBasicPresentations,
    } as GradingScale;

    const gradingScaleWithGradedPresentations = {
        id: 3,
        course: courseWithGradedPresentation,
        presentationsNumber: 3,
        presentationsWeight: 30,
    } as GradingScale;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [GradingSystemPresentationsComponent, MockComponent(HelpIconComponent), MockComponent(ModePickerComponent)],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(GradingSystemPresentationsComponent);
                component = componentFixture.componentInstance;
                component.presentationsConfig = { presentationType: PresentationType.NONE };
            });
    });

    it('should init presentations config for no presentations', () => {
        component.gradingScale = gradingScaleWithoutPresentations;

        component.ngOnChanges();

        expect(component.presentationsConfig.presentationsNumber).toBeUndefined();
        expect(component.presentationsConfig.presentationsWeight).toBeUndefined();
        expect(component.presentationsConfig.presentationType).toBe(PresentationType.NONE);
    });

    it('should init presentations config for basic presentations', () => {
        component.gradingScale = gradingScaleWithBasicPresentations;

        component.ngOnChanges();

        expect(component.presentationsConfig.presentationsNumber).toBeUndefined();
        expect(component.presentationsConfig.presentationsWeight).toBeUndefined();
        expect(component.presentationsConfig.presentationType).toBe(PresentationType.BASIC);
    });

    it('should init presentations config for graded presentations', () => {
        component.gradingScale = gradingScaleWithGradedPresentations;

        component.ngOnChanges();

        expect(component.presentationsConfig.presentationsNumber).toBe(gradingScaleWithGradedPresentations.presentationsNumber);
        expect(component.presentationsConfig.presentationsWeight).toBe(gradingScaleWithGradedPresentations.presentationsWeight);
        expect(component.presentationsConfig.presentationType).toBe(PresentationType.GRADED);
    });

    it('should update number of presentations', () => {
        component.gradingScale = gradingScaleWithGradedPresentations;

        component.updatePresentationsNumber(128);

        expect(component.presentationsConfig.presentationsNumber).toBe(128);
        expect(gradingScaleWithGradedPresentations.presentationsNumber).toBe(128);
    });

    it('should update combined weight of presentations', () => {
        component.gradingScale = gradingScaleWithGradedPresentations;

        component.updatePresentationsWeight(64);

        expect(component.presentationsConfig.presentationsWeight).toBe(64);
        expect(gradingScaleWithGradedPresentations.presentationsWeight).toBe(64);
    });

    it('should update presentation type from none to graded', () => {
        component.gradingScale = gradingScaleWithoutPresentations;

        component.onPresentationTypeChange(PresentationType.GRADED);

        expect(component.isGradedPresentation()).toBeTrue();
        expect(component.presentationsConfig.presentationsNumber).toBe(2); // Default value
        expect(gradingScaleWithoutPresentations.presentationsNumber).toBe(2); // Default value
        expect(component.presentationsConfig.presentationsWeight).toBe(20); // Default value
        expect(gradingScaleWithoutPresentations.presentationsWeight).toBe(20); // Default value
    });

    it('should update presentation type from graded to none', () => {
        component.gradingScale = gradingScaleWithGradedPresentations;

        component.onPresentationTypeChange(PresentationType.NONE);

        expect(component.isGradedPresentation()).toBeFalse();
        expect(component.presentationsConfig.presentationsNumber).toBeUndefined();
        expect(gradingScaleWithGradedPresentations.presentationsNumber).toBeUndefined();
        expect(component.presentationsConfig.presentationsWeight).toBeUndefined();
        expect(gradingScaleWithGradedPresentations.presentationsWeight).toBeUndefined();
    });
});
