import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective } from 'ng-mocks';
import { CourseFaqAccordionComponent } from 'app/communication/course-faq/course-faq-accordion-component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';

describe('CourseFaqAccordionComponent', () => {
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
        courseFaqAccordionComponentFixture.destroy();
    });

    it('should initialize', () => {
        courseFaqAccordionComponentFixture.detectChanges();
        expect(courseFaqAccordionComponent).not.toBeNull();
    });

    it('should show the title and keep the categories to the right of it', () => {
        courseFaqAccordionComponentFixture.componentRef.setInput('faq', {
            id: 1,
            questionTitle: 'Title?',
            questionAnswer: 'Answer',
            categories: [{ category: 'a', color: '#ffffff' }],
        });
        courseFaqAccordionComponentFixture.detectChanges();
        const element: HTMLElement = courseFaqAccordionComponentFixture.nativeElement;
        expect(element.querySelector('h5')?.textContent).toBe('Title?');
        expect(element.querySelectorAll('jhi-custom-exercise-category-badge')).toHaveLength(1);
    });
});
