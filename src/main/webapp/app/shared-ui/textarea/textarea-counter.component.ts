import { Component, computed, inject, input } from '@angular/core';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-textarea-counter',
    templateUrl: './textarea-counter.component.html',
    providers: [],
    imports: [ArtemisTranslatePipe],
})
export class TextareaCounterComponent {
    private stringCountService = inject(StringCountService);

    readonly maxLength = input.required<number>();
    readonly content = input<string>();
    readonly visible = input<boolean>();

    readonly characterCount = computed(() => this.stringCountService.countCharacters(this.content()));
}
