import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { UserStoryEffortFieldComponent } from 'app/programming/overview/user-story-effort/user-story-effort-field.component';

describe('UserStoryEffortFieldComponent', () => {
    let fixture: ComponentFixture<UserStoryEffortFieldComponent>;
    let component: UserStoryEffortFieldComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(UserStoryEffortFieldComponent);
        component = fixture.componentInstance;
    });

    it('should show the reported value when not editing', () => {
        fixture.componentRef.setInput('value', 2.5);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('2.5');
        expect(fixture.nativeElement.querySelector('.user-story-effort-input')).toBeNull();
    });

    it('should show a placeholder for an unreported value', () => {
        fixture.componentRef.setInput('value', undefined);
        fixture.detectChanges();

        // The orange border that flags this lives on the surrounding information box, not here.
        expect(fixture.nativeElement.querySelector('.user-story-effort-input')).toBeNull();
    });

    it('should show the editor when the header puts it in edit mode', () => {
        fixture.componentRef.setInput('value', 1);
        fixture.componentRef.setInput('editing', true);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('.user-story-effort-input')).not.toBeNull();
        expect(component['draft']()).toBe(1);
    });

    it('should emit the entered value on save', () => {
        let emitted: number | undefined | 'nothing' = 'nothing';
        fixture.componentRef.setInput('value', undefined);
        fixture.componentRef.setInput('editing', true);
        fixture.detectChanges();
        component.valueChange.subscribe((value) => (emitted = value));

        component['draft'].set(1.5);
        component['save']();

        expect(emitted).toBe(1.5);
    });

    it('should restore the stored value and report the cancellation', () => {
        let cancelled = false;
        fixture.componentRef.setInput('value', 4);
        fixture.componentRef.setInput('editing', true);
        fixture.detectChanges();
        component.cancelled.subscribe(() => (cancelled = true));

        component['draft'].set(99);
        component['cancel']();

        expect(component['draft']()).toBe(4);
        expect(cancelled).toBe(true);
    });
});
