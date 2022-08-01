import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { LiveStatisticsCardComponent } from 'app/exam/statistics/subpages/live-statistics-card.component';
import { By } from '@angular/platform-browser';
import { ArtemisTestModule } from '../../../../test.module';
import { DebugElement } from '@angular/core';

describe('Live Statistics Card Component', () => {
    let comp: LiveStatisticsCardComponent;
    let fixture: ComponentFixture<LiveStatisticsCardComponent>;
    let debugElement: DebugElement;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LiveStatisticsCardComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LiveStatisticsCardComponent);
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
                const titleElement = debugElement.query(By.css(`[data-testid="live-statistics-card-${param.value}"]`));

                expect(titleElement).not.toBeNull();
                expect(titleElement.nativeElement.textContent).toEqual(param.value);
            });
        }),
    );
});

function setValue(comp: LiveStatisticsCardComponent, value: string) {
    switch (value) {
        case 'title':
            comp.title = value;
            break;
        case 'description':
            comp.description = value;
            break;
    }
}
