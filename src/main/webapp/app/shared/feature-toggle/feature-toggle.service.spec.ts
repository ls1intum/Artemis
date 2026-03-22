import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { ActiveFeatureToggles, FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('FeatureToggleService', () => {
    setupTestBed({ zoneless: true });

    let service: FeatureToggleService;
    let websocketService: MockWebsocketService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [FeatureToggleService, { provide: WebsocketService, useClass: MockWebsocketService }, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(FeatureToggleService);
        websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('subscribeFeatureToggleUpdates', () => {
        it('should subscribe to websocket topic on first call', () => {
            const subscribeSpy = vi.spyOn(websocketService, 'subscribe');
            service.subscribeFeatureToggleUpdates();
            expect(subscribeSpy).toHaveBeenCalledOnce();
        });

        it('should not subscribe again if already subscribed', () => {
            const subscribeSpy = vi.spyOn(websocketService, 'subscribe');
            service.subscribeFeatureToggleUpdates();
            service.subscribeFeatureToggleUpdates();
            expect(subscribeSpy).toHaveBeenCalledOnce();
        });
    });

    describe('unsubscribeFeatureToggleUpdates', () => {
        it('should unsubscribe when subscribed', () => {
            service.subscribeFeatureToggleUpdates();
            // second call should be a no-op (subscription already torn down)
            service.unsubscribeFeatureToggleUpdates();
            // subscribing again should work (subscription was torn down)
            const subscribeSpy = vi.spyOn(websocketService, 'subscribe');
            service.subscribeFeatureToggleUpdates();
            expect(subscribeSpy).toHaveBeenCalledOnce();
        });

        it('should be a no-op when not subscribed', () => {
            // should not throw
            expect(() => service.unsubscribeFeatureToggleUpdates()).not.toThrow();
        });
    });

    describe('initializeFeatureToggles', () => {
        it('should push active features to subscribers', async () => {
            const activeFeatures: ActiveFeatureToggles = [FeatureToggle.ProgrammingExercises, FeatureToggle.AutonomousTutor];
            service.initializeFeatureToggles(activeFeatures);
            const result = await firstValueFrom(service.getFeatureToggles());
            expect(result).toEqual(activeFeatures);
        });
    });

    describe('getFeatureToggles', () => {
        it('should return all feature toggles by default', async () => {
            const result = await firstValueFrom(service.getFeatureToggles());
            expect(result).toEqual(Object.values(FeatureToggle));
        });
    });

    describe('getFeatureToggleActive', () => {
        it('should return true for an active feature', async () => {
            service.initializeFeatureToggles(Object.values(FeatureToggle));
            const result = await firstValueFrom(service.getFeatureToggleActive(FeatureToggle.ProgrammingExercises));
            expect(result).toBe(true);
        });

        it('should return false for an inactive feature', async () => {
            service.initializeFeatureToggles([]);
            const result = await firstValueFrom(service.getFeatureToggleActive(FeatureToggle.ProgrammingExercises));
            expect(result).toBe(false);
        });
    });

    describe('getFeatureTogglesActive', () => {
        it('should return true when all specified features are active', async () => {
            service.initializeFeatureToggles(Object.values(FeatureToggle));
            const result = await firstValueFrom(service.getFeatureTogglesActive([FeatureToggle.ProgrammingExercises, FeatureToggle.AutonomousTutor]));
            expect(result).toBe(true);
        });

        it('should return false when any specified feature is inactive', async () => {
            service.initializeFeatureToggles([FeatureToggle.ProgrammingExercises]);
            const result = await firstValueFrom(service.getFeatureTogglesActive([FeatureToggle.ProgrammingExercises, FeatureToggle.AutonomousTutor]));
            expect(result).toBe(false);
        });
    });

    describe('websocket updates', () => {
        it('should update feature toggles when a websocket message is received', async () => {
            service.subscribeFeatureToggleUpdates();

            const updatedFeatures: ActiveFeatureToggles = [FeatureToggle.AutonomousTutor];
            websocketService.emit('/topic/management/feature-toggles', updatedFeatures);

            const result = await firstValueFrom(service.getFeatureToggles());
            expect(result).toEqual(updatedFeatures);
        });
    });

    describe('setFeatureToggleState', () => {
        it('should send a PUT request with the feature toggle state', () => {
            const httpTesting = TestBed.inject(HttpTestingController);

            service.setFeatureToggleState(FeatureToggle.AutonomousTutor, true).subscribe();

            const req = httpTesting.expectOne('/api/core/admin/feature-toggle');
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({ [FeatureToggle.AutonomousTutor]: true });
            req.flush(null);
            httpTesting.verify();
        });

        it('should send a PUT request to disable a feature toggle', () => {
            const httpTesting = TestBed.inject(HttpTestingController);

            service.setFeatureToggleState(FeatureToggle.ProgrammingExercises, false).subscribe();

            const req = httpTesting.expectOne('/api/core/admin/feature-toggle');
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual({ [FeatureToggle.ProgrammingExercises]: false });
            req.flush(null);
            httpTesting.verify();
        });
    });
});
