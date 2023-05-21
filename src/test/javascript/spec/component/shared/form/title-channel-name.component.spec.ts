import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';

import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { ArtemisTestModule } from '../../../test.module';

describe('TitleChannelNameComponent', () => {
    let component: TitleChannelNameComponent;
    let fixture: ComponentFixture<TitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [TitleChannelNameComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TitleChannelNameComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
