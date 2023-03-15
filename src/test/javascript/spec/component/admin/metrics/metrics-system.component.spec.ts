import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArtemisTestModule } from '../../../test.module';
import { MetricsSystemComponent } from 'app/admin/metrics/blocks/metrics-system/metrics-system.component';

describe('MetricsSystemComponent', () => {
    let comp: MetricsSystemComponent;
    let fixture: ComponentFixture<MetricsSystemComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MetricsSystemComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MetricsSystemComponent);
                comp = fixture.componentInstance;
            });
    });

    it('should convert milliseconds to durations', () => {
        expect(comp.convertMillisecondsToDuration(5000)).toBe('5 seconds ');
        expect(comp.convertMillisecondsToDuration(10000)).toBe('10 seconds ');
        expect(comp.convertMillisecondsToDuration(60000)).toBe('1 minute ');
        expect(comp.convertMillisecondsToDuration(61000)).toBe('1 minute 1 second ');
        expect(comp.convertMillisecondsToDuration(62000)).toBe('1 minute 2 seconds ');
        expect(comp.convertMillisecondsToDuration(120000)).toBe('2 minutes ');
        expect(comp.convertMillisecondsToDuration(121000)).toBe('2 minutes 1 second ');
        expect(comp.convertMillisecondsToDuration(125000)).toBe('2 minutes 5 seconds ');
        expect(comp.convertMillisecondsToDuration(3600000)).toBe('1 hour ');
        expect(comp.convertMillisecondsToDuration(7200000)).toBe('2 hours ');
        expect(comp.convertMillisecondsToDuration(7261000)).toBe('2 hours 1 minute 1 second ');
        expect(comp.convertMillisecondsToDuration(7261000)).toBe('2 hours 1 minute 1 second ');
        expect(comp.convertMillisecondsToDuration(259200000)).toBe('3 days ');
        expect(comp.convertMillisecondsToDuration(15778476000)).toBe('6 months ');
        expect(comp.convertMillisecondsToDuration(14200920000000)).toBe('450 years ');
    });
});
