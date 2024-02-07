import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BrowserModule } from '@angular/platform-browser';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ScienceEventType } from 'app/shared/science/science.model';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';

describe('OnlineUnitComponent', () => {
    const exampleName = 'Test';
    const exampleDescription = 'Lorem Ipsum';
    const exampleSource = 'https://www.example.com';
    let onlineUnitComponentFixture: ComponentFixture<OnlineUnitComponent>;
    let onlineUnitComponent: OnlineUnitComponent;
    let onlineUnit: OnlineUnit;
    let scienceService: ScienceService;
    let logEventStub: jest.SpyInstance;

    beforeEach(() => {
        onlineUnit = new OnlineUnit();
        onlineUnit.id = 1;
        onlineUnit.name = exampleName;
        onlineUnit.description = exampleDescription;
        onlineUnit.source = exampleSource;

        TestBed.configureTestingModule({
            imports: [BrowserModule, MockDirective(NgbTooltip), MockDirective(NgbCollapse)],
            declarations: [OnlineUnitComponent, SafeResourceUrlPipe, MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [
                { provide: SafeResourceUrlPipe, useClass: SafeResourceUrlPipe },
                { provide: ScienceService, useClass: MockScienceService },
            ],
        })
            .compileComponents()
            .then(() => {
                onlineUnitComponentFixture = TestBed.createComponent(OnlineUnitComponent);
                onlineUnitComponent = onlineUnitComponentFixture.componentInstance;
                onlineUnitComponent.onlineUnit = onlineUnit;
                scienceService = TestBed.inject(ScienceService);
                logEventStub = jest.spyOn(scienceService, 'logEvent');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should collapse when clicked', () => {
        onlineUnitComponentFixture.detectChanges();
        expect(onlineUnitComponent.isCollapsed).toBeTrue();
        const handleCollapseSpy = jest.spyOn(onlineUnitComponent, 'handleCollapse');

        const container = onlineUnitComponentFixture.debugElement.nativeElement.querySelector('.card-header');
        expect(container).not.toBeNull();
        container.click();

        expect(handleCollapseSpy).toHaveBeenCalledOnce();
        expect(onlineUnitComponent.isCollapsed).toBeFalse();

        handleCollapseSpy.mockRestore();
    });

    it('should call completion callback when opening link', () => {
        return new Promise<void>((done) => {
            const windowSpy = jest.spyOn(window, 'open').mockImplementation(() => {
                return null;
            });
            onlineUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(onlineUnit);
                expect(event.completed).toBeTrue();
                done();
            });
            onlineUnitComponent.openLink(new Event('click'));
            expect(windowSpy).toHaveBeenCalledOnce();
            expect(windowSpy).toHaveBeenCalledWith(exampleSource, '_blank');
        });
    }, 1000);

    it('should call completion callback when clicked', () => {
        return new Promise<void>((done) => {
            onlineUnitComponent.onCompletion.subscribe((event) => {
                expect(event.lectureUnit).toEqual(onlineUnit);
                expect(event.completed).toBeFalse();
                done();
            });
            onlineUnitComponent.handleClick(new Event('click'), false);
        });
    }, 1000);

    it('should log event on open link', () => {
        jest.spyOn(window, 'open').mockImplementation(() => {
            return null;
        });
        onlineUnitComponentFixture.detectChanges(); // ngInit
        onlineUnitComponent.openLink(new Event('click'));
        expect(logEventStub).toHaveBeenCalledExactlyOnceWith(ScienceEventType.LECTURE__OPEN_UNIT, onlineUnit.id!);
    });
});
