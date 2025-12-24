/**
 * Vitest tests for StatisticsComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideRouter } from '@angular/router';

import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { StatisticsComponent } from 'app/core/admin/statistics/statistics.component';

describe('StatisticsComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<StatisticsComponent>;
    let component: StatisticsComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [StatisticsComponent],
            providers: [provideRouter([]), LocalStorageService, SessionStorageService],
        })
            .overrideTemplate(
                StatisticsComponent,
                `
                <input type="radio" id="option3" (click)="onTabChanged(2)">
            `,
            )
            .compileComponents();

        fixture = TestBed.createComponent(StatisticsComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should call onTabChanged when button is clicked', async () => {
        const tabSpy = vi.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        await fixture.whenStable();
        expect(tabSpy).toHaveBeenCalledOnce();
        expect(tabSpy).toHaveBeenCalledWith(2);
    });
});
