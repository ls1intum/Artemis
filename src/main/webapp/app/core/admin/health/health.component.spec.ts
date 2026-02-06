/**
 * Vitest tests for HealthComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { HealthComponent } from 'app/core/admin/health/health.component';
import { HealthService } from 'app/core/admin/health/health.service';
import { Health } from 'app/core/admin/health/health.model';

describe('HealthComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: HealthComponent;
    let fixture: ComponentFixture<HealthComponent>;
    let healthService: HealthService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HealthComponent],
            providers: [provideHttpClient(), provideHttpClientTesting()],
        })
            .overrideTemplate(HealthComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(HealthComponent);
        comp = fixture.componentInstance;
        healthService = TestBed.inject(HealthService);
    });

    it('should return bg-success class for UP status', () => {
        expect(comp.getBadgeClass('UP')).toBe('bg-success');
    });

    it('should return bg-danger class for DOWN status', () => {
        expect(comp.getBadgeClass('DOWN')).toBe('bg-danger');
    });

    it('should call refresh on init', () => {
        const health: Health = { status: 'UP', components: { mail: { status: 'UP', details: { mailDetail: 'mail' } } } };
        vi.spyOn(healthService, 'checkHealth').mockReturnValue(of(health));

        comp.ngOnInit();

        expect(healthService.checkHealth).toHaveBeenCalledOnce();
        expect(comp.health()).toEqual(health);
    });

    it('should handle a 503 on refreshing health data', () => {
        const health: Health = { status: 'DOWN', components: { mail: { status: 'DOWN' } } };
        vi.spyOn(healthService, 'checkHealth').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: health })));

        comp.refresh();

        expect(healthService.checkHealth).toHaveBeenCalledOnce();
        expect(comp.health()).toEqual(health);
    });

    it('should set selectedHealth and show modal when showHealth is called', () => {
        const healthDetails = { key: 'mail', value: { status: 'UP', details: { mailDetail: 'mail' } } };
        comp.showHealth(healthDetails as any);

        expect(comp.selectedHealth()).toEqual(healthDetails);
        expect(comp.showHealthModal()).toBe(true);
    });
});
