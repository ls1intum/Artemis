import { Component, Input } from '@angular/core';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';

@Component({
    selector: 'jhi-textarea-counter',
    templateUrl: './text-area-counter.component.html',
    providers: [],
})
export class TextAreaCounterComponent {
    @Input() maxLength: number;
    @Input() content?: string;
    @Input() visible?: boolean;

    constructor(private stringCountService: StringCountService) {}

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.content);
    }
}
