import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ActivatedRoute } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('LectureUnitComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LectureUnitComponent;
    let fixture: ComponentFixture<LectureUnitComponent>;
    let mockActivatedRoute: { snapshot: { queryParams: Record<string, any> } };
    let mockProfileService: { profileInfo: any; isModuleFeatureActive: ReturnType<typeof vi.fn> };

    const lectureUnit: LectureUnit = {
        id: 1,
        name: 'Test Lecture Unit',
        completed: true,
        visibleToStudents: true,
    };

    beforeEach(async () => {
        mockActivatedRoute = {
            snapshot: {
                queryParams: {},
            },
        };

        mockProfileService = {
            profileInfo: { activeModuleFeatures: [] },
            isModuleFeatureActive: vi.fn().mockReturnValue(false),
        };

        await TestBed.configureTestingModule({
            imports: [LectureUnitComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: ActivatedRoute,
                    useValue: mockActivatedRoute,
                },
                {
                    provide: ProfileService,
                    useValue: mockProfileService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', lectureUnit);
        fixture.componentRef.setInput('showViewIsolatedButton', true);
        fixture.componentRef.setInput('isPresentationMode', false);
        fixture.componentRef.setInput('icon', faVideo);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should handle isolated view', async () => {
        const emitSpy = vi.spyOn(component.onShowIsolated, 'emit');
        const handleIsolatedViewSpy = vi.spyOn(component, 'handleIsolatedView');

        fixture.detectChanges();

        const viewIsolatedButton = fixture.debugElement.query(By.css('#view-isolated-button'));
        viewIsolatedButton.nativeElement.click();

        expect(handleIsolatedViewSpy).toHaveBeenCalledTimes(1);
        expect(emitSpy).toHaveBeenCalledTimes(1);
    });

    it('should toggle completion', async () => {
        const toggleCompletionSpy = vi.spyOn(component, 'toggleCompletion');
        const onCompletionEmitSpy = vi.spyOn(component.onCompletion, 'emit');

        fixture.detectChanges();

        const completedCheckbox = fixture.debugElement.query(By.css('#completed-checkbox'));
        completedCheckbox.nativeElement.click();

        expect(toggleCompletionSpy).toHaveBeenCalledTimes(1);
        expect(onCompletionEmitSpy).toHaveBeenCalledTimes(1);
    });

    it('should toggle collapse', async () => {
        const toggleCollapseSpy = vi.spyOn(component, 'toggleCollapse');
        const onCollapseEmitSpy = vi.spyOn(component.onCollapse, 'emit');

        fixture.detectChanges();

        const collapseButton = fixture.debugElement.query(By.css('#lecture-unit-toggle-button'));
        collapseButton.nativeElement.click();

        expect(toggleCollapseSpy).toHaveBeenCalledTimes(1);
        expect(onCollapseEmitSpy).toHaveBeenCalledTimes(1);
    });

    it('should handle original version view', async () => {
        const handleOriginalVersionViewSpy = vi.spyOn(component, 'handleOriginalVersionView');
        const onShowOriginalVersionEmitSpy = vi.spyOn(component.onShowOriginalVersion, 'emit');

        fixture.componentRef.setInput('showOriginalVersionButton', true);
        fixture.detectChanges();

        const event = new MouseEvent('click');
        const button = fixture.debugElement.query(By.css('#view-original-version-button'));

        expect(button).not.toBeNull();

        button.nativeElement.dispatchEvent(event);

        expect(handleOriginalVersionViewSpy).toHaveBeenCalledTimes(1);
        expect(onShowOriginalVersionEmitSpy).toHaveBeenCalledTimes(1);
    });

    describe('Deeplinking scroll behavior', () => {
        beforeEach(() => {
            Element.prototype.scrollIntoView = vi.fn();
        });

        it('should scroll to video player when timestamp parameter is present', async () => {
            mockActivatedRoute.snapshot.queryParams = { timestamp: '30' };
            fixture.componentRef.setInput('initiallyExpanded', true);

            const mockVideoPlayer = document.createElement('div');
            mockVideoPlayer.scrollIntoView = vi.fn();
            const videoScrollSpy = mockVideoPlayer.scrollIntoView as ReturnType<typeof vi.fn>;

            vi.spyOn(fixture.nativeElement, 'querySelector').mockReturnValue(mockVideoPlayer);

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(videoScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
            });
        });

        it('should scroll to PDF viewer when page parameter is present', async () => {
            mockActivatedRoute.snapshot.queryParams = { page: '5' };
            fixture.componentRef.setInput('initiallyExpanded', true);

            const mockPdfViewer = document.createElement('div');
            mockPdfViewer.scrollIntoView = vi.fn();
            const pdfScrollSpy = mockPdfViewer.scrollIntoView as ReturnType<typeof vi.fn>;

            vi.spyOn(fixture.nativeElement, 'querySelector').mockReturnValue(mockPdfViewer);

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(pdfScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
            });
        });

        it('should not scroll to PDF viewer when only timestamp parameter is present', async () => {
            mockActivatedRoute.snapshot.queryParams = { timestamp: '30' };
            fixture.componentRef.setInput('initiallyExpanded', true);

            const mockVideoPlayer = document.createElement('div');
            mockVideoPlayer.scrollIntoView = vi.fn();
            const videoScrollSpy = mockVideoPlayer.scrollIntoView as ReturnType<typeof vi.fn>;

            const mockPdfViewer = document.createElement('div');
            mockPdfViewer.scrollIntoView = vi.fn();
            const pdfScrollSpy = mockPdfViewer.scrollIntoView as ReturnType<typeof vi.fn>;

            vi.spyOn(fixture.nativeElement, 'querySelector').mockImplementation((selector) => {
                if (selector === 'jhi-video-player') return mockVideoPlayer;
                if (selector === 'jhi-pdf-viewer') return mockPdfViewer;
                return null;
            });

            fixture.detectChanges();

            await vi.waitFor(() => {
                // Video player should be scrolled (timestamp takes priority)
                expect(videoScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
                // PDF viewer should not be scrolled (early return after video player)
                expect(pdfScrollSpy).not.toHaveBeenCalled();
            });
        });

        it('should scroll to YouTube player when no video player is present', async () => {
            mockActivatedRoute.snapshot.queryParams = { timestamp: '30' };
            fixture.componentRef.setInput('initiallyExpanded', true);

            const mockYoutubePlayer = document.createElement('div');
            mockYoutubePlayer.scrollIntoView = vi.fn();
            const youtubeScrollSpy = mockYoutubePlayer.scrollIntoView as ReturnType<typeof vi.fn>;

            vi.spyOn(fixture.nativeElement, 'querySelector').mockImplementation((selector) => {
                if (selector === 'jhi-video-player') return null;
                if (selector === 'jhi-youtube-player') return mockYoutubePlayer;
                return null;
            });

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(youtubeScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
            });
        });

        it('should ignore deeplink parameters when manually expanding (toggle)', async () => {
            mockActivatedRoute.snapshot.queryParams = { timestamp: '30', page: '5' };
            fixture.componentRef.setInput('initiallyExpanded', false);

            const mockVideoPlayer = document.createElement('div');
            mockVideoPlayer.scrollIntoView = vi.fn();
            const videoScrollSpy = mockVideoPlayer.scrollIntoView as ReturnType<typeof vi.fn>;

            const mockPdfViewer = document.createElement('div');
            mockPdfViewer.scrollIntoView = vi.fn();
            const pdfScrollSpy = mockPdfViewer.scrollIntoView as ReturnType<typeof vi.fn>;

            const unitCardScrollSpy = vi.spyOn(fixture.nativeElement, 'scrollIntoView');

            vi.spyOn(fixture.nativeElement, 'querySelector').mockImplementation((selector) => {
                if (selector === 'jhi-video-player') return mockVideoPlayer;
                if (selector === 'jhi-pdf-viewer') return mockPdfViewer;
                return null;
            });

            fixture.detectChanges();

            // Manually toggle collapse to expand
            const collapseButton = fixture.debugElement.query(By.css('#lecture-unit-toggle-button'));
            collapseButton.nativeElement.click();

            await vi.waitFor(() => {
                // Deeplink targets should not be scrolled (manual expand ignores deeplinks)
                expect(videoScrollSpy).not.toHaveBeenCalled();
                expect(pdfScrollSpy).not.toHaveBeenCalled();
                // Unit card itself should be scrolled with 'nearest'
                expect(unitCardScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
            });
        });
    });
});
