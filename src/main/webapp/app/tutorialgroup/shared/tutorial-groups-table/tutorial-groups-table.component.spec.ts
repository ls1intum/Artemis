import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { Component, SimpleChange, input, viewChild, viewChildren } from '@angular/core';
import { TutorialGroupRowStubComponent } from 'test/helpers/stubs/tutorialgroup/tutorial-groups-table-stub.component';
import { Course, Language } from 'app/core/course/shared/entities/course.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { runOnPushChangeDetection } from 'test/helpers/on-push-change-detection.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { provideHttpClient } from '@angular/common/http';
import { TutorialGroupsTableComponent } from 'app/tutorialgroup/shared/tutorial-groups-table/tutorial-groups-table.component';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';

@Component({ selector: 'jhi-mock-extra-column', template: '' })
class MockExtraColumnComponent {
    readonly tutorialGroup = input<TutorialGroup>();
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-groups-table [tutorialGroups]="tutorialGroups()" [course]="course()" [showIdColumn]="true">
            <ng-template let-tutorialGroup>
                <jhi-mock-extra-column [tutorialGroup]="tutorialGroup" />
            </ng-template>
        </jhi-tutorial-groups-table>
    `,
    imports: [TutorialGroupsTableComponent, MockExtraColumnComponent],
})
class MockWrapperComponent {
    readonly tutorialGroups = input<TutorialGroup[]>([]);
    readonly course = input.required<Course>();

    tutorialGroupTableInstance = viewChild.required(TutorialGroupsTableComponent);
    mockExtraColumns = viewChildren(MockExtraColumnComponent);
}

describe('TutorialGroupTableWrapperTest', () => {
    let fixture: ComponentFixture<MockWrapperComponent>;
    let component: MockWrapperComponent;
    let tableInstance: TutorialGroupsTableComponent;
    let mockExtraColumns: MockExtraColumnComponent[];
    let tutorialGroupOne: TutorialGroup;
    let tutorialGroupTwo: TutorialGroup;
    const course = {
        id: 1,
        title: 'Test Course',
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [
                TutorialGroupsTableComponent,
                TutorialGroupRowStubComponent,
                MockWrapperComponent,
                MockExtraColumnComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(SortService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapperComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });
                fixture.componentRef.setInput('tutorialGroups', [tutorialGroupOne, tutorialGroupTwo]);
                fixture.componentRef.setInput('course', course);
                fixture.detectChanges();
                tableInstance = component.tutorialGroupTableInstance();
                mockExtraColumns = [...component.mockExtraColumns()]; // spread to make mutable
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should pass the tutorialGroup to the headers', () => {
        expect(tableInstance.tutorialGroups()).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(tableInstance.course()).toEqual(course);
        expect(mockExtraColumns).toHaveLength(2);
        mockExtraColumns.sort((a, b) => a.tutorialGroup()!.id! - b.tutorialGroup()!.id!);
        expect(mockExtraColumns[0].tutorialGroup()).toEqual(tutorialGroupOne);
        expect(mockExtraColumns[1].tutorialGroup()).toEqual(tutorialGroupTwo);
        expect(fixture.nativeElement.querySelectorAll('jhi-mock-extra-column')).toHaveLength(2);
    });
});

describe('TutorialGroupsTableComponent', () => {
    let fixture: ComponentFixture<TutorialGroupsTableComponent>;
    let component: TutorialGroupsTableComponent;

    let tutorialGroupOne: TutorialGroup;
    let tutorialGroupTwo: TutorialGroup;
    const course = {
        id: 1,
        title: 'Test Course',
    } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(SortService), MockProvider(ActivatedRoute), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsTableComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });
                fixture.componentRef.setInput('tutorialGroups', [tutorialGroupOne, tutorialGroupTwo]);
                fixture.componentRef.setInput('course', course);
                fixture.componentRef.setInput('showIdColumn', true);
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should show the language column if multiple languages are present', () => {
        tutorialGroupOne.language = Language.ENGLISH;
        tutorialGroupTwo.language = Language.GERMAN;

        fixture.componentRef.setInput('tutorialGroups', [tutorialGroupOne, tutorialGroupTwo]);
        fixture.changeDetectorRef.detectChanges();

        expect(fixture.nativeElement.querySelector('#language-column')).not.toBeNull();

        tutorialGroupOne.language = Language.ENGLISH;
        tutorialGroupTwo.language = Language.ENGLISH;

        fixture.componentRef.setInput('tutorialGroups', [tutorialGroupOne, tutorialGroupTwo]);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture.nativeElement.querySelector('#language-column')).toBeNull();
    });

    it('should show the language column if multiple formats are present', () => {
        tutorialGroupOne.isOnline = true;
        tutorialGroupTwo.isOnline = false;

        component.ngOnChanges({ tutorialGroups: new SimpleChange([], [tutorialGroupOne, tutorialGroupTwo], true) });
        runOnPushChangeDetection(fixture);

        expect(fixture.nativeElement.querySelector('#online-column')).not.toBeNull();

        tutorialGroupOne.isOnline = true;
        tutorialGroupTwo.isOnline = true;

        component.ngOnChanges({ tutorialGroups: new SimpleChange([], [tutorialGroupOne, tutorialGroupTwo], true) });
        runOnPushChangeDetection(fixture);
        expect(fixture.nativeElement.querySelector('#online-column')).toBeNull();
    });

    it('should show the language column if multiple campuses are present', () => {
        tutorialGroupOne.campus = 'Garching';
        tutorialGroupTwo.campus = 'Munich';

        component.ngOnChanges({ tutorialGroups: new SimpleChange([], [tutorialGroupOne, tutorialGroupTwo], true) });
        runOnPushChangeDetection(fixture);

        expect(fixture.nativeElement.querySelector('#campus-column')).not.toBeNull();

        tutorialGroupOne.campus = 'Garching';
        tutorialGroupTwo.campus = 'Garching';

        component.ngOnChanges({ tutorialGroups: new SimpleChange([], [tutorialGroupOne, tutorialGroupTwo], true) });
        runOnPushChangeDetection(fixture);
        expect(fixture.nativeElement.querySelector('#campus-column')).toBeNull();
    });

    it('should call sort service', () => {
        component.sortingPredicate = 'id';
        component.ascending = false;

        const sortService = fixture.debugElement.injector.get(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], 'id', false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });

    it('should call sort service with day and time', () => {
        component.sortingPredicate = 'dayAndTime';
        component.ascending = false;

        const sortService = fixture.debugElement.injector.get(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByMultipleProperties');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], ['tutorialGroupSchedule.dayOfWeek', 'tutorialGroupSchedule.startTime'], false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });

    it('should call sort service with capacity and number of registered users', () => {
        component.sortingPredicate = 'capacityAndRegistrations';
        component.ascending = false;

        const sortService = fixture.debugElement.injector.get(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByMultipleProperties');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], ['capacity', 'numberOfRegisteredUsers'], false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });
});
