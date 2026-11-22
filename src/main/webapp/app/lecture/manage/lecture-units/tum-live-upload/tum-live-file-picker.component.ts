import { ChangeDetectionStrategy, Component, ElementRef, input, model, viewChild } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DecimalPipe } from '@angular/common';

@Component({
    selector: 'jhi-tum-live-file-picker',
    imports: [ArtemisTranslatePipe, DecimalPipe, TumAetUiButtonDirective, TumAetUiMessageComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
        <div class="flex min-w-0 flex-wrap items-center gap-2">
            <input #picker type="file" accept=".mp4,video/mp4" data-testid="tum-live-upload-file" hidden [disabled]="disabled()" (change)="choose(picker)" />
            @if (file(); as selected) {
                <span class="min-w-0 break-all"
                    >{{ selected.name }}
                    <span class="text-muted-color"
                        >({{ (selected.size >= 1_000_000 ? selected.size / 1_000_000 : selected.size / 1000) | number: '1.0-2' }}
                        {{ selected.size >= 1_000_000 ? 'MB' : 'kB' }})</span
                    ></span
                >
            }
            @if (file() || invalid()) {
                <button tumAetUiButton type="button" severity="danger" variant="text" size="small" [disabled]="disabled()" (click)="clear()">
                    {{ 'artemisApp.tumLiveUpload.removeFile' | artemisTranslate }}
                </button>
            }
            <button #chooseButton tumAetUiButton type="button" variant="outlined" size="small" data-testid="tum-live-choose-file" [disabled]="disabled()" (click)="picker.click()">
                {{ 'artemisApp.tumLiveUpload.chooseMp4' | artemisTranslate }}
            </button>
        </div>
        @if (invalid()) {
            <tumaet-ui-message severity="error" [text]="'artemisApp.tumLiveUpload.invalidMp4' | artemisTranslate" />
        }
    `,
})
export class TumLiveFilePickerComponent {
    readonly disabled = input(false);
    readonly file = model<File>();
    readonly invalid = model(false);
    private readonly chooseButton = viewChild('chooseButton', { read: ElementRef<HTMLButtonElement> });

    protected choose(picker: HTMLInputElement): void {
        const file = picker.files?.[0];
        picker.value = '';
        if (!file) {
            return;
        }
        const valid = file.size > 0 && /\.mp4$/i.test(file.name) && (!file.type || file.type === 'video/mp4' || file.type === 'application/octet-stream');
        this.file.set(valid ? file : undefined);
        this.invalid.set(!valid);
    }

    protected clear(): void {
        this.file.set(undefined);
        this.invalid.set(false);
        this.chooseButton()?.nativeElement.focus();
    }
}
