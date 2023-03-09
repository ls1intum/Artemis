import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { StatisticsComponent } from 'app/admin/statistics/statistics.component';
import { SpanType } from 'app/entities/statistics.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../../test.module';

describe('StatisticsComponent', () => {
    let fixture: ComponentFixture<StatisticsComponent>;
    let component: StatisticsComponent;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([])],
            declarations: [
                StatisticsComponent,
                MockComponent(StatisticsGraphComponent),
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
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
