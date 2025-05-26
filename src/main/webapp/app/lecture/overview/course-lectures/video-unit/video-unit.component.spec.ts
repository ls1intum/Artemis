import { ScienceService } from 'app/shared/science/science.service';
import { VideoUnitComponent } from 'app/lecture/overview/course-lectures/video-unit/video-unit.component';
import { MockScienceService } from 'test/helpers/mocks/service/mock-science-service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VideoUnit } from 'app/lecture/shared/entities/lecture-unit/videoUnit.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';
import { CompetencyContributionCardDTO } from 'app/atlas/shared/entities/competency.model';
import { of } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('VideoUnitComponent', () => {
    let scienceService: ScienceService;

    let component: VideoUnitComponent;
    let fixture: ComponentFixture<VideoUnitComponent>;

    const videoUnit: VideoUnit = {
        id: 1,
        name: 'Test',
        description: 'Lorem Ipsum',
        source: 'https://www.youtube.com/embed/8iU8LPEa4o0',
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [VideoUnitComponent, MockComponent(CompetencyContributionComponent)],
            declarations: [
                MockComponent({
                    selector: 'jhi-video-player',
                    inputs: ['videoUrl', 'transcriptSegments'],
                }),
            ],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: SafeResourceUrlPipe, useClass: SafeResourceUrlPipe },
                { provide: ScienceService, useClass: MockScienceService },
                MockProvider(CourseCompetencyService),
                MockProvider(ProfileService),
            ],
        }).compileComponents();

        const competencyService = TestBed.inject(CourseCompetencyService);
        jest.spyOn(competencyService, 'getCompetencyContributionsForLectureUnit').mockReturnValue(of({} as HttpResponse<CompetencyContributionCardDTO[]>));

        scienceService = TestBed.inject(ScienceService);

        fixture = TestBed.createComponent(VideoUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', videoUnit);
        fixture.componentRef.setInput('courseId', 1);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
    });

    it('should show iFrame correctly', () => {
        fixture.detectChanges();

        const lectureUnitToggleButton = fixture.debugElement.query(By.css('#lecture-unit-toggle-button'));
        lectureUnitToggleButton.nativeElement.click();

        fixture.detectChanges();

        const iFrame = fixture.debugElement.nativeElement.querySelector('iframe');
        expect(iFrame).not.toBeNull();
        expect(iFrame?.src).toContain('youtube.com');
    });

    it('should toggle collapse, log event and set timeout on open', () => {
        const logEventSpy = jest.spyOn(scienceService, 'logEvent');
        const onCompletionSpy = jest.spyOn(component.onCompletion, 'emit');

        jest.useFakeTimers();
        fixture.detectChanges();
        component.toggleCollapse(false); // Toggle to open

        fixture.detectChanges();

        expect(logEventSpy).toHaveBeenCalled();

        jest.runAllTimers();

        expect(onCompletionSpy).toHaveBeenCalledWith({
            lectureUnit: videoUnit,
            completed: true,
        });
    });

    it('should toggle collapse and clear timeout on close', () => {
        const clearTimeoutSpy = jest.spyOn(global, 'clearTimeout');
        component.toggleCollapse(false); // Initially open

        component.toggleCollapse(true); // Toggle to close

        expect(clearTimeoutSpy).toHaveBeenCalledOnce();
    });

    it('should fetch and pass transcript segments to jhi-video-player for HLS (.m3u8) video', () => {
        const hlsVideoUnit: VideoUnit = {
            ...videoUnit,
            source: 'https://tum.live/hls/streamId/playlist.m3u8?jwt=abc123',
        };

        const mockTranscript = [
            { startTime: 0, endTime: 5, text: 'Intro', slideNumber: 1 },
            { startTime: 6, endTime: 10, text: 'Part A', slideNumber: 2 },
        ];

        fixture.componentRef.setInput('lectureUnit', hlsVideoUnit);
        fixture.detectChanges();

        const lectureUnitToggleButton = fixture.debugElement.query(By.css('#lecture-unit-toggle-button'));
        lectureUnitToggleButton.nativeElement.click();
        fixture.detectChanges();

        const req = httpMock.expectOne(`/api/lecture/lecture-unit/${hlsVideoUnit.id}/transcript`);
        expect(req.request.method).toBe('GET');
        req.flush({ segments: mockTranscript });
        fixture.detectChanges();

        const videoPlayerEl = fixture.debugElement.query(By.css('jhi-video-player'));
        expect(videoPlayerEl).not.toBeNull();

        const videoPlayerInstance = videoPlayerEl.componentInstance;
        expect(videoPlayerInstance.transcriptSegments).toEqual(mockTranscript);
    });
});
