import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, ngMocks } from 'ng-mocks';
import { beforeEach, describe, expect, it } from 'vitest';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumLiveFilePickerComponent } from './tum-live-file-picker.component';

describe('TumLiveFilePickerComponent', () => {
    let fixture: ComponentFixture<TumLiveFilePickerComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [TumLiveFilePickerComponent] }).overrideComponent(TumLiveFilePickerComponent, {
            remove: { imports: [ArtemisTranslatePipe] },
            add: { imports: [MockPipe(ArtemisTranslatePipe, (value) => value ?? '')] },
        });
        fixture = TestBed.createComponent(TumLiveFilePickerComponent);
        fixture.detectChanges();
    });

    function choose(file: File): void {
        const input = ngMocks.find<HTMLInputElement>(fixture, 'input[type=file]').nativeElement;
        Object.defineProperty(input, 'files', { configurable: true, value: [file] });
        input.dispatchEvent(new Event('change'));
        fixture.detectChanges();
    }

    it.each(['video/mp4', 'application/octet-stream', ''])('accepts a non-empty MP4 with MIME type "%s"', (type) => {
        const file = new File(['mp4'], 'Lecture.MP4', { type });
        choose(file);
        expect(fixture.componentInstance.file()).toBe(file);
        expect(fixture.componentInstance.invalid()).toBe(false);
        expect(fixture.nativeElement.textContent).toContain(file.name);
    });

    it.each([new File([], 'empty.mp4'), new File(['slides'], 'slides.pdf'), new File(['slides'], 'slides.mp4', { type: 'application/pdf' })])(
        'rejects %s and allows clearing the selection',
        (file) => {
            choose(file);
            expect(fixture.componentInstance.file()).toBeUndefined();
            expect(fixture.componentInstance.invalid()).toBe(true);

            ngMocks.find(fixture, 'button[severity=danger]').nativeElement.click();
            expect(fixture.componentInstance.invalid()).toBe(false);
        },
    );
});
