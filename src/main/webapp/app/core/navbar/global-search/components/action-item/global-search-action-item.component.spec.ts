import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GlobalSearchActionItemComponent } from './global-search-action-item.component';

describe('GlobalSearchActionItemComponent', () => {
    setupTestBed({ zoneless: true });

    let component: GlobalSearchActionItemComponent;
    let fixture: ComponentFixture<GlobalSearchActionItemComponent>;

    beforeEach(() => {
        vi.clearAllMocks();

        TestBed.configureTestingModule({
            imports: [GlobalSearchActionItemComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        fixture = TestBed.createComponent(GlobalSearchActionItemComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('title', 'Ask Iris');
        fixture.componentRef.setInput('description', 'Get an AI-powered answer');
        fixture.componentRef.setInput('accentColor', '#6a0dad');
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('template', () => {
        it('should display the title', () => {
            const title = fixture.nativeElement.querySelector('.action-title');
            expect(title.textContent.trim()).toBe('Ask Iris');
        });

        it('should display the description', () => {
            const description = fixture.nativeElement.querySelector('.action-description');
            expect(description.textContent.trim()).toBe('Get an AI-powered answer');
        });

        it('should not apply is-selected class when selected is false', () => {
            fixture.componentRef.setInput('selected', false);
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('button');
            expect(button.classList.contains('is-selected')).toBe(false);
        });

        it('should apply is-selected class when selected is true', () => {
            fixture.componentRef.setInput('selected', true);
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('button');
            expect(button.classList.contains('is-selected')).toBe(true);
        });

        it('should apply accent color as CSS custom property', () => {
            const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
            expect(button.style.getPropertyValue('--accent')).toBe('#6a0dad');
        });

        it('should apply secondary accent color when provided', () => {
            fixture.componentRef.setInput('secondaryAccentColor', '#9b59b6');
            fixture.detectChanges();

            const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
            expect(button.style.getPropertyValue('--accent-secondary')).toBe('#9b59b6');
        });
    });

    describe('interactions', () => {
        it('should emit clicked when the button is clicked', () => {
            const spy = vi.fn();
            component.clicked.subscribe(spy);

            const button = fixture.nativeElement.querySelector('button');
            button.click();

            expect(spy).toHaveBeenCalledOnce();
        });

        it('should not emit clicked when the button is not clicked', () => {
            const spy = vi.fn();
            component.clicked.subscribe(spy);

            expect(spy).not.toHaveBeenCalled();
        });
    });
});
