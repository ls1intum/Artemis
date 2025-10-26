import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { LectureVideoPlayerComponent } from './lecture-video-player.component';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { AlertService } from 'app/core/util/alert.service';
import { MockProvider } from 'ng-mocks';

// Mock HLS.js
declare global {
    interface Window {
        Hls: any;
    }
}

describe('LectureVideoPlayerComponent', () => {
    let component: LectureVideoPlayerComponent;
    let fixture: ComponentFixture<LectureVideoPlayerComponent>;
    let lectureService: LectureService;
    let alertService: AlertService;

    const mockLectureId = 456;
    const mockStreamUrl = 'https://nebula.example.com/video-storage/playlist/test-video-id/playlist.m3u8';
    const mockHls = {
        isSupported: jest.fn(() => true),
        loadSource: jest.fn(),
        attachMedia: jest.fn(),
        on: jest.fn(),
        destroy: jest.fn(),
        startLoad: jest.fn(),
        recoverMediaError: jest.fn(),
    };

    beforeEach(async () => {
        // Mock HLS.js
        window.Hls = jest.fn(() => mockHls);
        window.Hls.isSupported = mockHls.isSupported;
        window.Hls.Events = {
            MANIFEST_PARSED: 'manifestParsed',
            ERROR: 'error',
        };
        window.Hls.ErrorTypes = {
            NETWORK_ERROR: 'networkError',
            MEDIA_ERROR: 'mediaError',
        };

        await TestBed.configureTestingModule({
            imports: [LectureVideoPlayerComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(LectureService), MockProvider(AlertService)],
        }).compileComponents();

        fixture = TestBed.createComponent(LectureVideoPlayerComponent);
        component = fixture.componentInstance;
        lectureService = TestBed.inject(LectureService);
        alertService = TestBed.inject(AlertService);

        component.lectureId = mockLectureId;
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('Component Initialization', () => {
        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should initialize with loading state', () => {
            expect(component.loading()).toBeTrue();
            expect(component.streamUrl()).toBeNull();
        });

        it('should load video stream URL on init', fakeAsync(() => {
            // Arrange
            const getStreamUrlSpy = jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));

            // Act
            component.ngOnInit();
            tick(100); // Wait for setTimeout

            // Assert
            expect(getStreamUrlSpy).toHaveBeenCalledWith(mockLectureId);
            expect(component.streamUrl()).toBe(mockStreamUrl);
            expect(component.loading()).toBeFalse();
        }));

        it('should handle error when loading stream URL', () => {
            // Arrange
            const error = { error: { message: 'Video not found' } };
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(throwError(() => error));
            const alertSpy = jest.spyOn(alertService, 'error');

            // Act
            component.ngOnInit();

            // Assert
            expect(component.loading()).toBeFalse();
            expect(alertSpy).toHaveBeenCalledWith('Video not found');
        });
    });

    describe('HLS Player Initialization', () => {
        beforeEach(() => {
            // Set up component with video element
            component.streamUrl.set(mockStreamUrl);
            component.loading.set(false);
            fixture.detectChanges();
        });

        it('should initialize HLS player when HLS.js is supported', fakeAsync(() => {
            // Arrange
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));

            // Act
            component.ngOnInit();
            tick(100);

            // Assert - HLS instance created and configured
            expect(mockHls.loadSource).toHaveBeenCalledWith(mockStreamUrl);
            expect(mockHls.attachMedia).toHaveBeenCalled();
            expect(mockHls.on).toHaveBeenCalled();
        }));

        it('should use native HLS for Safari when HLS.js not supported', fakeAsync(() => {
            // Arrange
            mockHls.isSupported.mockReturnValue(false);
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));

            // Mock video element with native HLS support
            const mockVideoElement = {
                canPlayType: jest.fn((type: string) => (type === 'application/vnd.apple.mpegurl' ? 'probably' : '')),
                src: '',
            };

            component.videoPlayerRef = {
                nativeElement: mockVideoElement as any,
            } as any;

            // Act
            component.ngOnInit();
            tick(100);

            // Assert
            expect(mockVideoElement.src).toBe(mockStreamUrl);
        }));

        it('should show error when HLS is not supported', fakeAsync(() => {
            // Arrange
            mockHls.isSupported.mockReturnValue(false);
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));
            const alertSpy = jest.spyOn(alertService, 'error');

            const mockVideoElement = {
                canPlayType: jest.fn(() => ''),
            };

            component.videoPlayerRef = {
                nativeElement: mockVideoElement as any,
            } as any;

            // Act
            component.ngOnInit();
            tick(100);

            // Assert
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.lecture.video.notSupported');
        }));
    });

    describe('HLS Error Handling', () => {
        it('should handle network errors and retry', fakeAsync(() => {
            // Arrange
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));
            const alertSpy = jest.spyOn(alertService, 'error');

            let errorCallback: any;
            mockHls.on.mockImplementation((event: string, callback: any) => {
                if (event === 'error') {
                    errorCallback = callback;
                }
            });

            // Act
            component.ngOnInit();
            tick(100);

            // Simulate network error
            errorCallback(null, {
                type: 'networkError',
                fatal: true,
            });

            // Assert
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.lecture.video.networkError');
            expect(mockHls.startLoad).toHaveBeenCalled();
        }));

        it('should handle media errors and recover', fakeAsync(() => {
            // Arrange
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));
            const alertSpy = jest.spyOn(alertService, 'error');

            let errorCallback: any;
            mockHls.on.mockImplementation((event: string, callback: any) => {
                if (event === 'error') {
                    errorCallback = callback;
                }
            });

            // Act
            component.ngOnInit();
            tick(100);

            // Simulate media error
            errorCallback(null, {
                type: 'mediaError',
                fatal: true,
            });

            // Assert
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.lecture.video.mediaError');
            expect(mockHls.recoverMediaError).toHaveBeenCalled();
        }));

        it('should handle fatal errors and destroy player', fakeAsync(() => {
            // Arrange
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));
            const alertSpy = jest.spyOn(alertService, 'error');

            let errorCallback: any;
            mockHls.on.mockImplementation((event: string, callback: any) => {
                if (event === 'error') {
                    errorCallback = callback;
                }
            });

            // Act
            component.ngOnInit();
            tick(100);

            // Simulate fatal error
            errorCallback(null, {
                type: 'otherError',
                fatal: true,
            });

            // Assert
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.lecture.video.fatalError');
            expect(mockHls.destroy).toHaveBeenCalled();
        }));
    });

    describe('Component Cleanup', () => {
        it('should destroy HLS instance on component destroy', fakeAsync(() => {
            // Arrange
            jest.spyOn(lectureService, 'getVideoStreamUrl').mockReturnValue(of(mockStreamUrl));

            // Act
            component.ngOnInit();
            tick(100);
            component.ngOnDestroy();

            // Assert
            expect(mockHls.destroy).toHaveBeenCalled();
        }));

        it('should handle destroy when HLS instance is null', () => {
            // Act & Assert - Should not throw error
            expect(() => component.ngOnDestroy()).not.toThrow();
        });
    });
});
