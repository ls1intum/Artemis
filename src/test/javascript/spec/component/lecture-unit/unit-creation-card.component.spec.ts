import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { UnitCreationCardComponent } from 'app/lecture/lecture-unit/lecture-unit-management/unit-creation-card/unit-creation-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DebounceClickDirective } from 'app/shared/directives/DebounceClickDirective';
import { By } from '@angular/platform-browser';

chai.use(sinonChai);
const expect = chai.expect;
describe('UnitCreationCardComponent', () => {
    let unitCreationCardComponentFixture: ComponentFixture<UnitCreationCardComponent>;
    let unitCreationCardComponent: UnitCreationCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [UnitCreationCardComponent, MockPipe(TranslatePipe), MockComponent(FaIconComponent), DebounceClickDirective],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                unitCreationCardComponentFixture = TestBed.createComponent(UnitCreationCardComponent);
                unitCreationCardComponent = unitCreationCardComponentFixture.componentInstance;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        unitCreationCardComponentFixture.detectChanges();
        expect(unitCreationCardComponent).to.be.ok;
    });

    it('should send the creation events when buttons are clicked', fakeAsync(() => {
        unitCreationCardComponentFixture.detectChanges();
        const buttons = unitCreationCardComponentFixture.debugElement.queryAll(By.css('button'));
        const spies = [];
        spies.push(sinon.spy(unitCreationCardComponent.createTextUnit, 'emit'));
        spies.push(sinon.spy(unitCreationCardComponent.createAttachmentUnit, 'emit'));
        spies.push(sinon.spy(unitCreationCardComponent.createExerciseUnit, 'emit'));
        spies.push(sinon.spy(unitCreationCardComponent.createVideoUnit, 'emit'));
        for (const button of buttons) {
            button.nativeElement.click();
            tick(500); // debounceClick
        }
        for (const spy of spies) {
            expect(spy).to.have.been.calledOnce;
        }
    }));
});
