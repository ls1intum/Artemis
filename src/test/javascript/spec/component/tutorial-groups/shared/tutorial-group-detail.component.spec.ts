import { TutorialGroupDetailComponent } from 'app/course/tutorial-groups/shared/tutorial-group-detail/tutorial-group-detail.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { Component, Input, ViewChild } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({ selector: 'jhi-mock-header', template: '<div id="mockHeader"></div>' })
class MockHeader {
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
class MockWrapper {
    @Input()
    tutorialGroup: TutorialGroup;

    @ViewChild(TutorialGroupDetailComponent)
    tutorialGroupDetailInstance: TutorialGroupDetailComponent;

    @ViewChild(MockHeader)
    mockHeaderInstance: MockHeader;
}

describe('TutorialGroupDetailWrapperTest', () => {
    let fixture: ComponentFixture<MockWrapper>;
    let component: MockWrapper;
    let detailInstance: TutorialGroupDetailComponent;
    let headerInstance: MockHeader;
    let exampleTutorialGroup: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [TutorialGroupDetailComponent, MockWrapper, MockHeader, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisMarkdownService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapper);
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
            declarations: [TutorialGroupDetailComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(ArtemisMarkdownService)],
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
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should call courseClickHandler', () => {
        const courseClickHandler = jest.fn();
        component.courseClickHandler = courseClickHandler;
        fixture.detectChanges();
        const courseLink = fixture.debugElement.nativeElement.querySelector('#courseLink');
        courseLink.click();
        expect(courseClickHandler).toHaveBeenCalledOnce();
    });

    it('should call registrationClickHandler', () => {
        const registrationClickHandler = jest.fn();
        component.registrationClickHandler = registrationClickHandler;
        fixture.detectChanges();
        const registrationLink = fixture.debugElement.nativeElement.querySelector('#registrationLink');
        registrationLink.click();
        expect(registrationClickHandler).toHaveBeenCalledOnce();
    });

    it('should render template reference and pass in tutorial group', () => {
        fixture.detectChanges();
        const templateReference = fixture.debugElement.nativeElement.querySelector('#templateReference');
        expect(templateReference).not.toBeNull();
    });
});
