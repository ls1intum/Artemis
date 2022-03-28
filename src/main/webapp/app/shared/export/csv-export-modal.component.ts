import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { faBan, faDownload } from '@fortawesome/free-solid-svg-icons';

export enum CsvFieldSeparator {
    TAB = '\t',
    COMMA = ',',
    SEMICOLON = ';',
    SPACE = ' ',
    PERIOD = '.',
}

export enum CsvQuoteStrings {
    DOUBLE_QUOTES = '\x22',
    SINGLE_QUOTES = '\x27',
    NONE = '',
}

export interface CsvExportOptions {
    fieldSeparator: CsvFieldSeparator;
    quoteStrings: CsvQuoteStrings;
}

@Component({
    selector: 'jhi-csv-export-modal',
    templateUrl: './csv-export-modal.component.html',
    styleUrls: ['./csv-export-modal.component.scss'],
})
export class CsvExportModalComponent implements OnInit {
    readonly CsvFieldSeparator = CsvFieldSeparator;
    readonly CsvQuoteStrings = CsvQuoteStrings;

    options: CsvExportOptions;
    locale: string;

    // Icons
    faBan = faBan;
    faDownload = faDownload;

    constructor(private activeModal: NgbActiveModal, private translateService: TranslateService) {
        this.locale = translateService.getBrowserLang() ?? 'en';
    }

    ngOnInit(): void {
        // set default csv export options not based on Artemis locale but on the user's system
        switch (this.locale) {
            case 'de':
                this.options = {
                    fieldSeparator: CsvFieldSeparator.SEMICOLON,
                    quoteStrings: CsvQuoteStrings.DOUBLE_QUOTES,
                };
                break;
            default:
                this.options = {
                    fieldSeparator: CsvFieldSeparator.COMMA,
                    quoteStrings: CsvQuoteStrings.DOUBLE_QUOTES,
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
        this.options.quoteStrings = quoteString;
    }

    /**
     * Dismisses the csv export options modal
     */
    cancel() {
        this.activeModal.dismiss();
    }

    /**
     * Closes the csv export modal and passes the selected options back
     */
    onFinish() {
        this.activeModal.close(this.options);
    }
}
