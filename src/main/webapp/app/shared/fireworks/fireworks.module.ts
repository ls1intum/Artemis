import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FireworksComponent } from 'app/shared/fireworks/fireworks.component';

@NgModule({
    imports: [CommonModule],
    declarations: [FireworksComponent],
    exports: [FireworksComponent],
})
export class FireworksModule {}
