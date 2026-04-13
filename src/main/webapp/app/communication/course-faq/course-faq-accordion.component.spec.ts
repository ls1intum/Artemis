import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseFaqAccordionComponent } from 'app/communication/course-faq/course-faq-accordion-component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';

describe('CourseFaqAccordionComponent', () => {
    setupTestBed({ zoneless: true });

    let courseFaqAccordionComponent: CourseFaqAccordionComponent;
    let courseFaqAccordionComponentFixture: ComponentFixture<CourseFaqAccordionComponent>;

    afterEach(() => {
        vi.restoreAllMocks();
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseFaqAccordionComponent, MockDirective(TranslateDirective), MockComponent(CustomExerciseCategoryBadgeComponent)],
        });

        courseFaqAccordionComponentFixture = TestBed.createComponent(CourseFaqAccordionComponent);
        courseFaqAccordionComponent = courseFaqAccordionComponentFixture.componentInstance;
        courseFaqAccordionComponentFixture.componentRef.setInput('faq', { id: 1, questionTitle: 'Title?', questionAnswer: 'Answer', categories: [] });
    });

    afterEach(() => {
        courseFaqAccordionComponent.ngOnDestroy();
        courseFaqAccordionComponentFixture.destroy();
    });

    it('should initialize', () => {
        courseFaqAccordionComponentFixture.detectChanges();
        expect(courseFaqAccordionComponent).not.toBeNull();
        courseFaqAccordionComponent.ngOnDestroy();
    });
});
