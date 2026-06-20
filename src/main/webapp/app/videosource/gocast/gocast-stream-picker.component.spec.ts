import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { GocastStreamPickerComponent } from './gocast-stream-picker.component';
import { GocastService } from './gocast.service';
import { GocastBinding, GocastStream } from './gocast.model';

describe('GocastStreamPickerComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<GocastStreamPickerComponent>;
    let component: GocastStreamPickerComponent;
    let gocastService: GocastService;
    let alertService: AlertService;

    const mockStreams: GocastStream[] = [
        { streamId: 100, name: 'Lecture 1', private: false, start: '2026-10-01T10:00:00Z', end: '2026-10-01T12:00:00Z' },
        { streamId: 101, name: 'Lecture 2 (private)', private: true },
    ];

    const activeBinding: GocastBinding = { courseId: 10, gocastCourseId: 1, gocastCourseSlug: 'eidi', status: 'ACTIVE' };
    const pendingBinding: GocastBinding = { courseId: 10, gocastCourseId: 1, gocastCourseSlug: 'eidi', status: 'PENDING' };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [GocastStreamPickerComponent, FormsModule, TranslateDirective, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(GocastService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        gocastService = TestBed.inject(GocastService);
        alertService = TestBed.inject(AlertService);
    });

    function createComponent(courseId = 10, hasActiveBinding?: boolean): void {
        fixture = TestBed.createComponent(GocastStreamPickerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
        if (hasActiveBinding !== undefined) {
            fixture.componentRef.setInput('hasActiveBinding', hasActiveBinding);
        }
        fixture.detectChanges();
    }

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('with hasActiveBinding input provided', () => {
        it('should NOT load streams when hasActiveBinding=false', () => {
            vi.spyOn(gocastService, 'getBinding').mockReturnValue(throwError(() => ({ status: 404 })));
            vi.spyOn(gocastService, 'listTumLiveStreams');

            createComponent(10, false);

            expect(gocastService.listTumLiveStreams).not.toHaveBeenCalled();
            expect(component.streams()).toHaveLength(0);
        });

        it('should load streams when hasActiveBinding=true', () => {
            vi.spyOn(gocastService, 'listTumLiveStreams').mockReturnValue(of(mockStreams));
            createComponent(10, true);

            expect(gocastService.listTumLiveStreams).toHaveBeenCalledWith(10);
            expect(component.streams()).toHaveLength(2);
            expect(component.streams()[0].name).toBe('Lecture 1');
        });
    });

    describe('with server-resolved binding status', () => {
        it('should NOT load streams when server returns PENDING binding', () => {
            vi.spyOn(gocastService, 'getBinding').mockReturnValue(of(pendingBinding));
            vi.spyOn(gocastService, 'listTumLiveStreams');

            createComponent(10);

            expect(gocastService.listTumLiveStreams).not.toHaveBeenCalled();
        });

        it('should load streams when server returns ACTIVE binding', () => {
            vi.spyOn(gocastService, 'getBinding').mockReturnValue(of(activeBinding));
            vi.spyOn(gocastService, 'listTumLiveStreams').mockReturnValue(of(mockStreams));

            createComponent(10);

            expect(component.bindingStatus()).toBe('ACTIVE');
            expect(component.streams()).toHaveLength(2);
        });

        it('should set bindingStatus to REVOKED (show hint) when no binding exists (404)', () => {
            vi.spyOn(gocastService, 'getBinding').mockReturnValue(throwError(() => ({ status: 404 })));

            createComponent(10);

            // 404/error is treated as "no binding" — shown via the REVOKED hint rather than invisible
            expect(component.bindingStatus()).toBe('REVOKED');
            expect(component.streams()).toHaveLength(0);
        });

        it('should leave bindingStatus undefined (render nothing) on a 5xx/other transient error', () => {
            vi.spyOn(gocastService, 'getBinding').mockReturnValue(throwError(() => ({ status: 503 })));

            createComponent(10);

            // 5xx: do NOT set REVOKED — that would show a misleading "no binding" hint
            expect(component.bindingStatus()).toBeUndefined();
            expect(component.streams()).toHaveLength(0);
        });
    });

    it('should show the stream dropdown when binding is ACTIVE', () => {
        vi.spyOn(gocastService, 'listTumLiveStreams').mockReturnValue(of(mockStreams));
        createComponent(10, true);

        const dropdown = fixture.nativeElement.querySelector('#gocastStreamSelect');
        expect(dropdown).toBeDefined();
    });

    it('should emit streamSelected when a stream is chosen', () => {
        vi.spyOn(gocastService, 'listTumLiveStreams').mockReturnValue(of(mockStreams));
        createComponent(10, true);

        const emittedValues: { streamId: number; streamName: string; slug?: string }[] = [];
        component.streamSelected.subscribe((v) => emittedValues.push(v));

        // p-select onChange emits the selected option value (the streamId)
        component.onStreamSelected(100);

        expect(component.selectedStreamId()).toBe(100);
        expect(emittedValues).toHaveLength(1);
        expect(emittedValues[0]).toMatchObject({ streamId: 100, streamName: 'Lecture 1' });
    });

    it('should emit the bound course slug (server-resolved) so the correct watch URL can be built', () => {
        vi.spyOn(gocastService, 'getBinding').mockReturnValue(of(activeBinding));
        vi.spyOn(gocastService, 'listTumLiveStreams').mockReturnValue(of(mockStreams));
        // Server-resolved path (no hasActiveBinding input) → slug comes from the binding
        createComponent(10);

        const emittedValues: { streamId: number; streamName: string; slug?: string }[] = [];
        component.streamSelected.subscribe((v) => emittedValues.push(v));

        component.onStreamSelected(100);

        expect(emittedValues).toHaveLength(1);
        expect(emittedValues[0].slug).toBe('eidi');
    });

    it('should show an error alert when loading streams fails', () => {
        vi.spyOn(gocastService, 'listTumLiveStreams').mockReturnValue(throwError(() => new Error('Network error')));
        vi.spyOn(alertService, 'error');

        createComponent(10, true);

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.gocast.streamPicker.error.loadStreams');
        expect(component.streams()).toHaveLength(0);
    });

    it('should emit undefined when the selection is cleared so the parent can reset its cached stream id', () => {
        vi.spyOn(gocastService, 'listTumLiveStreams').mockReturnValue(of(mockStreams));
        createComponent(10, true);

        const emittedValues: unknown[] = [];
        component.streamSelected.subscribe((v) => emittedValues.push(v));

        // First select a stream, then clear it via p-select [showClear] (emits undefined).
        component.onStreamSelected(100);
        component.onStreamSelected(undefined);

        expect(emittedValues).toHaveLength(2);
        expect(emittedValues[1]).toBeUndefined();
        expect(component.selectedStreamId()).toBeUndefined();
    });
});
