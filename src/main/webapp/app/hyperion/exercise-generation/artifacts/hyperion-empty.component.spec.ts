import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { HyperionEmptyComponent } from './hyperion-empty.component';

@Component({
    template: `<jhi-hyperion-empty titleKey="empty.title" descriptionKey="empty.description"><button type="button">Retry</button></jhi-hyperion-empty>`,
    imports: [HyperionEmptyComponent],
})
class EmptyHostComponent {}

describe('HyperionEmptyComponent', () => {
    it('uses the shared empty state and preserves translated content and native recovery actions', () => {
        TestBed.configureTestingModule({
            imports: [EmptyHostComponent],
            providers: [{ provide: TranslateService, useValue: { onLangChange: of(), instant: vi.fn((key: string) => `translated:${key}`) } }],
        });
        const fixture = TestBed.createComponent(EmptyHostComponent);
        fixture.detectChanges();
        const emptyState = fixture.nativeElement.querySelector('tumaet-ui-empty-state');
        expect(emptyState.querySelector('h2').textContent).toBe('translated:empty.title');
        expect(emptyState.querySelector('p').textContent).toBe('translated:empty.description');
        expect(emptyState.querySelector('button').textContent).toBe('Retry');
        expect(emptyState.querySelector('[aria-hidden="true"]')).toBeNull();
    });
});
