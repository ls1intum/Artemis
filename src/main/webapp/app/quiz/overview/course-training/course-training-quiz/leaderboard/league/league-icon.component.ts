import { Component, effect, input, signal } from '@angular/core';

@Component({
    selector: 'jhi-league-icon',
    templateUrl: './league-icon.component.html',
})
export class LeagueIconComponent {
    class = input<string>('');
    league = input<string>('');
    size = signal('50');

    constructor() {
        effect(() => {
            this.size.set(this.class().includes('small-icon') ? '30' : '50');
        });
    }
}
