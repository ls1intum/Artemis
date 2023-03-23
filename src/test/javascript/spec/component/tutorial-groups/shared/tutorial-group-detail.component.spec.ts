import { TutorialGroupDetailComponent } from 'app/course/tutorial-groups/shared/tutorial-group-detail/tutorial-group-detail.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { Component, Input, ViewChild } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { SortService } from 'app/shared/service/sort.service';
import { runOnPushChangeDetection } from '../../../helpers/on-push-change-detection.helper';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/course/tutorial-groups/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';
import { RemoveSecondsPipe } from '../../../../../../main/webapp/app/course/tutorial-groups/shared/remove-seconds.pipe';

@Component({ selector: 'jhi-mock-header', template: '<div id="mockHeader"></div>' })
class MockHeaderComponent {
    @Input() tutorialGroup: TutorialGroup;
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-group-detail [tutorialGroup]="tutorialGroup">
            <ng-template let-tutorialGroup>
                <jhi-mock-header [tutorialGroup]="tutorialGroup"></jhi-mock-header>
            </ng-template>
        </jhi-tutorial-group-detail>
    `,
})
class MockWrapperComponent {
    @Input()
    tutorialGroup: TutorialGroup;

    @ViewChild(TutorialGroupDetailComponent)
    tutorialGroupDetailInstance: TutorialGroupDetailComponent;

    @ViewChild(MockHeaderComponent)
    mockHeaderInstance: MockHeaderComponent;
}

describe('TutorialGroupDetailWrapperTest', () => {
    let fixture: ComponentFixture<MockWrapperComponent>;
    let component: MockWrapperComponent;
    let detailInstance: TutorialGroupDetailComponent;
    let headerInstance: MockHeaderComponent;
    let exampleTutorialGroup: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [
                TutorialGroupDetailComponent,
                MockWrapperComponent,
                MockHeaderComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(RemoveSecondsPipe),
                MockComponent(FaIconComponent),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
            ],
            providers: [MockProvider(ArtemisMarkdownService), MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapperComponent);
                component = fixture.componentInstance;
                exampleTutorialGroup = generateExampleTutorialGroup({});
                component.tutorialGroup = exampleTutorialGroup;
                fixture.detectChanges();
                detailInstance = component.tutorialGroupDetailInstance;
                headerInstance = component.mockHeaderInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should pass the tutorialGroup to the header', () => {
        expect(headerInstance.tutorialGroup).toEqual(component.tutorialGroup);
        expect(component.tutorialGroup).toEqual(detailInstance.tutorialGroup);
        const mockHeader = fixture.debugElement.nativeElement.querySelector('#mockHeader');
        expect(mockHeader).toBeTruthy();
    });
});

describe('TutorialGroupDetailComponent', () => {
    let fixture: ComponentFixture<TutorialGroupDetailComponent>;
    let component: TutorialGroupDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgbTooltipMocksModule],
            declarations: [
                TutorialGroupDetailComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockPipe(RemoveSecondsPipe),
            ],
            providers: [MockProvider(ArtemisMarkdownService), MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupDetailComponent);
                component = fixture.componentInstance;
                component.tutorialGroup = generateExampleTutorialGroup({});
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should call courseClickHandler', () => {
        const courseClickHandler = jest.fn();
        component.courseClickHandler = courseClickHandler;
        runOnPushChangeDetection(fixture);
        const courseLink = fixture.debugElement.nativeElement.querySelector('#courseLink');
        courseLink.click();
        expect(courseClickHandler).toHaveBeenCalledOnce();
    });

    it('should call registrationClickHandler', () => {
        const registrationClickHandler = jest.fn();
        component.registrationClickHandler = registrationClickHandler;
        runOnPushChangeDetection(fixture);
        const registrationLink = fixture.debugElement.nativeElement.querySelector('#registrationLink');
        registrationLink.click();
        expect(registrationClickHandler).toHaveBeenCalledOnce();
    });
});
