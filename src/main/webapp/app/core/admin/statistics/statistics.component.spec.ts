import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { StatisticsComponent } from 'app/core/admin/statistics/statistics.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { SpanType } from 'app/exercise/shared/entities/statistics.model';
import { provideRouter } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('StatisticsComponent', () => {
    let fixture: ComponentFixture<StatisticsComponent>;
    let component: StatisticsComponent;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            declarations: [StatisticsComponent, MockComponent(StatisticsGraphComponent), MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [provideRouter([]), LocalStorageService, SessionStorageService, { provide: TranslateService, useClass: MockTranslateService }],
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
