import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BrowserModule } from '@angular/platform-browser';

describe('VideoUnitComponent', () => {
    const exampleName = 'Test';
    const exampleDescription = 'Lorem Ipsum';
    const exampleSource = 'https://www.youtube.com/embed/8iU8LPEa4o0';
    let videoUnitComponentFixture: ComponentFixture<VideoUnitComponent>;
    let videoUnitComponent: VideoUnitComponent;
    let videoUnit: VideoUnit;

    beforeEach(() => {
        videoUnit = new VideoUnit();
        videoUnit.name = exampleName;
        videoUnit.description = exampleDescription;
        videoUnit.source = exampleSource;

        TestBed.configureTestingModule({
            imports: [BrowserModule],
            declarations: [
                VideoUnitComponent,
                SafeResourceUrlPipe,
                MockComponent(FaIconComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbCollapse),
                MockDirective(NgbTooltip),
            ],
            providers: [{ provide: SafeResourceUrlPipe, useClass: SafeResourceUrlPipe }],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                videoUnitComponentFixture = TestBed.createComponent(VideoUnitComponent);
                videoUnitComponent = videoUnitComponentFixture.componentInstance;
                videoUnitComponent.videoUnit = videoUnit;
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

        expect(handleCollapseSpy).toHaveBeenCalled();
        expect(videoUnitComponent.isCollapsed).toBeFalse();

        handleCollapseSpy.mockRestore();
    });

    it('should call complete callback when uncollapsed after timeout', (done) => {
        jest.useFakeTimers();
        jest.spyOn(global, 'setTimeout');
        videoUnitComponent.onCompletion.subscribe((event) => {
            expect(event.lectureUnit).toEqual(videoUnit);
            expect(event.completed).toBeTrue();
            done();
        });
        videoUnitComponent.handleCollapse(new Event('click'));
        expect(setTimeout).toHaveBeenCalledTimes(1);
        expect(setTimeout).toHaveBeenLastCalledWith(expect.any(Function), 1000 * 60 * 5);
        jest.runAllTimers();
    }, 1000);

    it('should call completion callback when clicked', (done) => {
        videoUnitComponent.onCompletion.subscribe((event) => {
            expect(event.lectureUnit).toEqual(videoUnit);
            expect(event.completed).toBeFalse();
            done();
        });
        videoUnitComponent.handleClick(new Event('click'), false);
    }, 1000);
});
