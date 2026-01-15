import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule, NgForm } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { TitleChannelNameComponent } from 'app/shared/form/title-channel-name/title-channel-name.component';
import { CustomNotIncludedInValidatorDirective } from '../../validators/custom-not-included-in-validator.directive';
import { MockDirective } from 'ng-mocks';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

const CHANNEL_NAME_PREFIX = '-- -!?-p --()';

describe('TitleChannelNameComponent', () => {
    let component: TitleChannelNameComponent;
    let fixture: ComponentFixture<TitleChannelNameComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [TitleChannelNameComponent, MockDirective(CustomNotIncludedInValidatorDirective)],
            providers: [NgForm, { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TitleChannelNameComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should display title and channel name input fields with correct content', fakeAsync(() => {
        fixture.componentRef.setInput('title', 'Test');
        fixture.componentRef.setInput('channelName', 'test');

        fixture.changeDetectorRef.detectChanges();
        tick();

        fixture.whenStable().then(() => {
            const titleInput = fixture.debugElement.query(By.css('#field_title'));
            expect(titleInput).not.toBeNull();
            expect(titleInput.nativeElement.value).toBe(component.title);

            const channelNameInput = fixture.debugElement.query(By.css('#field_channel_name'));
            expect(channelNameInput).not.toBeNull();
            expect(channelNameInput.nativeElement.value).toBe(component.channelName);
        });
    }));

    it('should only display title input field if channel name is hidden', () => {
        fixture.componentRef.setInput('hideChannelName', true);
        fixture.changeDetectorRef.detectChanges();

        const titleInput = fixture.debugElement.query(By.css('#field_title'));
        expect(titleInput).not.toBeNull();

        const channelNameInput = fixture.debugElement.query(By.css('#field_channel_name'));
        expect(channelNameInput).toBeNull();
    });

    it('should update channel name on title change', () => {
        fixture.componentRef.setInput('title', 'test');
        fixture.componentRef.setInput('channelName', 'test');
        fixture.changeDetectorRef.detectChanges();

        const newTitle = 'New 0123 @()[]{} !?.-_ $%& too long name that is more than 30 characters';
        const titleInput = fixture.debugElement.query(By.css('#field_title'));
        titleInput.nativeElement.value = newTitle;
        titleInput.nativeElement.dispatchEvent(new Event('input'));

        fixture.changeDetectorRef.detectChanges();

        expect(component.title()).toBe(newTitle);
        expect(component.channelName()).toBe('new-0123-too-long-name-that-is');
    });

    it('init prefix if undefined', () => {
        component.ngOnInit();

        expect(component.channelNamePrefix()).toBe('');
    });

    it('init channel name based on prefix and title', fakeAsync(() => {
        fixture.componentRef.setInput('title', 'Test');
        fixture.componentRef.setInput('channelNamePrefix', 'prefix-');

        component.ngOnInit();
        tick();

        expect(component.channelName()).toBe('prefix-test');
    }));

    it('init channel name based on prefix if title is undefined', fakeAsync(() => {
        fixture.componentRef.setInput('channelNamePrefix', 'prefix-');

        component.ngOnInit();
        tick();

        expect(component.channelName()).toBe('prefix-');
    }));

    it('remove special characters and trailing hyphens from channel name on init with non-empty title', fakeAsync(() => {
        fixture.componentRef.setInput('title', '-- -  t--=*+ -- ');
        fixture.componentRef.setInput('channelNamePrefix', CHANNEL_NAME_PREFIX);

        component.ngOnInit();
        tick();

        expect(component.channelName()).toBe('-p-t');
    }));

    it("don't remove trailing hyphens from channel name on init with empty title", fakeAsync(() => {
        fixture.componentRef.setInput('title', '');
        fixture.componentRef.setInput('channelNamePrefix', CHANNEL_NAME_PREFIX);

        component.ngOnInit();
        tick();

        expect(component.channelName()).toBe('-p-');
    }));

    it("don't remove trailing hyphens from channel name on init with undefined title", fakeAsync(() => {
        fixture.componentRef.setInput('title', undefined);
        fixture.componentRef.setInput('channelNamePrefix', CHANNEL_NAME_PREFIX + '-');

        component.ngOnInit();
        tick();

        expect(component.channelName()).toBe('-p-');
    }));

    it('remove trailing hyphens from channel name on title edit', () => {
        fixture.componentRef.setInput('channelNamePrefix', CHANNEL_NAME_PREFIX);

        component.updateTitle('--t--(%&');

        expect(component.channelName()).toBe('-p-t');
    });

    it("don't remove trailing hyphens from channel name on title edit if title empty", () => {
        fixture.componentRef.setInput('channelNamePrefix', CHANNEL_NAME_PREFIX);

        component.updateTitle('');

        expect(component.channelName()).toBe('-p-');
    });

    it("don't remove trailing hyphens from channel name on channel name edit", () => {
        fixture.componentRef.setInput('channelNamePrefix', CHANNEL_NAME_PREFIX + '-');

        component.formatChannelName('-p--t--');

        expect(component.channelName()).toBe('-p--t--');
    });

    it("don't init channel name if not allowed", () => {
        fixture.componentRef.setInput('title', 't');
        fixture.componentRef.setInput('channelNamePrefix', 'p-');
        fixture.componentRef.setInput('initChannelName', false);

        component.ngOnInit();

        expect(component.channelName()).toBeUndefined();
    });
});
