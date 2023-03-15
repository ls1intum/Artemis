import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArtemisTestModule } from '../../test.module';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';

describe('ResizeableContainerComponent', () => {
    let component: ResizeableContainerComponent;
    let fixture: ComponentFixture<ResizeableContainerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ResizeableContainerComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(ResizeableContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
