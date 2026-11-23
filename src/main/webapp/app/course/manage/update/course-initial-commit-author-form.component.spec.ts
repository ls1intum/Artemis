import { describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { InitialCommitAuthorMode } from 'app/course/shared/entities/course-configuration.model';
import { CourseInitialCommitAuthorFormComponent, createInitialCommitAuthorFormGroup } from './course-initial-commit-author-form.component';

describe('createInitialCommitAuthorFormGroup', () => {
    it('defaults to the exercise creator, which is what a course did before the setting existed', () => {
        const group = createInitialCommitAuthorFormGroup();

        expect(group.controls.mode.value).toBe(InitialCommitAuthorMode.EXERCISE_CREATOR);
        expect(group.valid).toBe(true);
        expect(group.controls.name.disabled).toBe(true);
        expect(group.controls.email.disabled).toBe(true);
    });

    it('keeps the stored custom identity and requires it', () => {
        const group = createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM, name: 'Course Team', email: 'team@university.example' });

        expect(group.controls.name.enabled).toBe(true);
        expect(group.getRawValue()).toEqual({ mode: InitialCommitAuthorMode.CUSTOM, name: 'Course Team', email: 'team@university.example' });
        expect(group.valid).toBe(true);
    });

    it('enables the identity for the custom mode only, so the other modes save without it', () => {
        const group = createInitialCommitAuthorFormGroup();

        group.controls.mode.setValue(InitialCommitAuthorMode.CUSTOM);
        expect(group.controls.name.enabled).toBe(true);
        expect(group.controls.email.enabled).toBe(true);
        expect(group.valid).toBe(false);

        group.controls.mode.setValue(InitialCommitAuthorMode.PLACEHOLDER);
        expect(group.controls.name.disabled).toBe(true);
        expect(group.valid).toBe(true);
    });

    it.each(['', '   ', 'Team <team>', 'Team>', 'Team\nauthor Forged', 'Te\u0085am', 'Team\u202E', 'Te\u200Bam', 'Team\u2028Two', 'x'.repeat(101)])(
        'rejects the name %j',
        (name) => {
            const group = createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM, name, email: 'team@university.example' });

            expect(group.controls.name.invalid).toBe(true);
            expect(group.controls.email.valid).toBe(true);
        },
    );

    it.each([
        '',
        'team',
        'team@example',
        '@example.org',
        'a@b@example.org',
        'tea m@example.org',
        'tea\u00A0m@example.org',
        'team@exa\u00A0mple.org',
        'te\u0085am@example.org',
        'team@example.org\u202E',
        'team@example.org\ncommitter forged',
        `${'a'.repeat(250)}@example.org`,
    ])('rejects the email %j', (email) => {
        const group = createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM, name: 'Course Team', email });

        expect(group.controls.email.invalid).toBe(true);
        expect(group.controls.name.valid).toBe(true);
    });

    it.each(['a@b.c', 'a@example.com', 'a@b..c', 'first.last+tag@sub.example.org'])('accepts the email %j with an inner dot in the domain', (email) => {
        const group = createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM, name: 'Course Team', email });

        expect(group.controls.email.valid).toBe(true);
    });

    it.each(['a@b.', 'a@.b', 'a@.', 'a@b', 'a@.example.com', 'a@example.', 'a@b.c.'])('rejects the email %j without an inner dot in the domain', (email) => {
        const group = createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM, name: 'Course Team', email });

        expect(group.controls.email.invalid).toBe(true);
    });

    it('accepts names with umlauts and an address with a plus tag', () => {
        const group = createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM, name: 'Jürgen Müller', email: 'j.mueller+course@sub.university.example' });

        expect(group.valid).toBe(true);
    });
});

describe('CourseInitialCommitAuthorFormComponent', () => {
    function render(group = createInitialCommitAuthorFormGroup()) {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        const fixture = TestBed.createComponent(CourseInitialCommitAuthorFormComponent);
        fixture.componentRef.setInput('group', group);
        fixture.detectChanges();
        return { fixture, group, element: fixture.nativeElement as HTMLElement };
    }

    it('offers the three choices and shows the identity fields for the custom one only', async () => {
        const { fixture, group, element } = render();

        expect(element.querySelectorAll('input[type="radio"]')).toHaveLength(3);
        expect(element.querySelector('[data-testid="initial-commit-author-name"]')).toBeNull();

        group.controls.mode.setValue(InitialCommitAuthorMode.CUSTOM);
        element.querySelector<HTMLInputElement>('#initialCommitAuthorCustom')!.click();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(element.querySelector('[data-testid="initial-commit-author-name"]')).not.toBeNull();
        expect(element.querySelector('[data-testid="initial-commit-author-email"]')).not.toBeNull();
    });

    it.each([
        ['Placeholder', InitialCommitAuthorMode.PLACEHOLDER],
        ['Custom', InitialCommitAuthorMode.CUSTOM],
        ['Creator', InitialCommitAuthorMode.EXERCISE_CREATOR],
    ])('selects the mode when the text of the option %s is clicked', async (suffix, mode) => {
        const { fixture, group, element } = render(
            createInitialCommitAuthorFormGroup({
                mode: mode === InitialCommitAuthorMode.EXERCISE_CREATOR ? InitialCommitAuthorMode.PLACEHOLDER : InitialCommitAuthorMode.EXERCISE_CREATOR,
            }),
        );

        const label = element.querySelector<HTMLLabelElement>(`label[for="initialCommitAuthor${suffix}"]`)!;
        label.querySelector<HTMLElement>('span')!.click();
        await fixture.whenStable();

        expect(group.controls.mode.value).toBe(mode);
        expect(element.querySelector<HTMLInputElement>(`#initialCommitAuthor${suffix}`)!.getAttribute('aria-label')).toBeTruthy();
    });

    it('shows the error of a field once it was left and hides it when the value is valid', async () => {
        const { fixture, group, element } = render(createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM }));
        const nameAlert = () => element.querySelectorAll<HTMLElement>('[role="alert"]')[0];

        expect(nameAlert().hidden).toBe(true);

        group.controls.name.markAsTouched();
        await fixture.whenStable();
        expect(nameAlert().hidden).toBe(false);
        expect(nameAlert().textContent).toContain('artemisApp.course.initialCommitAuthor.name.required');

        group.controls.name.setValue('Course Team');
        await fixture.whenStable();
        expect(nameAlert().hidden).toBe(true);
    });

    describe('visual state of the radio buttons', () => {
        /** The radios whose box has the checked classes of the kit: the state a user sees, which the native input does not tell. */
        const visuallyChecked = (element: HTMLElement): string[] =>
            Array.from(element.querySelectorAll<HTMLElement>('tumaet-ui-radio-button'))
                .filter((radio) => radio.querySelector('.tumaet-ui-radio-button-box')!.classList.contains('tumaet:bg-primary'))
                .map((radio) => radio.querySelector('input')!.id);

        it('shows exactly one option as selected after each click on an option or on its text', async () => {
            const { fixture, group, element } = render();
            await fixture.whenStable();
            fixture.detectChanges();
            expect(visuallyChecked(element)).toEqual(['initialCommitAuthorCreator']);

            const steps: [string, InitialCommitAuthorMode, 'input' | 'label'][] = [
                ['initialCommitAuthorPlaceholder', InitialCommitAuthorMode.PLACEHOLDER, 'label'],
                ['initialCommitAuthorCustom', InitialCommitAuthorMode.CUSTOM, 'input'],
                ['initialCommitAuthorCreator', InitialCommitAuthorMode.EXERCISE_CREATOR, 'label'],
                ['initialCommitAuthorCustom', InitialCommitAuthorMode.CUSTOM, 'label'],
            ];
            for (const [id, mode, target] of steps) {
                element.querySelector<HTMLElement>(target === 'input' ? `#${id}` : `label[for="${id}"]`)!.click();
                await fixture.whenStable();
                fixture.detectChanges();

                expect(group.controls.mode.value).toBe(mode);
                expect(visuallyChecked(element)).toEqual([id]);
            }
        });

        it('follows a change of the form that the user did not make', async () => {
            const { fixture, group, element } = render();
            await fixture.whenStable();

            group.controls.mode.setValue(InitialCommitAuthorMode.PLACEHOLDER);
            await fixture.whenStable();
            fixture.detectChanges();
            expect(visuallyChecked(element)).toEqual(['initialCommitAuthorPlaceholder']);

            group.reset({ mode: InitialCommitAuthorMode.CUSTOM, name: '', email: '' });
            await fixture.whenStable();
            fixture.detectChanges();
            expect(visuallyChecked(element)).toEqual(['initialCommitAuthorCustom']);
        });
    });

    it('declares both identity inputs as required natively, so that assistive technology knows', () => {
        const { element } = render(createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM }));

        for (const testId of ['initial-commit-author-name', 'initial-commit-author-email']) {
            const input = element.querySelector<HTMLInputElement>(`[data-testid="${testId}"]`)!;
            expect(input.hasAttribute('required')).toBe(true);
            expect(input.required).toBe(true);
        }
    });

    it('keeps the same errors and validity with the native required attribute', async () => {
        const { fixture, group } = render(createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM }));
        await fixture.whenStable();

        expect(group.controls.name.errors).toEqual({ required: true });
        expect(group.controls.email.errors).toEqual({ required: true });

        group.controls.name.setValue('   ');
        expect(group.controls.name.errors).toEqual({ required: true });

        group.controls.name.setValue('Course Team');
        group.controls.email.setValue('team@example');
        expect(group.controls.name.valid).toBe(true);
        expect(group.controls.email.errors).toEqual({ email: true });

        group.controls.email.setValue('team@example.org');
        expect(group.valid).toBe(true);

        group.controls.mode.setValue(InitialCommitAuthorMode.PLACEHOLDER);
        group.controls.name.setValue('');
        expect(group.valid).toBe(true);
    });

    it('writes the chosen identity into the form', async () => {
        const { fixture, group, element } = render(createInitialCommitAuthorFormGroup({ mode: InitialCommitAuthorMode.CUSTOM }));
        const name = element.querySelector<HTMLInputElement>('[data-testid="initial-commit-author-name"]')!;
        const email = element.querySelector<HTMLInputElement>('[data-testid="initial-commit-author-email"]')!;

        name.value = 'Course Team';
        name.dispatchEvent(new Event('input'));
        email.value = 'team@university.example';
        email.dispatchEvent(new Event('input'));
        await fixture.whenStable();

        expect(group.getRawValue()).toEqual({ mode: InitialCommitAuthorMode.CUSTOM, name: 'Course Team', email: 'team@university.example' });
        expect(group.valid).toBe(true);
    });
});
