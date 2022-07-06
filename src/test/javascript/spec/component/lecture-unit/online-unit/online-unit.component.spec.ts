import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { OnlineUnit } from 'app/entities/lecture-unit/onlineUnit.model';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BrowserModule } from '@angular/platform-browser';

describe('OnlineUnitComponent', () => {
    const exampleName = 'Test';
    const exampleDescription = 'Lorem Ipsum';
    const exampleSource = 'https://www.example.com';
    let onlineUnitComponentFixture: ComponentFixture<OnlineUnitComponent>;
    let onlineUnitComponent: OnlineUnitComponent;
    let onlineUnit: OnlineUnit;

    beforeEach(() => {
        onlineUnit = new OnlineUnit();
        onlineUnit.name = exampleName;
        onlineUnit.description = exampleDescription;
        onlineUnit.source = exampleSource;

        TestBed.configureTestingModule({
            imports: [BrowserModule],
            declarations: [
                OnlineUnitComponent,
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
                onlineUnitComponentFixture = TestBed.createComponent(OnlineUnitComponent);
                onlineUnitComponent = onlineUnitComponentFixture.componentInstance;
                onlineUnitComponent.onlineUnit = onlineUnit;
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

    it('should call completion callback when opening link', (done) => {
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
    }, 1000);

    it('should call completion callback when clicked', (done) => {
        onlineUnitComponent.onCompletion.subscribe((event) => {
            expect(event.lectureUnit).toEqual(onlineUnit);
            expect(event.completed).toBeFalse();
            done();
        });
        onlineUnitComponent.handleClick(new Event('click'), false);
    }, 1000);
});
