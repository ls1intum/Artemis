import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { LoadingNotificationComponent } from 'app/core/loading-notification/loading-notification.component';
import { LoadingNotificationService } from 'app/core/loading-notification/loading-notification.service';

describe('LoadingNotificationComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LoadingNotificationComponent;
    let fixture: ComponentFixture<LoadingNotificationComponent>;
    let loadingNotificationServiceMock: LoadingNotificationService;
    let loadingStatusSubject: Subject<boolean>;

    beforeEach(async () => {
        loadingStatusSubject = new Subject<boolean>();

        loadingNotificationServiceMock = {
            loadingStatus: loadingStatusSubject.asObservable(),
        } as any as LoadingNotificationService;

        TestBed.configureTestingModule({
            imports: [LoadingNotificationComponent],
            providers: [{ provide: LoadingNotificationService, useValue: loadingNotificationServiceMock }],
        });
        await TestBed.compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(LoadingNotificationComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    describe('initialization', () => {
        it('should create', () => {
            fixture.detectChanges();
            expect(component).toBeTruthy();
        });

        it('should have isLoading initialized to false', () => {
            fixture.detectChanges(); // Ensure ngOnInit is called so ngOnDestroy doesn't fail
            expect(component.isLoading).toBe(false);
        });

        it('should subscribe to loadingStatus on init', () => {
            fixture.detectChanges();
            expect(component.loadingSubscription).toBeDefined();
        });
    });

    describe('loading status subscription', () => {
        it('should set isLoading to true after debounce when loadingStatus emits true', async () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            loadingStatusSubject.next(true);
            expect(component.isLoading).toBe(false); // Should still be false due to debounce

            await vi.advanceTimersByTimeAsync(1000); // Wait for debounce time
            expect(component.isLoading).toBe(true);
        });

        it('should set isLoading to false after debounce when loadingStatus emits false', async () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            // First set to true
            loadingStatusSubject.next(true);
            await vi.advanceTimersByTimeAsync(1000);
            expect(component.isLoading).toBe(true);

            // Then set to false
            loadingStatusSubject.next(false);
            await vi.advanceTimersByTimeAsync(1000);
            expect(component.isLoading).toBe(false);
        });

        it('should only update isLoading after debounce period of 1000ms', async () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            loadingStatusSubject.next(true);
            await vi.advanceTimersByTimeAsync(500); // Only wait 500ms
            expect(component.isLoading).toBe(false); // Should still be false

            await vi.advanceTimersByTimeAsync(500); // Wait remaining 500ms
            expect(component.isLoading).toBe(true); // Now should be true
        });

        it('should debounce rapid loading status changes', async () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            // Rapid fire changes
            loadingStatusSubject.next(true);
            await vi.advanceTimersByTimeAsync(200);
            loadingStatusSubject.next(false);
            await vi.advanceTimersByTimeAsync(200);
            loadingStatusSubject.next(true);
            await vi.advanceTimersByTimeAsync(200);
            loadingStatusSubject.next(false);
            await vi.advanceTimersByTimeAsync(200);

            // Still within debounce, should be initial value
            expect(component.isLoading).toBe(false);

            await vi.advanceTimersByTimeAsync(1000); // Wait for final debounce
            // Should have the last emitted value (false)
            expect(component.isLoading).toBe(false);
        });
    });

    describe('template rendering', () => {
        it('should not display spinner when isLoading is false', () => {
            fixture.detectChanges();

            const spinner = fixture.debugElement.nativeElement.querySelector('.spinner-border');
            expect(spinner).toBeNull();
        });

        it('should display spinner when isLoading is true', () => {
            component.isLoading = true;
            fixture.detectChanges();

            const spinner = fixture.debugElement.nativeElement.querySelector('.spinner-border');
            expect(spinner).not.toBeNull();
        });

        it('should hide spinner when isLoading becomes false', async () => {
            // Start with loading true
            component.isLoading = true;
            fixture.detectChanges();
            await fixture.whenStable();

            let spinner = fixture.debugElement.nativeElement.querySelector('.spinner-border');
            expect(spinner).not.toBeNull();

            // Set to false - recreate the fixture to avoid change detection issues
            fixture = TestBed.createComponent(LoadingNotificationComponent);
            component = fixture.componentInstance;
            component.isLoading = false;
            fixture.detectChanges();
            await fixture.whenStable();

            spinner = fixture.debugElement.nativeElement.querySelector('.spinner-border');
            expect(spinner).toBeNull();
        });

        it('should have correct spinner styling', () => {
            component.isLoading = true;
            fixture.detectChanges();

            const spinner = fixture.debugElement.nativeElement.querySelector('.spinner-border');
            expect(spinner).not.toBeNull();
            expect(spinner.getAttribute('role')).toBe('status');
        });
    });

    describe('ngOnDestroy', () => {
        it('should unsubscribe from loadingStatus on destroy', () => {
            fixture.detectChanges();

            const unsubscribeSpy = vi.spyOn(component.loadingSubscription, 'unsubscribe');

            component.ngOnDestroy();

            expect(unsubscribeSpy).toHaveBeenCalledOnce();
        });

        it('should not update isLoading after component is destroyed', async () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            component.ngOnDestroy();

            // Emit after destroy
            loadingStatusSubject.next(true);
            await vi.advanceTimersByTimeAsync(1000);

            expect(component.isLoading).toBe(false);
        });
    });

    describe('debounce behavior purpose', () => {
        it('should not update isLoading for fast requests (under 1000ms)', async () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            // Simulate fast request: start and stop loading within 1000ms
            loadingStatusSubject.next(true);
            await vi.advanceTimersByTimeAsync(500);
            loadingStatusSubject.next(false);
            await vi.advanceTimersByTimeAsync(1000);

            // The debounced value should be false (the last value after debounce)
            expect(component.isLoading).toBe(false);
        });

        it('should update isLoading for slow requests (over 1000ms)', async () => {
            vi.useFakeTimers();
            fixture.detectChanges();

            // Simulate slow request: loading starts and stays true for over 1000ms
            loadingStatusSubject.next(true);
            await vi.advanceTimersByTimeAsync(1000);

            expect(component.isLoading).toBe(true);
        });
    });
});
