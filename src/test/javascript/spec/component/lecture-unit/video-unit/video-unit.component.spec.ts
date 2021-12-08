import * as sinon from 'sinon';
import sinonChai from 'sinon-chai';
import * as chai from 'chai';

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

chai.use(sinonChai);
const expect = chai.expect;

describe('VideoUnitComponent', () => {
    const sandbox = sinon.createSandbox();
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
            });
    });

    afterEach(function () {
        sandbox.restore();
    });

    it('should initialize', () => {
        videoUnitComponentFixture.detectChanges();
        expect(videoUnitComponent).to.be.ok;
    });

    it('should iFrame correctly', () => {
        videoUnitComponent.videoUnit = videoUnit;
        videoUnitComponentFixture.detectChanges(); // ngInit
        const iFrame = videoUnitComponentFixture.debugElement.nativeElement.querySelector('#videoFrame');
        expect(iFrame.src).to.equal(videoUnit.source);
    });

    it('should collapse when clicked', () => {
        videoUnitComponent.videoUnit = videoUnit;
        videoUnitComponentFixture.detectChanges(); // ngInit
        expect(videoUnitComponent.isCollapsed).to.be.true;
        const handleCollapseSpy = sinon.spy(videoUnitComponent, 'handleCollapse');

        const container = videoUnitComponentFixture.debugElement.nativeElement.querySelector('.card-header');
        expect(container).to.be.ok;
        container.click();

        expect(handleCollapseSpy).to.have.been.called;
        expect(videoUnitComponent.isCollapsed).to.be.false;

        handleCollapseSpy.restore();
    });
});
