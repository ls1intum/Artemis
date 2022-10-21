import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { MonitoringCardComponent } from 'app/exam/monitoring/subpages/monitoring-card.component';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../../../test.module';
import { DebugElement } from '@angular/core';

describe('Monitoring Card Component', () => {
    let comp: MonitoringCardComponent;
    let fixture: ComponentFixture<MonitoringCardComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MonitoringCardComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MonitoringCardComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    afterEach(() => {
        // completely restore all fakes created through the sandbox
        jest.restoreAllMocks();
    });

    it.each`
        value
        ${'title'}
        ${'description'}
    `(
        "should render '$value' as provided",
        fakeAsync((param: { value: string }) => {
            setValue(comp, param.value);
            fixture.detectChanges();
            fixture.whenStable().then(() => {
                const titleElement = debugElement.query(By.css(`[data-testid="monitoring-card-${param.value}"]`));

                expect(titleElement).not.toBeNull();
                expect(titleElement.nativeElement.textContent).toEqual(param.value);
            });
        }),
    );
});

function setValue(comp: MonitoringCardComponent, value: string) {
    switch (value) {
        case 'title':
            comp.title = value;
            break;
        case 'description':
            comp.description = value;
            break;
    }
}
