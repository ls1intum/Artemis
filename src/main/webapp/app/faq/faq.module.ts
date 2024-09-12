import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { FAQComponent } from 'app/faq/faq.component';
import { faqRoutes } from 'app/faq/faq.routes';
import { FAQUpdateComponent } from 'app/faq/faq-update.component';
const ENTITY_STATES = [...faqRoutes];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), FAQComponent, FAQUpdateComponent],
    exports: [FAQComponent, FAQUpdateComponent],
})
export class ArtemisFAQModule {}
