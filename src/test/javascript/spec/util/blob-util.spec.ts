import {
    arrayBufferToBinaryString,
    base64StringToBlob,
    binaryStringToArrayBuffer,
    blobToBase64String,
    blobToBinaryString,
    createBlob,
    objectToJsonBlob,
} from 'app/utils/blob-util';

describe('BlobUtil', () => {
    describe('createBlob', () => {
        it('should create blob from no parts', () => {
            const blob = createBlob([]);
            expect(blob.size).toBe(0);
            expect(blob.type).toBe('');
        });

        it('should create a blob from an array', () => {
            const parts = [new Uint8Array([1, 2, 3])];
            const blob = createBlob(parts);
            expect(blob.size).toBe(3);
            expect(blob.type).toBe('');
        });

        it('should create a blob from an array with content type', () => {
            const parts = [new Uint8Array([1, 2, 3])];
            const blob = createBlob(parts, { type: 'text/plain' });
            expect(blob.size).toBe(3);
            expect(blob.type).toBe('text/plain');
        });

        it('should create a blob from an array with content type and string property', () => {
            const parts = [new Uint8Array([1, 2, 3])];
            const blob = createBlob(parts, 'text/plain');
            expect(blob.size).toBe(3);
            expect(blob.type).toBe('text/plain');
        });
    });

    describe('objectToJsonBlob', () => {
        it('should convert empty object correctly', async () => {
            const blob = objectToJsonBlob({});
            expect(blob.type).toBe('application/json');
            const string = await blobToBinaryString(blob);
            expect(string).toBe('{}');
        });

        it('should convert object with content correctly', async () => {
            const blob = objectToJsonBlob({
                foo: 'bar',
                bar: 'foo',
            });
            expect(blob.type).toBe('application/json');
            const string = await blobToBinaryString(blob);
            expect(string).toBe('{"foo":"bar","bar":"foo"}');
        });
    });

    describe('blobToBinaryString', () => {
        it('should return an empty string for an empty blob', async () => {
            const blob = new Blob([]);
            const binary = await blobToBinaryString(blob);
            expect(binary).toBe('');
        });

        it('should return the correct binary string for a blob with content', async () => {
            const blob = new Blob(['Hello World!']);
            const binary = await blobToBinaryString(blob);
            expect(binary).toBe('Hello World!');
        });
    });

    describe('base64StringToBlob', () => {
        it('should return a blob with the correct contents for a base64 string with a length of 0', () => {
            const base64 = '';
            const blob = base64StringToBlob(base64);
            expect(blob.size).toBe(0);
        });

        it('should return a blob with the correct contents', async () => {
            const base64 = 'SGVsbG8gV29ybGQh';
            const blob = base64StringToBlob(base64);
            expect(blob.size).toBe(12);
        });
    });

    describe('blobToBase64String', () => {
        it('should return an empty string for an empty blob', async () => {
            const blob = new Blob([]);
            const binary = await blobToBase64String(blob);
            expect(binary).toBe('');
        });

        it('should return the correct binary string for a blob with content', async () => {
            const blob = new Blob(['Hello World!']);
            const binary = await blobToBase64String(blob);
            expect(binary).toBe('SGVsbG8gV29ybGQh');
        });
    });

    describe('arrayBufferToBinaryString', () => {
        it('should return an empty string for an empty array buffer', () => {
            const emptyArrayBuffer = new ArrayBuffer(0);
            const binary = arrayBufferToBinaryString(emptyArrayBuffer);
            expect(binary).toBe('');
        });

        it('should return the correct binary string for an array buffer with content', () => {
            const arrayBuffer = new ArrayBuffer(12);
            const array = new Uint8Array(arrayBuffer);
            array.set([72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33]);
            const binary = arrayBufferToBinaryString(arrayBuffer);
            expect(binary).toBe('Hello World!');
        });
    });

    describe('binaryStringToArrayBuffer', () => {
        it('should return an array buffer with the correct contents for a binary string with a length of 0', () => {
            const binary = '';
            const arrayBuffer = binaryStringToArrayBuffer(binary);
            const bytes = new Uint8Array(arrayBuffer);
            expect(bytes).toBeEmpty();
        });

        it('should return an array buffer with the correct contents', () => {
            const binary = 'Hello World!';
            const arrayBuffer = binaryStringToArrayBuffer(binary);
            const bytes = new Uint8Array(arrayBuffer);
            expect(bytes).toEqual(new Uint8Array([72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33]));
        });
    });
});
