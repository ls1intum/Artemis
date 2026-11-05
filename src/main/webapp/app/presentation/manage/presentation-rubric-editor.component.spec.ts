import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationRubricEditorComponent } from 'app/presentation/manage/presentation-rubric-editor.component';
import { RubricForm, createRubricForm } from 'app/presentation/manage/presentation-rubric-form';
import { PresentationRubricCriterion, PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { typeIntoInputNumber } from 'src/test/javascript/spec/helpers/input-number-test-helpers';

function criterion(name: string, weight: number, scale = PresentationRubricScale.FIVE_POINT): PresentationRubricCriterion {
    const levelCount = scale === PresentationRubricScale.THREE_POINT ? 3 : 5;
    return { name, weight, scale, levels: Array.from({ length: levelCount }, () => ({})) };
}

describe('PresentationRubricEditorComponent', () => {
    let fixture: ComponentFixture<PresentationRubricEditorComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationRubricEditorComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideArtemisTumAetUiTranslator()],
        }).compileComponents();
        fixture = TestBed.createComponent(PresentationRubricEditorComponent);
    });

    function render(form: RubricForm, options: { locked?: boolean; showErrors?: boolean } = {}): RubricForm {
        fixture.componentRef.setInput('form', form);
        fixture.componentRef.setInput('locked', options.locked ?? false);
        fixture.componentRef.setInput('showErrors', options.showErrors ?? false);
        fixture.detectChanges();
        return form;
    }

    function query(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    /** Clicks a native button, or the button inside a `tumaet-ui-button` that carries the test id. */
    function click(testId: string): void {
        const element = query(testId);
        const button = element instanceof HTMLButtonElement ? element : element?.querySelector('button');
        button!.click();
        fixture.detectChanges();
    }

    function confirmation() {
        return fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined);
    }

    function chooseScale(form: RubricForm, index: number, scale: PresentationRubricScale): void {
        form.at(index).controls.scale.setValue(scale);
        fixture.debugElement.query(By.css(`[data-testid="rubric-criterion-scale-${index}"]`)).triggerEventHandler('selectionChange', scale);
        fixture.detectChanges();
    }

    it('should offer to add the first criterion of an empty rubric, which takes the whole weight', () => {
        const form = render(createRubricForm());
        expect(query('rubric-empty')).not.toBeNull();

        click('rubric-add-first-criterion');

        expect(form.length).toBe(1);
        expect(form.at(0).controls.weight.value).toBe(100);
        expect(query('rubric-empty')).toBeNull();
        expect(query('rubric-criterion-0')).not.toBeNull();
    });

    it.each([
        { weights: [60], expectedWeight: 40 },
        { weights: [60, 40], expectedWeight: undefined },
    ])('should give a new criterion the weight still missing to 100 percent: $weights', ({ weights, expectedWeight }) => {
        const form = render(createRubricForm(weights.map((weight, index) => criterion(`Criterion ${index}`, weight))));

        click('rubric-add-criterion');

        expect(form.length).toBe(weights.length + 1);
        expect(form.at(weights.length).controls.weight.value).toBe(expectedWeight);
        expect(form.at(weights.length).controls.scale.value).toBe(PresentationRubricScale.FIVE_POINT);
        expect(form.dirty).toBe(true);
    });

    it('should stop adding criteria at the maximum and say why', () => {
        render(createRubricForm(Array.from({ length: 20 }, (_, index) => criterion(`Criterion ${index}`, 5))));

        expect((query('rubric-add-criterion') as HTMLButtonElement).disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('[jhiTranslate="artemisApp.presentationAssessment.rubric.maxCriteriaReached"]')).not.toBeNull();
    });

    it('should remove a criterion', () => {
        const form = render(createRubricForm([criterion('Content', 60), criterion('Delivery', 40)]));

        click('rubric-remove-criterion-0');

        expect(form.length).toBe(1);
        expect(form.at(0).controls.name.value).toBe('Delivery');
        expect(form.dirty).toBe(true);
    });

    it('should show the total weight in red until it reaches 100 percent and in green then', () => {
        const total = () => query('rubric-total-weight')!;
        const severity = () => total().querySelector('[data-severity]')!.getAttribute('data-severity');
        render(createRubricForm());
        expect(severity()).toBe('primary');

        render(createRubricForm([criterion('Content', 60)]));
        expect(total().textContent).toContain('60 %');
        expect(severity()).toBe('danger');

        typeIntoInputNumber(fixture.nativeElement.querySelector('input#rubric-criterion-weight-0'), '100');
        fixture.detectChanges();

        expect(total().textContent).toContain('100 %');
        expect(severity()).toBe('success');
    });

    it('should show the errors of the whole rubric only when asked to', () => {
        render(createRubricForm());
        expect(query('rubric-empty-error')).toBeNull();

        render(createRubricForm(), { showErrors: true });
        expect(query('rubric-empty-error')).not.toBeNull();

        render(createRubricForm([criterion('Content', 60)]), { showErrors: true });
        expect(query('rubric-weight-sum-error')).not.toBeNull();
    });

    it('should label the name and description of a criterion', () => {
        render(createRubricForm([criterion('Content', 100)]));

        for (const field of ['name', 'description']) {
            const label = fixture.nativeElement.querySelector(`label[for="rubric-criterion-${field}-0"]`) as HTMLLabelElement;
            expect(fixture.nativeElement.querySelector(`#${label.htmlFor}`)).toBeInstanceOf(HTMLInputElement);
        }
    });

    it('should explain a missing name once the field was touched', () => {
        const form = render(createRubricForm([criterion('Content', 100)]));
        const nameError = () => query('rubric-criterion-0')!.querySelector('[jhiTranslate="artemisApp.presentationAssessment.rubric.validation.nameRequired"]');
        form.at(0).controls.name.setValue('   ');
        fixture.detectChanges();
        expect(nameError()).toBeNull();

        form.at(0).controls.name.markAsTouched();
        fixture.detectChanges();

        expect(nameError()).not.toBeNull();
    });

    it('should explain descriptions that are too long, also while the scale is collapsed', () => {
        const form = render(createRubricForm([criterion('Content', 100)]));
        form.at(0).controls.description.setValue('x'.repeat(1001));
        form.at(0).controls.levels.at(0).controls.description.setValue('x'.repeat(1001));
        fixture.detectChanges();

        expect(query('rubric-criterion-description-error-0')).not.toBeNull();
        expect(query('rubric-levels-error-0')).not.toBeNull();
    });

    it('should show the levels highest first and hide them again', () => {
        render(createRubricForm([criterion('Content', 100)]));
        const levelInputs = () => Array.from(fixture.nativeElement.querySelectorAll('[data-testid^="rubric-level-description-0-"]')) as HTMLElement[];
        expect(levelInputs()).toHaveLength(0);

        click('rubric-toggle-levels-0');

        expect(levelInputs().map((input) => input.dataset['testid'])).toEqual([4, 3, 2, 1, 0].map((position) => `rubric-level-description-0-${position}`));
        expect((query('rubric-toggle-levels-0') as HTMLButtonElement).getAttribute('aria-expanded')).toBe('true');

        click('rubric-toggle-levels-0');

        expect(levelInputs()).toHaveLength(0);
    });

    it('should change the scale at once while no level has a description', () => {
        const form = render(createRubricForm([criterion('Content', 100)]));

        chooseScale(form, 0, PresentationRubricScale.THREE_POINT);

        expect(form.at(0).controls.levels.length).toBe(3);
        expect(confirmation()).toBeUndefined();
    });

    it('should keep the levels when the same scale is chosen again', () => {
        const form = render(createRubricForm([criterion('Content', 100)]));
        form.at(0).controls.levels.at(4).controls.description.setValue('Outstanding');

        chooseScale(form, 0, PresentationRubricScale.FIVE_POINT);

        expect(confirmation()).toBeUndefined();
        expect(form.at(0).controls.levels.at(4).controls.description.value).toBe('Outstanding');
    });

    it('should ask before discarding level descriptions and replace the levels once confirmed', () => {
        const form = render(createRubricForm([criterion('Content', 100)]));
        form.at(0).controls.levels.at(4).controls.description.setValue('Outstanding');

        chooseScale(form, 0, PresentationRubricScale.THREE_POINT);
        expect(form.at(0).controls.levels.length).toBe(5);

        confirmation()!.accept();

        expect(form.at(0).controls.levels.length).toBe(3);
        expect(
            form
                .at(0)
                .controls.levels.getRawValue()
                .map((level) => level.description),
        ).toEqual(['', '', '']);
    });

    it('should keep the scale and the level descriptions when the change is cancelled', () => {
        const form = render(createRubricForm([criterion('Content', 100)]));
        form.at(0).controls.levels.at(4).controls.description.setValue('Outstanding');

        chooseScale(form, 0, PresentationRubricScale.THREE_POINT);
        confirmation()!.reject!();

        expect(form.at(0).controls.scale.value).toBe(PresentationRubricScale.FIVE_POINT);
        expect(form.at(0).controls.levels.at(4).controls.description.value).toBe('Outstanding');
    });

    it('should translate the scales again when the active language changes', () => {
        render(createRubricForm());
        const translateService = TestBed.inject(TranslateService) as unknown as MockTranslateService;
        vi.spyOn(translateService, 'instant').mockImplementation((key) => `de:${key}`);
        translateService.onLangChangeSubject.next({ lang: 'de', translations: {} });

        expect(fixture.componentInstance.scaleOptions().map((option) => option.label)).toEqual([
            'de:artemisApp.presentationAssessment.rubric.scale.THREE_POINT',
            'de:artemisApp.presentationAssessment.rubric.scale.FIVE_POINT',
        ]);
    });

    it('should not let a locked rubric add or remove criteria', () => {
        const form = render(createRubricForm([criterion('Content', 100)], true), { locked: true });

        expect(query('rubric-locked-hint')).not.toBeNull();
        expect(query('rubric-add-criterion')).toBeNull();
        expect(query('rubric-remove-criterion-0')).toBeNull();
        expect(form.at(0).controls.scale.disabled).toBe(true);
    });
});
