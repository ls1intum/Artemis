import * as fs from 'fs';
import * as os from 'os';
import path from 'path';
import { Download, Page, expect } from '@playwright/test';
import { simpleGit } from 'simple-git';
import yauzl, { Entry, ZipFile } from 'yauzl';

/*
 * Reads the archives Artemis hands out, so an export test can assert what is actually inside one rather than only
 * that a file arrived. Everything an export promises is visible here: the entry names, the POSIX permissions the
 * entry records, whether an entry was deflated or stored, and - once extracted with those permissions restored -
 * whether the result is a Git repository that git itself considers clean.
 */

/** Compression method of a ZIP entry, as recorded in its local header. */
export const STORED = 0;

export const DEFLATED = 8;

export interface ArchiveEntry {
    /** Path inside the archive, always with `/` as separator. */
    name: string;
    isDirectory: boolean;
    /**
     * POSIX permission bits the entry records, e.g. 0o755. Zero when the archive stores no unix mode at all, which is
     * what `java.util.zip` produced before exports were taught to carry permissions.
     */
    unixMode: number;
    /** {@link STORED} or {@link DEFLATED}. */
    compressionMethod: number;
    uncompressedSize: number;
    compressedSize: number;
}

function openZip(zipFilePath: string): Promise<ZipFile> {
    return new Promise((resolve, reject) => {
        yauzl.open(zipFilePath, { lazyEntries: true }, (error, zipFile) => (error ? reject(error) : resolve(zipFile!)));
    });
}

function toArchiveEntry(entry: Entry): ArchiveEntry {
    return {
        name: entry.fileName,
        isDirectory: entry.fileName.endsWith('/'),
        // The unix mode lives in the high 16 bits of the external attributes; the low bits are DOS attributes.
        unixMode: (entry.externalFileAttributes >>> 16) & 0o777,
        compressionMethod: entry.compressionMethod,
        uncompressedSize: entry.uncompressedSize,
        compressedSize: entry.compressedSize,
    };
}

/**
 * Walks every entry of an archive. The callback may read the entry content; the walk continues once it resolves.
 */
async function forEachEntry(zipFilePath: string, onEntry: (entry: Entry, zipFile: ZipFile) => Promise<void>): Promise<void> {
    const zipFile = await openZip(zipFilePath);
    return new Promise((resolve, reject) => {
        zipFile.on('entry', (entry: Entry) => {
            onEntry(entry, zipFile)
                .then(() => zipFile.readEntry())
                .catch(reject);
        });
        zipFile.on('end', resolve);
        zipFile.on('error', reject);
        zipFile.readEntry();
    });
}

function readEntryContent(zipFile: ZipFile, entry: Entry): Promise<Buffer> {
    return new Promise((resolve, reject) => {
        zipFile.openReadStream(entry, (error, readStream) => {
            if (error) {
                reject(error);
                return;
            }
            const chunks: Buffer[] = [];
            readStream!.on('data', (chunk) => chunks.push(chunk as Buffer));
            readStream!.on('end', () => resolve(Buffer.concat(chunks)));
            readStream!.on('error', reject);
        });
    });
}

/**
 * Lists every entry of an archive without extracting it.
 *
 * @param zipFilePath the archive to read
 * @return one {@link ArchiveEntry} per entry, in the order the archive stores them
 */
export async function readArchiveEntries(zipFilePath: string): Promise<ArchiveEntry[]> {
    const entries: ArchiveEntry[] = [];
    await forEachEntry(zipFilePath, async (entry) => {
        entries.push(toArchiveEntry(entry));
    });
    return entries;
}

/**
 * Reads the content of one entry.
 *
 * @param zipFilePath the archive to read from
 * @param entryName   the exact entry name, as {@link readArchiveEntries} reports it
 * @return the entry content, or undefined when the archive has no such entry
 */
export async function readArchiveEntry(zipFilePath: string, entryName: string): Promise<Buffer | undefined> {
    let content: Buffer | undefined;
    await forEachEntry(zipFilePath, async (entry, zipFile) => {
        if (entry.fileName === entryName) {
            content = await readEntryContent(zipFile, entry);
        }
    });
    return content;
}

/**
 * Extracts an archive, restoring the POSIX permissions each entry records.
 *
 * Restoring them matters: git tracks the executable bit, so extracting without it makes every repository that ships
 * an executable file report a modification before anyone has touched the working tree - which is exactly the defect
 * these tests guard against, and it would otherwise be invisible because the archive itself is correct.
 *
 * @param zipFilePath the archive to extract
 * @param targetDir   the directory to extract into; created if it does not exist
 */
export async function extractArchive(zipFilePath: string, targetDir: string): Promise<void> {
    fs.mkdirSync(targetDir, { recursive: true });
    await forEachEntry(zipFilePath, async (entry, zipFile) => {
        const target = path.join(targetDir, entry.fileName);
        // The archives under test are produced by Artemis, but a traversal would silently write outside the fixture
        // directory, so it is refused rather than trusted.
        if (!path.resolve(target).startsWith(path.resolve(targetDir))) {
            throw new Error(`Refusing to extract ${entry.fileName} because it escapes ${targetDir}`);
        }
        if (entry.fileName.endsWith('/')) {
            fs.mkdirSync(target, { recursive: true });
            return;
        }
        fs.mkdirSync(path.dirname(target), { recursive: true });
        fs.writeFileSync(target, await readEntryContent(zipFile, entry));
        const { unixMode } = toArchiveEntry(entry);
        if (unixMode !== 0 && os.platform() !== 'win32') {
            fs.chmodSync(target, unixMode);
        }
    });
}

/**
 * Extracts one archive that is nested inside another and returns the path of the extracted copy. An Artemis course
 * archive holds one archive per repository, so reaching a repository means opening two.
 *
 * @param zipFilePath the outer archive
 * @param entryName   the name of the nested archive inside it
 * @param targetDir   the directory to place the nested archive in
 * @return the path of the extracted nested archive
 */
export async function extractNestedArchive(zipFilePath: string, entryName: string, targetDir: string): Promise<string> {
    const content = await readArchiveEntry(zipFilePath, entryName);
    expect(content, `the archive must contain the nested archive ${entryName}`).toBeDefined();
    fs.mkdirSync(targetDir, { recursive: true });
    const nestedPath = path.join(targetDir, path.basename(entryName));
    fs.writeFileSync(nestedPath, content!);
    return nestedPath;
}

/**
 * Saves a download to a temporary file and returns where it went, together with the name the browser was offered.
 */
export async function saveDownload(download: Download): Promise<{ filePath: string; suggestedFilename: string }> {
    const suggestedFilename = download.suggestedFilename();
    const filePath = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-export-')), suggestedFilename);
    await download.saveAs(filePath);
    return { filePath, suggestedFilename };
}

/**
 * Runs an action that triggers a download and returns the downloaded file.
 *
 * @param page   the page the download originates from
 * @param action the interaction that starts the download, e.g. clicking an export button
 */
export async function downloadArchive(page: Page, action: () => Promise<void>): Promise<{ filePath: string; suggestedFilename: string }> {
    const downloadPromise = page.waitForEvent('download');
    await action();
    return saveDownload(await downloadPromise);
}

/**
 * Asserts that a directory is a Git repository that git itself is happy with: the working tree matches the index it
 * ships with, and the history is walkable.
 *
 * @param repositoryDir the extracted repository
 * @return the number of commits the history holds
 */
export async function expectUsableGitRepository(repositoryDir: string): Promise<number> {
    expect(fs.existsSync(path.join(repositoryDir, '.git')), `${repositoryDir} must contain a .git directory`).toBe(true);
    const git = simpleGit(repositoryDir);
    const status = await git.status();
    expect(status.files, `the extracted repository ${repositoryDir} must have a clean working tree`).toEqual([]);
    const log = await git.log();
    expect(log.total, `the extracted repository ${repositoryDir} must have a walkable history`).toBeGreaterThan(0);
    return log.total;
}

/**
 * Returns the entry of an archive with the given name, failing the test with the full entry listing when it is
 * missing. The listing is what makes a failure diagnosable, because an export defect usually shows up as a slightly
 * different name rather than an empty archive.
 */
export function expectEntry(entries: ArchiveEntry[], name: string): ArchiveEntry {
    const entry = entries.find((candidate) => candidate.name === name);
    expect(entry, `the archive must contain ${name}, but it holds:\n${entries.map((candidate) => candidate.name).join('\n')}`).toBeDefined();
    return entry!;
}

/**
 * Returns the single entry whose name ends with the given suffix, failing when there is none or more than one.
 * Repository exports name their entries after the participation, so a test usually knows the tail of a name but not
 * the generated prefix.
 */
export function expectSingleEntryEndingWith(entries: ArchiveEntry[], suffix: string): ArchiveEntry {
    const matching = entries.filter((candidate) => candidate.name.endsWith(suffix));
    expect(
        matching.map((candidate) => candidate.name),
        `exactly one entry must end with ${suffix}`,
    ).toHaveLength(1);
    return matching[0];
}
