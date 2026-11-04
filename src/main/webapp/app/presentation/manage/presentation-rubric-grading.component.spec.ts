import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { Course } from 'app/course/shared/entities/course.model';
import { PresentationRubricGradingComponent } from 'app/presentation/manage/presentation-rubric-grading.component';
import { RatingsForm, createRatingsForm } from 'app/presentation/manage/presentation-rubric-rating-form';
import { PresentationRubricCriterion, PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

function criterion(id: number, name: string, weight: number, scale: PresentationRubricScale): PresentationRubricCriterion {
    const levelCount = scale === PresentationRubricScale.THREE_POINT ? 3 : 5;
    return {
        id,
        name,
        description: `${name} description`,
        weight,
        scale,
        levels: Array.from({ length: levelCount }, (_, position) => ({ id: id * 10 + position + 1, description: `${name} level ${position + 1}` })),
    };
}

const criteria = [
    criterion(1, 'Content', 50, PresentationRubricScale.FIVE_POINT),
    criterion(2, 'Delivery', 30, PresentationRubricScale.THREE_POINT),
    criterion(3, 'Slides', 20, PresentationRubricScale.FIVE_POINT),
];

describe('PresentationRubricGradingComponent', () => {
    let fixture: ComponentFixture<PresentationRubricGradingComponent>;
    let form: RatingsForm;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationRubricGradingComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumAetUiTranslator()],
        }).compileComponents();
        fixture = TestBed.createComponent(PresentationRubricGradingComponent);
        form = createRatingsForm(criteria);
        render(1);
    });

    function render(accuracyOfScores: number): void {
        const course = new Course();
        course.accuracyOfScores = accuracyOfScores;
        fixture.componentRef.setInput('criteria', criteria);
        fixture.componentRef.setInput('form', form);
        fixture.componentRef.setInput('course', course);
        fixture.detectChanges();
    }

    function query(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    /** Rates a criterion by clicking the radio of the level at the given position, counting from 0 at the lowest level. */
    function rate(criterionIndex: number, position: number): void {
        (query(`rubric-grading-level-${criterionIndex}-${position}`)!.querySelector('input') as HTMLInputElement).click();
        fixture.detectChanges();
    }

    it('should show every criterion with its name, description and weight', () => {
        const delivery = query('rubric-grading-criterion-1')!;

        expect(delivery.textContent).toContain('Delivery');
        expect(delivery.textContent).toContain('Delivery description');
        expect(delivery.textContent).toContain('30 %');
    });

    it('should list the levels highest first with value, key and description', () => {
        const levels = Array.from(query('rubric-grading-criterion-0')!.querySelectorAll('[data-testid^="rubric-grading-level-0-"]')) as HTMLElement[];

        expect(levels.map((level) => level.dataset['testid'])).toEqual([4, 3, 2, 1, 0].map((position) => `rubric-grading-level-0-${position}`));
        expect(levels[0].textContent).toContain('5 –');
        expect(levels[0].textContent).toContain('artemisApp.presentationAssessment.rubric.levelKey.excellent');
        expect(levels[0].textContent).toContain('Content level 5');
    });

    it('should select the clicked level and ask for feedback on it', () => {
        expect(query('rubric-grading-unrated-0')).not.toBeNull();
        expect(query('rubric-grading-comment-0')).toBeNull();

        rate(0, 3);

        expect(form.at(0).controls.levelId.value).toBe(14);
        expect(query('rubric-grading-level-0-3')!.classList).toContain('presentation-rubric-grading-level-selected');
        expect(query('rubric-grading-unrated-0')).toBeNull();
        expect(query('rubric-grading-comment-0')).not.toBeNull();
        expect(form.at(0).dirty).toBe(true);
    });

    it('should label the feedback of a rated criterion', () => {
        rate(0, 3);

        const label = fixture.nativeElement.querySelector('label[for="rubric-grading-comment-0"]') as HTMLLabelElement;
        expect(fixture.nativeElement.querySelector(`#${label.htmlFor}`)).toBe(query('rubric-grading-comment-0'));
    });

    it('should explain a comment that is too long', () => {
        rate(0, 3);
        form.at(0).controls.comment.setValue('a'.repeat(1001));
        fixture.detectChanges();

        expect(query('rubric-grading-criterion-0')!.querySelector('[jhiTranslate="artemisApp.presentationAssessment.remarkTooLong"]')).not.toBeNull();
    });

    it('should show the overall result only once every criterion is rated', () => {
        const overall = () => query('rubric-grading-overall')!.textContent;
        rate(0, 3);
        rate(1, 2);
        expect(overall()).toContain('–');

        rate(2, 1);

        expect(overall()).toContain('72.5 %');
    });

    it('should round the overall result as the course rounds scores', () => {
        render(0);
        rate(0, 3);
        rate(1, 2);
        rate(2, 1);

        expect(query('rubric-grading-overall')!.textContent).toContain('73 %');
    });

    it('should not let a disabled rating be changed', () => {
        form.disable();
        fixture.detectChanges();

        expect((query('rubric-grading-level-0-3')!.querySelector('input') as HTMLInputElement).disabled).toBe(true);
    });
});
