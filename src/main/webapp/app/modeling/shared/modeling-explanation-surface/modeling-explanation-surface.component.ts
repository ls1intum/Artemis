import { Component, ElementRef, contentChild, input, signal, viewChild } from '@angular/core';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';

@Component({
    selector: 'jhi-modeling-explanation-surface',
    templateUrl: './modeling-explanation-surface.component.html',
    styleUrls: ['./modeling-explanation-surface.component.scss'],
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, ResizableDirective],
})
export class ModelingExplanationSurfaceComponent {
    readonly labelId = input.required<string>();
    readonly labelKey = input('artemisApp.modelingSubmission.explanationText');
    readonly icon = input<IconDefinition>(faCommentDots);
    readonly notchWidth = input(104);
    readonly minHeight = input(42);
    readonly maxHeight = input(320);

    private readonly surface = viewChild<ElementRef<HTMLElement>>('surface');
    private readonly autosize = contentChild(CdkTextareaAutosize);
    private readonly resizableContent = contentChild<ElementRef<HTMLElement>>('resizableContent');
    protected readonly manuallySized = signal(false);

    protected startManualResize(_size: ResizableSizeEvent): void {
        const content = this.resizableContent()?.nativeElement;
        content?.style.setProperty('height', '100%', 'important');
        content?.style.setProperty('max-height', 'none', 'important');
        this.manuallySized.set(true);
    }

    protected resetManualSize(): void {
        this.surface()?.nativeElement.style.removeProperty('height');
        const content = this.resizableContent()?.nativeElement;
        content?.style.removeProperty('height');
        content?.style.removeProperty('max-height');
        this.manuallySized.set(false);
        this.autosize()?.resizeToFitContent(true);
    }
}
