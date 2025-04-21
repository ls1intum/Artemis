import { input, runInInjectionContext } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultHistoryComponent } from 'app/exercise/result-history/result-history.component';
import { MockPipe } from 'ng-mocks';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { Result } from 'app/exercise/shared/entities/result/result.model';

describe('ResultHistoryComponent', () => {
    let component: ResultHistoryComponent;
    let fixture: ComponentFixture<ResultHistoryComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ResultHistoryComponent, MockPipe(ArtemisDatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultHistoryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize with same rated results', () => {
        runInInjectionContext(TestBed, () => {
            component.results = input<Result[]>([
                { rated: true, id: 1 },
                { rated: true, id: 2 },
                { rated: true, id: 3 },
            ]);
        });
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1 },
            { rated: true, id: 2 },
            { rated: true, id: 3 },
        ]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        runInInjectionContext(TestBed, () => {
            component.results = input<Result[]>([
                { rated: false, id: 1 },
                { rated: false, id: 2 },
                { rated: false, id: 3 },
                { rated: false, id: 4 },
                { rated: false, id: 5 },
                { rated: false, id: 6 },
            ]);
        });
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: false, id: 2 },
            { rated: false, id: 3 },
            { rated: false, id: 4 },
            { rated: false, id: 5 },
            { rated: false, id: 6 },
        ]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeFalsy();
    });

    it('should initialize with mixed rated results', () => {
        runInInjectionContext(TestBed, () => {
            component.results = input<Result[]>([
                { rated: true, id: 1 },
                { rated: false, id: 2 },
                { rated: false, id: 3 },
            ]);
        });
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1 },
            { rated: false, id: 2 },
            { rated: false, id: 3 },
        ]);
        expect(component.showPreviousDivider).toBeFalse();
        expect(component.movedLastRatedResult).toBeFalsy();

        runInInjectionContext(TestBed, () => {
            component.results = input<Result[]>([
                { rated: true, id: 1 },
                { rated: false, id: 2 },
                { rated: false, id: 3 },
                { rated: false, id: 4 },
                { rated: false, id: 5 },
                { rated: false, id: 6 },
            ]);
        });
        component.ngOnChanges();
        expect(component.displayedResults).toEqual([
            { rated: true, id: 1 },
            { rated: false, id: 3 },
            { rated: false, id: 4 },
            { rated: false, id: 5 },
            { rated: false, id: 6 },
        ]);
        expect(component.showPreviousDivider).toBeTrue();
        expect(component.movedLastRatedResult).toBeTrue();
    });
});
