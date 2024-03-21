import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupsTableComponent } from 'app/course/tutorial-groups/shared/tutorial-groups-table/tutorial-groups-table.component';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { SortService } from 'app/shared/service/sort.service';
import { generateExampleTutorialGroup } from '../helpers/tutorialGroupExampleModels';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { Component, Input, QueryList, SimpleChange, ViewChild, ViewChildren } from '@angular/core';
import { TutorialGroupRowStubComponent } from '../stubs/tutorial-groups-table-stub.component';
import { Course, Language } from 'app/entities/course.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { runOnPushChangeDetection } from '../../../helpers/on-push-change-detection.helper';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/course/tutorial-groups/shared/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';

@Component({ selector: 'jhi-mock-extra-column', template: '' })
class MockExtraColumnComponent {
    @Input() tutorialGroup: TutorialGroup;
}

@Component({
    selector: 'jhi-mock-wrapper',
    template: `
        <jhi-tutorial-groups-table [tutorialGroups]="tutorialGroups" [course]="course" [showIdColumn]="true">
            <ng-template let-tutorialGroup>
                <jhi-mock-extra-column [tutorialGroup]="tutorialGroup" />
            </ng-template>
        </jhi-tutorial-groups-table>
    `,
})
class MockWrapperComponent {
    @Input()
    tutorialGroups: TutorialGroup[];

    @Input()
    course: Course;

    @ViewChild(TutorialGroupsTableComponent)
    tutorialGroupTableInstance: TutorialGroupsTableComponent;

    @ViewChildren(MockExtraColumnComponent)
    mockExtraColumns: QueryList<MockExtraColumnComponent>;
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
            imports: [NgbTooltipMocksModule],
            declarations: [
                TutorialGroupsTableComponent,
                TutorialGroupRowStubComponent,
                MockWrapperComponent,
                MockExtraColumnComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockComponent(FaIconComponent),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
            ],
            providers: [MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MockWrapperComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });
                component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo];
                component.course = course;
                fixture.detectChanges();
                tableInstance = component.tutorialGroupTableInstance;
                mockExtraColumns = component.mockExtraColumns.toArray();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should pass the tutorialGroup to the headers', () => {
        expect(tableInstance.tutorialGroups).toEqual([tutorialGroupOne, tutorialGroupTwo]);
        expect(tableInstance.course).toEqual(course);
        expect(mockExtraColumns).toHaveLength(2);
        mockExtraColumns.sort((a, b) => a.tutorialGroup!.id! - b.tutorialGroup!.id!);
        expect(mockExtraColumns[0].tutorialGroup).toEqual(tutorialGroupOne);
        expect(mockExtraColumns[1].tutorialGroup).toEqual(tutorialGroupTwo);
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
            imports: [NgbTooltipMocksModule],
            declarations: [
                TutorialGroupsTableComponent,
                TutorialGroupRowStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(FaIconComponent),
                MockRouterLinkDirective,
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockComponent(TutorialGroupUtilizationIndicatorComponent),
                MockComponent(FaIconComponent),
            ],
            providers: [MockProvider(SortService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TutorialGroupsTableComponent);
                component = fixture.componentInstance;
                tutorialGroupOne = generateExampleTutorialGroup({ id: 1 });
                tutorialGroupTwo = generateExampleTutorialGroup({ id: 2 });
                component.tutorialGroups = [tutorialGroupOne, tutorialGroupTwo];
                component.course = course;
                component.showIdColumn = true;
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

        component.ngOnChanges({ tutorialGroups: new SimpleChange([], [tutorialGroupOne, tutorialGroupTwo], true) });
        runOnPushChangeDetection(fixture);

        expect(fixture.nativeElement.querySelector('#language-column')).not.toBeNull();

        tutorialGroupOne.language = Language.ENGLISH;
        tutorialGroupTwo.language = Language.ENGLISH;

        component.ngOnChanges({ tutorialGroups: new SimpleChange([], [tutorialGroupOne, tutorialGroupTwo], true) });
        runOnPushChangeDetection(fixture);
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

        const sortService = TestBed.inject(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], 'id', false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });

    it('should call sort service with day and time', () => {
        component.sortingPredicate = 'dayAndTime';
        component.ascending = false;

        const sortService = TestBed.inject(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByMultipleProperties');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], ['tutorialGroupSchedule.dayOfWeek', 'tutorialGroupSchedule.startTime'], false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });

    it('should call sort service with capacity and number of registered users', () => {
        component.sortingPredicate = 'capacityAndRegistrations';
        component.ascending = false;

        const sortService = TestBed.inject(SortService);
        const sortServiceSpy = jest.spyOn(sortService, 'sortByMultipleProperties');

        component.sortRows();
        expect(sortServiceSpy).toHaveBeenCalledWith([tutorialGroupOne, tutorialGroupTwo], ['capacity', 'numberOfRegisteredUsers'], false);
        expect(sortServiceSpy).toHaveBeenCalledOnce();
    });
});
