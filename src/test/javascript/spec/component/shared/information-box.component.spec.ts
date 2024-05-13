/* tslint:disable:no-unused-variable */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';

import { InformationBoxComponent } from '../../../../../main/webapp/app/shared/information-box/information-box.component';

describe('InformationBoxComponent', () => {
    let component: InformationBoxComponent;
    let fixture: ComponentFixture<InformationBoxComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            declarations: [InformationBoxComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(InformationBoxComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
