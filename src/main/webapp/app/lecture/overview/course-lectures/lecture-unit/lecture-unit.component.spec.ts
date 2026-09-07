import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { LectureUnit } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockComponent } from 'ng-mocks';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LectureDeepLink } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';

describe('LectureUnitComponent', () => {
    let component: LectureUnitComponent;
    let fixture: ComponentFixture<LectureUnitComponent>;
    let mockProfileService: { profileInfo: any; isModuleFeatureActive: ReturnType<typeof vi.fn> };

    const lectureUnit: LectureUnit = {
        id: 1,
        name: 'Test Lecture Unit',
        completed: true,
        visibleToStudents: true,
    };

    const deepLinkTo = (target: { timestamp?: number; page?: number }): LectureDeepLink => ({ unitId: lectureUnit.id!, timestamp: target.timestamp, page: target.page });

    beforeEach(async () => {
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
                    provide: ProfileService,
                    useValue: mockProfileService,
                },
            ],
        })
            .overrideComponent(LectureUnitComponent, {
                remove: { imports: [CompetencyContributionComponent] },
                add: { imports: [MockComponent(CompetencyContributionComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(LectureUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('lectureUnit', lectureUnit);
        fixture.componentRef.setInput('showViewIsolatedButton', true);
        fixture.componentRef.setInput('isPresentationMode', false);
        fixture.componentRef.setInput('icon', faVideo);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        vi.useRealTimers();
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

        const completedCheckbox = fixture.debugElement.query(By.css('[data-testid="lecture-unit-completion-icon"]'));
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

    it('handleFullscreen emits and stops propagation', () => {
        const emitSpy = vi.spyOn(component.onFullscreen, 'emit');
        const event = { stopPropagation: vi.fn() } as unknown as Event;

        component.handleFullscreen(event);

        expect(event.stopPropagation).toHaveBeenCalled();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    describe('Deeplinking scroll behavior', () => {
        beforeEach(() => {
            Element.prototype.scrollIntoView = vi.fn();
        });

        it('should scroll to video player when the deep link carries a timestamp', async () => {
            fixture.componentRef.setInput('deepLink', deepLinkTo({ timestamp: 30 }));

            const mockVideoPlayer = document.createElement('div');
            mockVideoPlayer.scrollIntoView = vi.fn();
            const videoScrollSpy = mockVideoPlayer.scrollIntoView as ReturnType<typeof vi.fn>;

            vi.spyOn(fixture.nativeElement, 'querySelector').mockReturnValue(mockVideoPlayer);

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(videoScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
            });
        });

        it('should scroll to PDF viewer when the deep link carries a page', async () => {
            fixture.componentRef.setInput('deepLink', deepLinkTo({ page: 5 }));

            const mockPdfViewer = document.createElement('div');
            mockPdfViewer.scrollIntoView = vi.fn();
            const pdfScrollSpy = mockPdfViewer.scrollIntoView as ReturnType<typeof vi.fn>;

            vi.spyOn(fixture.nativeElement, 'querySelector').mockReturnValue(mockPdfViewer);

            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(pdfScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
            });
        });

        it('should not scroll to PDF viewer when the deep link carries only a timestamp', async () => {
            fixture.componentRef.setInput('deepLink', deepLinkTo({ timestamp: 30 }));

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
                expect(videoScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
                expect(pdfScrollSpy).not.toHaveBeenCalled();
            });
        });

        it('should scroll to YouTube player when no video player is present', async () => {
            fixture.componentRef.setInput('deepLink', deepLinkTo({ timestamp: 30 }));

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

        it('should ignore a deep link superseded before its render callback schedules the scroll timeout', async () => {
            vi.useFakeTimers();

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

            fixture.componentRef.setInput('deepLink', deepLinkTo({ timestamp: 30 }));
            fixture.detectChanges();
            fixture.componentRef.setInput('deepLink', deepLinkTo({ page: 5 }));
            fixture.detectChanges();

            await fixture.whenStable();
            await vi.advanceTimersByTimeAsync(500);

            expect(videoScrollSpy).not.toHaveBeenCalled();
            expect(pdfScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
        });

        it('should ignore deeplink targets when manually expanding (toggle)', async () => {
            fixture.componentRef.setInput('deepLink', undefined);

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

            const collapseButton = fixture.debugElement.query(By.css('#lecture-unit-toggle-button'));
            collapseButton.nativeElement.click();

            await vi.waitFor(() => {
                expect(videoScrollSpy).not.toHaveBeenCalled();
                expect(pdfScrollSpy).not.toHaveBeenCalled();
                expect(unitCardScrollSpy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
            });
        });

        it('should expand and scroll again when the same place is requested a second time', async () => {
            const mockVideoPlayer = document.createElement('div');
            mockVideoPlayer.scrollIntoView = vi.fn();
            const videoScrollSpy = mockVideoPlayer.scrollIntoView as ReturnType<typeof vi.fn>;
            vi.spyOn(fixture.nativeElement, 'querySelector').mockReturnValue(mockVideoPlayer);

            fixture.componentRef.setInput('deepLink', deepLinkTo({ timestamp: 30 }));
            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(videoScrollSpy).toHaveBeenCalledTimes(1);
            });

            component.toggleCollapse();
            fixture.detectChanges();
            expect(component.isCollapsed()).toBe(true);

            fixture.componentRef.setInput('deepLink', deepLinkTo({ timestamp: 30 }));
            fixture.detectChanges();

            await vi.waitFor(() => {
                expect(videoScrollSpy).toHaveBeenCalledTimes(2);
            });
            expect(component.isCollapsed()).toBe(false);
        });
    });
});
