/**
 * Vitest tests for StatisticsComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { MockPipe } from 'ng-mocks';

import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { StatisticsComponent } from 'app/admin/statistics/statistics.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

describe('StatisticsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<StatisticsComponent>;
    let component: StatisticsComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StatisticsComponent],
            providers: [provideRouter([]), LocalStorageService, SessionStorageService],
        })
            .overrideComponent(StatisticsComponent, {
                set: {
                    imports: [MockPipe(ArtemisTranslatePipe)],
                    template: `
                        <div data-testid="span-selector">
                            @for (option of spanOptions; track option.value) {
                                <button
                                    data-testid="span-option"
                                    [attr.data-value]="option.value"
                                    (click)="onTabChanged(option.value)"
                                >{{ option.label }}</button>
                            }
                        </div>
                        @for (graph of graphTypes; track graph; let i = $index) {
                            <div [id]="'graph-' + i" class="graph"></div>
                        }
                    `,
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(StatisticsComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with default span WEEK', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.currentSpan()).toBe(SpanType.WEEK);
    });

    it('should call onTabChanged and update currentSpan when a span option button is clicked', async () => {
        const tabSpy = vi.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const buttons = fixture.debugElement.queryAll(By.css('[data-testid="span-option"]'));
        const monthButton = buttons.find((b) => b.nativeElement.getAttribute('data-value') === SpanType.MONTH);
        expect(monthButton).toBeTruthy();
        monthButton!.nativeElement.click();

        await fixture.whenStable();
        expect(tabSpy).toHaveBeenCalledWith(SpanType.MONTH);
        expect(component.currentSpan()).toBe(SpanType.MONTH);
    });
});
