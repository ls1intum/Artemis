import { ScienceService } from 'app/shared/science/science.service';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';

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
            imports: [VideoUnitComponent],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: SafeResourceUrlPipe, useClass: SafeResourceUrlPipe },
                { provide: ScienceService, useClass: MockScienceService },
            ],
        }).compileComponents();

        scienceService = TestBed.inject(ScienceService);

        fixture = TestBed.createComponent(VideoUnitComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('lectureUnit', videoUnit);
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

        const iFrame = fixture.debugElement.nativeElement.querySelector('#videoFrame');
        expect(iFrame.src).toEqual(videoUnit.source);
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
});
