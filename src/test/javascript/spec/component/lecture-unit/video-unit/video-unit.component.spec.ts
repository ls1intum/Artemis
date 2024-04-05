import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BrowserModule } from '@angular/platform-browser';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';

describe('VideoUnitComponent', () => {
    const exampleName = 'Test';
    const exampleDescription = 'Lorem Ipsum';
    const exampleSource = 'https://www.youtube.com/embed/8iU8LPEa4o0';
    let videoUnitComponentFixture: ComponentFixture<VideoUnitComponent>;
    let videoUnitComponent: VideoUnitComponent;
    let videoUnit: VideoUnit;
    let scienceService: ScienceService;
    let logEventStub: jest.SpyInstance;

    beforeEach(() => {
        videoUnit = new VideoUnit();
        videoUnit.name = exampleName;
        videoUnit.description = exampleDescription;
        videoUnit.source = exampleSource;

        TestBed.configureTestingModule({
            imports: [BrowserModule, NgbCollapseMocksModule, MockDirective(NgbTooltip)],
            declarations: [VideoUnitComponent, SafeResourceUrlPipe, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: SafeResourceUrlPipe, useClass: SafeResourceUrlPipe },
                { provide: ScienceService, useClass: MockScienceService },
            ],
        })
            .compileComponents()
            .then(() => {
                videoUnitComponentFixture = TestBed.createComponent(VideoUnitComponent);
                videoUnitComponent = videoUnitComponentFixture.componentInstance;
                videoUnitComponent.videoUnit = videoUnit;
                scienceService = TestBed.inject(ScienceService);
                logEventStub = jest.spyOn(scienceService, 'logEvent');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should iFrame correctly', () => {
        videoUnitComponent.isCollapsed = false;
        videoUnitComponentFixture.detectChanges(); // ngInit
        const iFrame = videoUnitComponentFixture.debugElement.nativeElement.querySelector('#videoFrame');
        expect(iFrame.src).toEqual(videoUnit.source);
    });

    it('should not have iFrame', () => {
        videoUnitComponentFixture.detectChanges(); // ngInit
        const iFrame = videoUnitComponentFixture.debugElement.nativeElement.querySelector('#videoFrame');
        expect(iFrame).toBeNull();
    });

    it('should collapse when clicked', () => {
        videoUnitComponentFixture.detectChanges(); // ngInit
        expect(videoUnitComponent.isCollapsed).toBeTrue();
        const handleCollapseSpy = jest.spyOn(videoUnitComponent, 'handleCollapse');

        const container = videoUnitComponentFixture.debugElement.nativeElement.querySelector('.card-header');
        expect(container).not.toBeNull();
        container.click();

        expect(handleCollapseSpy).toHaveBeenCalledOnce();
        expect(videoUnitComponent.isCollapsed).toBeFalse();

        handleCollapseSpy.mockRestore();
    });

    it('should call complete callback when expanded after timeout', () => {
        return new Promise<void>((done) => {
            jest.useFakeTimers();
            jest.spyOn(global, 'setTimeout');
            videoUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(videoUnit);
                expect(event.completed).toBeTrue();
                done();
            });
            videoUnitComponent.handleCollapse(new Event('click'));
            expect(setTimeout).toHaveBeenCalledOnce();
            expect(setTimeout).toHaveBeenLastCalledWith(expect.any(Function), 1000 * 60 * 5);
            jest.runAllTimers();
        });
    }, 1000);

    it('should call completion callback when clicked', () => {
        return new Promise<void>((done) => {
            videoUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(videoUnit);
                expect(event.completed).toBeFalse();
                done();
            });
            videoUnitComponent.handleClick(new Event('click'), false);
        });
    }, 1000);

    it('should log event on open', () => {
        videoUnitComponent.isCollapsed = true;
        videoUnitComponentFixture.detectChanges(); // ngInit
        videoUnitComponent.handleCollapse(new Event('click'));
        expect(logEventStub).toHaveBeenCalledExactlyOnceWith(ScienceEventType.LECTURE__OPEN_UNIT, videoUnit.id!);
    });
});
