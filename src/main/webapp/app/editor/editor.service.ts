import { Injectable } from '@angular/core';
import { SERVER_API_URL } from '../app.constants';
import { HttpClient, HttpParameterCodec, HttpParams } from '@angular/common/http';

@Injectable()
export class EditorService {

    private resourceUrl =  SERVER_API_URL + 'api/plantuml';
    private encoder: HttpParameterCodec;

    constructor(private http: HttpClient) {
        this.encoder = new HttpUrlCustomEncoder();
    }

    /**
     * @function getPlantUmlImage
     * @param plantUml definition obtained by parsing the README markdown file
     * @desc Requests the plantuml png file as arraybuffer and converts it to base64
     */
    getPlantUmlImage(plantUml: string) {
        return this.http.get(`${this.resourceUrl}/png`, { params: new HttpParams({encoder: this.encoder}).set('plantuml', plantUml), responseType: 'arraybuffer'})
            .map(res => this.convertPlantUmlResponseToBase64(res));
    }

    private convertPlantUmlResponseToBase64(res) {
        // TODO: cache result => https://nrempel.com/guides/angular-httpclient-httpinterceptor-cache-requests/

        // TODO: check if this works
        // response => Buffer.from(response.data, 'binary').toString('base64')

        console.log('convertPlantUmlResponseToBase64', res);
        console.log('Buffer.from() method', Buffer.from(res, 'binary').toString('base64'));

        const arr = new Uint8Array(res);
        const chunk = 5000;
        let raw = '';
        let i, j, subArray;

        for (i = 0, j = arr.length; i < j; i += chunk) {
            subArray = arr.subarray(i, i + chunk);
            raw += String.fromCharCode.apply(null, subArray);
        }

        console.log(arr, raw, btoa(raw));

        return btoa(raw);
    }
}

/**
 * @class HttpUrlCustomEncoder
 * @desc Custom HttpParamEncoder implementation which defaults to using encodeURIComponent to encode params
 */
export class HttpUrlCustomEncoder implements HttpParameterCodec {
    encodeKey(k: string): string { return encodeURIComponent(k); }

    encodeValue(v: string): string { return encodeURIComponent(v); }

    decodeKey(k: string): string { return decodeURIComponent(k); }

    decodeValue(v: string) { return decodeURIComponent(v); }
}
