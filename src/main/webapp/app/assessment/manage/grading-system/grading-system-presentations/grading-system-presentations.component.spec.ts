import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import {
    GradingSystemPresentationsComponent,
    PresentationType,
    PresentationsConfig,
} from 'app/assessment/manage/grading-system/grading-system-presentations/grading-system-presentations.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';

describe('GradingSystemPresentationsComponent', () => {
    let component: GradingSystemPresentationsComponent;
    let fixture: ComponentFixture<GradingSystemPresentationsComponent>;

    // =========================================================================
    // Test Data Factory Functions
    // =========================================================================

    /**
     * Creates a course with no presentations configured.
     */
    const createCourseWithoutPresentations = (): Course =>
        ({
            id: 1,
            presentationScore: 0,
        }) as Course;

    /**
     * Creates a course with basic presentations enabled (presentationScore > 0).
     */
    const createCourseWithBasicPresentations = (): Course =>
        ({
            id: 2,
            presentationScore: 2,
        }) as Course;

    /**
     * Creates a course for graded presentations (presentationScore = 0).
     */
    const createCourseForGradedPresentations = (): Course =>
        ({
            id: 3,
            presentationScore: 0,
        }) as Course;

    /**
     * Creates a grading scale with no presentations configured.
     */
    const createGradingScaleWithoutPresentations = (): GradingScale =>
        ({
            id: 1,
            course: createCourseWithoutPresentations(),
        }) as GradingScale;

    /**
     * Creates a grading scale with basic presentations enabled.
     */
    const createGradingScaleWithBasicPresentations = (): GradingScale =>
        ({
            id: 2,
            course: createCourseWithBasicPresentations(),
        }) as GradingScale;

    /**
     * Creates a grading scale with graded presentations configured.
     */
    const createGradingScaleWithGradedPresentations = (): GradingScale =>
        ({
            id: 3,
            course: createCourseForGradedPresentations(),
            presentationsNumber: 3,
            presentationsWeight: 30,
        }) as GradingScale;

    /**
     * Creates a default presentations config with NONE type.
     */
    const createDefaultPresentationsConfig = (): PresentationsConfig => ({
        presentationType: PresentationType.NONE,
    });

    // =========================================================================
    // Test Setup
    // =========================================================================

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService)],
        })
            .overrideComponent(GradingSystemPresentationsComponent, {
                remove: { imports: [TranslateDirective] },
                add: { imports: [MockDirective(TranslateDirective)] },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(GradingSystemPresentationsComponent);
                component = fixture.componentInstance;
            });
    });

    // =========================================================================
    // Component Creation
    // =========================================================================

    describe('component creation', () => {
        it('should create the component', () => {
            const gradingScale = createGradingScaleWithoutPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component).toBeTruthy();
        });

        it('should expose presentation type constants for template use', () => {
            expect(component.NONE).toBe(PresentationType.NONE);
            expect(component.BASIC).toBe(PresentationType.BASIC);
            expect(component.GRADED).toBe(PresentationType.GRADED);
        });

        it('should have mode picker options defined', () => {
            expect(component.modePickerOptions).toBeDefined();
            expect(component.modePickerOptions).toHaveLength(3);
        });
    });

    // =========================================================================
    // Initialization - No Presentations
    // =========================================================================

    describe('initialization with no presentations', () => {
        it('should set presentation type to NONE', () => {
            const gradingScale = createGradingScaleWithoutPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationType).toBe(PresentationType.NONE);
        });

        it('should leave presentationsNumber undefined', () => {
            const gradingScale = createGradingScaleWithoutPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationsNumber).toBeUndefined();
        });

        it('should leave presentationsWeight undefined', () => {
            const gradingScale = createGradingScaleWithoutPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationsWeight).toBeUndefined();
        });
    });

    // =========================================================================
    // Initialization - Basic Presentations
    // =========================================================================

    describe('initialization with basic presentations', () => {
        it('should set presentation type to BASIC', () => {
            const gradingScale = createGradingScaleWithBasicPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationType).toBe(PresentationType.BASIC);
        });

        it('should leave graded presentation fields undefined', () => {
            const gradingScale = createGradingScaleWithBasicPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationsNumber).toBeUndefined();
            expect(component.presentationsConfig().presentationsWeight).toBeUndefined();
        });

        it('should correctly detect basic presentation mode', () => {
            const gradingScale = createGradingScaleWithBasicPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.isBasicPresentation()).toBeTrue();
            expect(component.isGradedPresentation()).toBeFalse();
        });
    });

    // =========================================================================
    // Initialization - Graded Presentations
    // =========================================================================

    describe('initialization with graded presentations', () => {
        it('should set presentation type to GRADED', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationType).toBe(PresentationType.GRADED);
        });

        it('should sync presentationsNumber from grading scale', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationsNumber).toBe(gradingScale.presentationsNumber);
        });

        it('should sync presentationsWeight from grading scale', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.presentationsConfig().presentationsWeight).toBe(gradingScale.presentationsWeight);
        });

        it('should correctly detect graded presentation mode', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            expect(component.isGradedPresentation()).toBeTrue();
        });
    });

    // =========================================================================
    // Update Methods
    // =========================================================================

    describe('updatePresentationsNumber', () => {
        it('should update both config and grading scale', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationsNumber(128);

            expect(component.presentationsConfig().presentationsNumber).toBe(128);
            expect(gradingScale.presentationsNumber).toBe(128);
        });

        it('should normalize zero to undefined', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationsNumber(0);

            expect(component.presentationsConfig().presentationsNumber).toBeUndefined();
        });

        it('should normalize negative values to undefined', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationsNumber(-5);

            expect(component.presentationsConfig().presentationsNumber).toBeUndefined();
        });
    });

    describe('updatePresentationsWeight', () => {
        it('should update both config and grading scale', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationsWeight(64);

            expect(component.presentationsConfig().presentationsWeight).toBe(64);
            expect(gradingScale.presentationsWeight).toBe(64);
        });

        it('should allow zero as a valid weight', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationsWeight(0);

            expect(component.presentationsConfig().presentationsWeight).toBe(0);
        });

        it('should normalize negative values to undefined', () => {
            const gradingScale = createGradingScaleWithGradedPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationsWeight(-10);

            expect(component.presentationsConfig().presentationsWeight).toBeUndefined();
        });
    });

    describe('updatePresentationScore', () => {
        it('should update course presentation score for basic presentations', () => {
            const gradingScale = createGradingScaleWithBasicPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationScore(5);

            expect(component.presentationsConfig().presentationScore).toBe(5);
            expect(gradingScale.course!.presentationScore).toBe(5);
        });

        it('should normalize invalid values to undefined', () => {
            const gradingScale = createGradingScaleWithBasicPresentations();
            const config = createDefaultPresentationsConfig();

            fixture.componentRef.setInput('gradingScale', gradingScale);
            fixture.componentRef.setInput('presentationsConfig', config);
            fixture.detectChanges();

            component.updatePresentationScore(0);

            expect(component.presentationsConfig().presentationScore).toBeUndefined();
        });
    });

    // =========================================================================
    // Presentation Type Changes
    // =========================================================================

    describe('onPresentationTypeChange', () => {
        describe('changing to GRADED', () => {
            it('should set default graded presentation values', () => {
                const gradingScale = createGradingScaleWithoutPresentations();
                const config = createDefaultPresentationsConfig();

                fixture.componentRef.setInput('gradingScale', gradingScale);
                fixture.componentRef.setInput('presentationsConfig', config);
                fixture.detectChanges();

                component.onPresentationTypeChange(PresentationType.GRADED);

                expect(component.isGradedPresentation()).toBeTrue();
                expect(component.presentationsConfig().presentationsNumber).toBe(2);
                expect(component.presentationsConfig().presentationsWeight).toBe(20);
            });

            it('should update grading scale with default values', () => {
                const gradingScale = createGradingScaleWithoutPresentations();
                const config = createDefaultPresentationsConfig();

                fixture.componentRef.setInput('gradingScale', gradingScale);
                fixture.componentRef.setInput('presentationsConfig', config);
                fixture.detectChanges();

                component.onPresentationTypeChange(PresentationType.GRADED);

                expect(gradingScale.presentationsNumber).toBe(2);
                expect(gradingScale.presentationsWeight).toBe(20);
            });
        });

        describe('changing to BASIC', () => {
            it('should set default basic presentation score', () => {
                const gradingScale = createGradingScaleWithoutPresentations();
                const config = createDefaultPresentationsConfig();

                fixture.componentRef.setInput('gradingScale', gradingScale);
                fixture.componentRef.setInput('presentationsConfig', config);
                fixture.detectChanges();

                component.onPresentationTypeChange(PresentationType.BASIC);

                expect(component.presentationsConfig().presentationScore).toBe(2);
            });

            it('should clear graded presentation settings', () => {
                const gradingScale = createGradingScaleWithGradedPresentations();
                const config = createDefaultPresentationsConfig();

                fixture.componentRef.setInput('gradingScale', gradingScale);
                fixture.componentRef.setInput('presentationsConfig', config);
                fixture.detectChanges();

                component.onPresentationTypeChange(PresentationType.BASIC);

                expect(component.presentationsConfig().presentationsNumber).toBeUndefined();
                expect(component.presentationsConfig().presentationsWeight).toBeUndefined();
            });
        });

        describe('changing to NONE', () => {
            it('should clear all presentation settings from graded', () => {
                const gradingScale = createGradingScaleWithGradedPresentations();
                const config = createDefaultPresentationsConfig();

                fixture.componentRef.setInput('gradingScale', gradingScale);
                fixture.componentRef.setInput('presentationsConfig', config);
                fixture.detectChanges();

                component.onPresentationTypeChange(PresentationType.NONE);

                expect(component.isGradedPresentation()).toBeFalse();
                expect(component.presentationsConfig().presentationsNumber).toBeUndefined();
                expect(component.presentationsConfig().presentationsWeight).toBeUndefined();
            });

            it('should update grading scale to clear settings', () => {
                const gradingScale = createGradingScaleWithGradedPresentations();
                const config = createDefaultPresentationsConfig();

                fixture.componentRef.setInput('gradingScale', gradingScale);
                fixture.componentRef.setInput('presentationsConfig', config);
                fixture.detectChanges();

                component.onPresentationTypeChange(PresentationType.NONE);

                expect(gradingScale.presentationsNumber).toBeUndefined();
                expect(gradingScale.presentationsWeight).toBeUndefined();
            });
        });
    });
});
