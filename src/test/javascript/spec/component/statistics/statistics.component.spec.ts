import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { StatisticsComponent } from 'app/admin/statistics/statistics.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { SpanType } from 'app/entities/statistics.model';
import { provideRouter } from '@angular/router';

describe('StatisticsComponent', () => {
    let fixture: ComponentFixture<StatisticsComponent>;
    let component: StatisticsComponent;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [
                StatisticsComponent,
                MockComponent(StatisticsGraphComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [provideRouter([]), { provide: LocalStorageService, useClass: MockSyncStorage }, { provide: SessionStorageService, useClass: MockSyncStorage }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StatisticsComponent);
                component = fixture.componentInstance;
            });
    }));

    afterEach(fakeAsync(() => {
        jest.clearAllMocks();
    }));

    it('should initialize', fakeAsync(() => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    }));

    it('should click Month button', fakeAsync(() => {
        const tabSpy = jest.spyOn(component, 'onTabChanged');
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#option3');
        button.click();

        tick();
        expect(tabSpy).toHaveBeenCalledOnce();
        expect(component.currentSpan).toEqual(SpanType.MONTH);
        tabSpy.mockRestore();
    }));
});
