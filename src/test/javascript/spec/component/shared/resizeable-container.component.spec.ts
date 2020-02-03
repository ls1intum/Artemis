import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ArtemisSharedLibsModule } from 'app/shared';

describe('ResizeableContainerComponent', () => {
    let component: ResizeableContainerComponent;
    let fixture: ComponentFixture<ResizeableContainerComponent>;

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisSharedLibsModule],
            declarations: [ResizeableContainerComponent],
        }).compileComponents();
    }));

    beforeEach(() => {
        fixture = TestBed.createComponent(ResizeableContainerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
