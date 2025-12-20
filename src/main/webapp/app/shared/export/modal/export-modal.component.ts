import { Component, OnInit, inject } from '@angular/core';
import { NgbActiveModal, NgbNav, NgbNavContent, NgbNavItem, NgbNavLink, NgbNavLinkBase, NgbNavOutlet } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { faBan, faDownload } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { KeyValuePipe, NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';

export enum CsvFieldSeparator {
    TAB = '\t',
    COMMA = ',',
    SEMICOLON = ';',
    SPACE = ' ',
    PERIOD = '.',
}

export enum CsvQuoteStrings {
    QUOTES_DOUBLE = '\x22',
    QUOTES_SINGLE = '\x27',
    NONE = '',
}

export enum CsvDecimalSeparator {
    COMMA = ',',
    PERIOD = '.',
}

export interface CsvExportOptions {
    fieldSeparator: CsvFieldSeparator;
    quoteStrings: boolean;
    quoteCharacter: CsvQuoteStrings;
    decimalSeparator: CsvDecimalSeparator;
}

@Component({
    selector: 'jhi-csv-export-modal',
    templateUrl: './export-modal.component.html',
    styleUrls: ['./export-modal.component.scss'],
    imports: [
        FormsModule,
        TranslateDirective,
        NgbNav,
        NgbNavItem,
        NgbNavLink,
        NgbNavLinkBase,
        NgbNavContent,
        NgClass,
        NgbNavOutlet,
        FaIconComponent,
        KeyValuePipe,
        ArtemisTranslatePipe,
    ],
})
export class ExportModalComponent implements OnInit {
    private activeModal = inject(NgbActiveModal);
    private translateService = inject(TranslateService);

    readonly CsvFieldSeparator = CsvFieldSeparator;
    readonly CsvQuoteStrings = CsvQuoteStrings;
    readonly CsvDecimalSeparator = CsvDecimalSeparator;

    activeTab = 1;
    options: CsvExportOptions;

    // Icons
    faBan = faBan;
    faDownload = faDownload;

    ngOnInit(): void {
        // set default csv export options based on the current language
        switch (this.translateService.getCurrentLang()) {
            case 'de':
                this.options = {
                    fieldSeparator: CsvFieldSeparator.SEMICOLON,
                    quoteStrings: true,
                    quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
                    decimalSeparator: CsvDecimalSeparator.COMMA,
                };
                break;
            default:
                this.options = {
                    fieldSeparator: CsvFieldSeparator.COMMA,
                    quoteStrings: true,
                    quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
                    decimalSeparator: CsvDecimalSeparator.PERIOD,
                };
        }
    }

    /**
     * Sets the field separator for the csv export options
     * @param separator chosen separator which is used to separate the fields in the generated csv file
     */
    setCsvFieldSeparator(separator: CsvFieldSeparator) {
        this.options.fieldSeparator = separator;
    }

    /**
     * Sets the quote string for the csv export options
     * @param quoteString chosen quoteString option which is used to quote strings in the generated csv file
     */
    setCsvQuoteString(quoteString: CsvQuoteStrings) {
        this.options.quoteCharacter = quoteString;
        this.options.quoteStrings = quoteString !== CsvQuoteStrings.NONE;
    }

    /**
     * Sets the decimal separator for the csv export options
     * @param separator chosen decimal separator which is used in the generated csv file
     */
    setCsvDecimalSeparator(separator: CsvDecimalSeparator) {
        this.options.decimalSeparator = separator;
    }

    /**
     * Dismisses the csv export options modal
     */
    cancel() {
        this.activeModal.dismiss();
    }

    /**
     * Closes the export modal and passes the selected csv options back in case the active page is not set to 1.
     */
    onFinish() {
        if (this.activeTab === 1) {
            // Excel export
            this.activeModal.close();
        } else {
            // CSV export
            this.activeModal.close(this.options);
        }
    }
}
