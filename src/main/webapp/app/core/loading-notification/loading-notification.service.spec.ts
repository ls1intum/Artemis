import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { LoadingNotificationService } from 'app/core/loading-notification/loading-notification.service';

describe('LoadingNotificationService', () => {
    setupTestBed({ zoneless: true });

    let service: LoadingNotificationService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LoadingNotificationService],
        });
        service = TestBed.inject(LoadingNotificationService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should have a loadingStatus Subject', () => {
        expect(service.loadingStatus).toBeDefined();
    });

    describe('startLoading', () => {
        it('should emit true when startLoading is called', () => {
            const emittedValues: boolean[] = [];
            service.loadingStatus.subscribe((value) => {
                emittedValues.push(value);
            });

            service.startLoading();

            expect(emittedValues).toEqual([true]);
        });

        it('should emit true multiple times when startLoading is called multiple times', () => {
            const emittedValues: boolean[] = [];
            service.loadingStatus.subscribe((value) => {
                emittedValues.push(value);
            });

            service.startLoading();
            service.startLoading();

            expect(emittedValues).toEqual([true, true]);
        });
    });

    describe('stopLoading', () => {
        it('should emit false when stopLoading is called', () => {
            const emittedValues: boolean[] = [];
            service.loadingStatus.subscribe((value) => {
                emittedValues.push(value);
            });

            service.stopLoading();

            expect(emittedValues).toEqual([false]);
        });

        it('should emit false multiple times when stopLoading is called multiple times', () => {
            const emittedValues: boolean[] = [];
            service.loadingStatus.subscribe((value) => {
                emittedValues.push(value);
            });

            service.stopLoading();
            service.stopLoading();

            expect(emittedValues).toEqual([false, false]);
        });
    });

    describe('startLoading and stopLoading combined', () => {
        it('should emit correct sequence when startLoading and stopLoading are called', () => {
            const emittedValues: boolean[] = [];
            service.loadingStatus.subscribe((value) => {
                emittedValues.push(value);
            });

            service.startLoading();
            service.stopLoading();

            expect(emittedValues).toEqual([true, false]);
        });

        it('should emit correct sequence for multiple start/stop cycles', () => {
            const emittedValues: boolean[] = [];
            service.loadingStatus.subscribe((value) => {
                emittedValues.push(value);
            });

            service.startLoading();
            service.stopLoading();
            service.startLoading();
            service.stopLoading();

            expect(emittedValues).toEqual([true, false, true, false]);
        });
    });

    describe('subscription behavior', () => {
        it('should not emit to unsubscribed subscribers', () => {
            const emittedValues: boolean[] = [];
            const subscription = service.loadingStatus.subscribe((value) => {
                emittedValues.push(value);
            });

            service.startLoading();
            subscription.unsubscribe();
            service.stopLoading();

            expect(emittedValues).toEqual([true]);
        });

        it('should support multiple subscribers', () => {
            const emittedValues1: boolean[] = [];
            const emittedValues2: boolean[] = [];

            service.loadingStatus.subscribe((value) => {
                emittedValues1.push(value);
            });
            service.loadingStatus.subscribe((value) => {
                emittedValues2.push(value);
            });

            service.startLoading();

            expect(emittedValues1).toEqual([true]);
            expect(emittedValues2).toEqual([true]);
        });
    });
});
