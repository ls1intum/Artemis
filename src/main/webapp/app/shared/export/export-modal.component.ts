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
    quoteStrings: CsvQuoteStrings;
    decimalSeparator: CsvDecimalSeparator;
}

@Component({
    selector: 'jhi-csv-export-modal',
    templateUrl: './export-modal.component.html',
    styleUrls: ['./export-modal.component.scss'],
})
export class ExportModalComponent implements OnInit {
    readonly CsvFieldSeparator = CsvFieldSeparator;
    readonly CsvQuoteStrings = CsvQuoteStrings;
    readonly CsvDecimalSeparator = CsvDecimalSeparator;

    activeTab = 1;
    options: CsvExportOptions;

    // Icons
    faBan = faBan;
    faDownload = faDownload;

    constructor(private activeModal: NgbActiveModal, private translateService: TranslateService) {}

    ngOnInit(): void {
        // set default csv export options based on the current language
        switch (this.translateService.currentLang) {
            case 'de':
                this.options = {
                    fieldSeparator: CsvFieldSeparator.SEMICOLON,
                    quoteStrings: CsvQuoteStrings.QUOTES_DOUBLE,
                    decimalSeparator: CsvDecimalSeparator.COMMA,
                };
                break;
            default:
                this.options = {
                    fieldSeparator: CsvFieldSeparator.COMMA,
                    quoteStrings: CsvQuoteStrings.QUOTES_DOUBLE,
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
        this.options.quoteStrings = quoteString;
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
