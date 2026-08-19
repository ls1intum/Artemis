import { strToU8, unzipSync, zipSync } from 'fflate';

/**
 * Minimal ZIP helpers (building and reading archives) built on `fflate`.
 */

/** A map of file name to its raw bytes, as produced by {@link readZipEntries}. */
export type ZipEntries = Record<string, Uint8Array<ArrayBuffer>>;

/**
 * Converts the supported file contents into the byte array `fflate` expects. Strings are UTF-8
 * encoded; Blobs/Files are read asynchronously.
 */
async function toUint8Array(content: Blob | string | Uint8Array): Promise<Uint8Array> {
    if (typeof content === 'string') {
        return strToU8(content);
    }
    if (content instanceof Uint8Array) {
        return content;
    }
    return new Uint8Array(await content.arrayBuffer());
}

/**
 * Accumulates files and produces a ZIP archive as a Blob. Files are queued synchronously via
 * {@link ZipBuilder.file} and only read/compressed when {@link generateBlob} is called.
 */
export class ZipBuilder {
    private readonly entries: { name: string; content: Blob | string | Uint8Array }[] = [];

    /** Queues a file for inclusion in the archive. */
    file(name: string, content: Blob | string | Uint8Array): void {
        this.entries.push({ name, content });
    }

    /** Builds the ZIP archive and returns it as a `application/zip` Blob. */
    async generateBlob(): Promise<Blob> {
        const files: Record<string, Uint8Array> = {};
        for (const entry of this.entries) {
            files[entry.name] = await toUint8Array(entry.content);
        }
        return new Blob([zipSync(files)], { type: 'application/zip' });
    }
}

/**
 * Reads a ZIP Blob/File into a map of file name to raw bytes. Directory entries (names ending with
 * `/`) are omitted, so the map contains only the actual files.
 */
export async function readZipEntries(file: Blob): Promise<ZipEntries> {
    const data = new Uint8Array(await file.arrayBuffer());
    const entries = unzipSync(data);
    const files: ZipEntries = {};
    for (const [name, bytes] of Object.entries(entries)) {
        if (!name.endsWith('/')) {
            files[name] = bytes;
        }
    }
    return files;
}
