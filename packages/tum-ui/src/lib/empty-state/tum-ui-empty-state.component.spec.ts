import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { faList } from '@fortawesome/free-solid-svg-icons';
import { By } from '@angular/platform-browser';

import { TumUiEmptyStateComponent } from './tum-ui-empty-state.component';

describe('TumUiEmptyStateComponent', () => {
    let fixture: ComponentFixture<TumUiEmptyStateComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiEmptyStateComponent, FontAwesomeTestingModule],
        }).compileComponents();

        fixture = TestBed.createComponent(TumUiEmptyStateComponent);
        fixture.componentRef.setInput('icon', faList);
        fixture.componentRef.setInput('title', 'No exercises yet');
        fixture.componentRef.setInput('description', 'Exercises will be listed here.');
        fixture.detectChanges();
    });

    it('renders the title and description', () => {
        expect(fixture.nativeElement.querySelector('h2')?.textContent.trim()).toBe('No exercises yet');
        expect(fixture.nativeElement.querySelector('p')?.textContent.trim()).toBe('Exercises will be listed here.');
    });

    it('renders the supplied icon as decorative content', () => {
        const iconContainer = fixture.debugElement.query(By.css('[aria-hidden="true"]'));
        expect(iconContainer).not.toBeNull();
        expect(iconContainer.query(By.css('svg'))).not.toBeNull();
    });

    it('omits the description when it is empty', () => {
        fixture.componentRef.setInput('description', undefined);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('p')).toBeNull();
    });

    it('uses the outlined variant by default and supports the other icon variants', () => {
        expect(fixture.nativeElement.getAttribute('data-variant')).toBe('outlined');

        fixture.componentRef.setInput('variant', 'solid');
        fixture.detectChanges();
        expect(fixture.nativeElement.getAttribute('data-variant')).toBe('solid');

        fixture.componentRef.setInput('variant', 'plain');
        fixture.detectChanges();
        expect(fixture.nativeElement.getAttribute('data-variant')).toBe('plain');
    });

    it('allows actions and documentation to be omitted', () => {
        expect(fixture.nativeElement.querySelector('button')).toBeNull();
        expect(fixture.nativeElement.querySelector('a')).toBeNull();
    });
});

@Component({
    template: `
        <tum-ui-empty-state [icon]="icon" title="No content" description="Nothing has been added yet.">
            <button tumUiEmptyStateActions type="button">Add content</button>
            <button tumUiEmptyStateActions type="button">Import content</button>
            <a tumUiEmptyStateDocumentation href="/documentation">Documentation</a>
        </tum-ui-empty-state>
    `,
    imports: [TumUiEmptyStateComponent, FontAwesomeTestingModule],
})
class EmptyStateHostComponent {
    readonly icon = faList;
}

describe('TumUiEmptyStateComponent (content projection)', () => {
    let fixture: ComponentFixture<EmptyStateHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EmptyStateHostComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(EmptyStateHostComponent);
        fixture.detectChanges();
    });

    it('projects actions and documentation links', () => {
        expect([...fixture.nativeElement.querySelectorAll('button')].map((button) => button.textContent.trim())).toEqual(['Add content', 'Import content']);
        expect(fixture.nativeElement.querySelector('a')?.textContent.trim()).toBe('Documentation');
    });
});

@Component({
    template: `
        @if (showView) {
            <tum-ui-empty-state [icon]="icon" title="No content">
                <ng-container tumUiEmptyStateActions>
                    @if (showActions) {
                        <button type="button">Add content</button>
                        <button type="button">Import content</button>
                    }
                </ng-container>
                <ng-container tumUiEmptyStateDocumentation>
                    @if (showDocumentation) {
                        <a href="/documentation">Documentation</a>
                    }
                </ng-container>
            </tum-ui-empty-state>
        }
    `,
    imports: [TumUiEmptyStateComponent, FontAwesomeTestingModule],
    preserveWhitespaces: true,
})
class ConditionalEmptyStateHostComponent {
    readonly icon = faList;
    readonly showView = true;
    readonly showActions = true;
    readonly showDocumentation = true;
}

describe('TumUiEmptyStateComponent (conditional content projection)', () => {
    let fixture: ComponentFixture<ConditionalEmptyStateHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ConditionalEmptyStateHostComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ConditionalEmptyStateHostComponent);
        fixture.detectChanges();
    });

    it('projects content declared inside conditional blocks', () => {
        expect(fixture.nativeElement.querySelector('button')?.textContent.trim()).toBe('Add content');
        expect(fixture.nativeElement.querySelector('a')?.textContent.trim()).toBe('Documentation');
    });
});
