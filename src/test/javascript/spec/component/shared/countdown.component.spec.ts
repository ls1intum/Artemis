import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../test.module';
import { CountdownComponent } from 'app/shared/countdown/countdown.component';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('Countdown Component', () => {
    let comp: CountdownComponent;
    let fixture: ComponentFixture<CountdownComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CountdownComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(ArtemisServerDateService), MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CountdownComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    /*
    use:
        const difference = Math.ceil(component.exam.startDate.diff(now, 'seconds') / 60);
        expect(component.timeUntilStart).toBe(difference + ' min');

        tick();
        jest.advanceTimersByTime(UI_RELOAD_TIME + 1); // simulate setInterval time passing

        const difference1 = Math.ceil(component.exam.startDate.diff(now1, 's') / 60);
        expect(component.timeUntilStart).toBe(difference1 + ' min');

        Test external date changes
     */
});
