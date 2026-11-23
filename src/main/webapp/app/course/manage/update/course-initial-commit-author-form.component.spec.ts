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

    it.each(['a@b.', 'a@.b', 'a@.', 'a@b', 'a@.example.com', 'a@example.'])('rejects the email %j without an inner dot in the domain', (email) => {
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
