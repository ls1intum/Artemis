/**
 * Registers a curated set of languages on the shared `highlight.js/lib/core` instance.
 *
 * The default `highlight.js` entry point bundles ~190 language grammars (~940 KB) and was the single
 * largest contributor to the eager `main.js` bundle. We import the bare core and register only the
 * languages Artemis actually renders in fenced code blocks: every language from the server-side
 * `ProgrammingLanguage` enum plus the common documentation/config languages used in markdown
 * (instructions, posts, lectures).
 *
 * `highlight.js/lib/core` exports a singleton, so the registrations performed here apply to every other
 * module that imports `highlight.js/lib/core` (e.g. {@link file://./markdown.conversion.util.ts}).
 *
 * Effect on behavior: explicitly tagged code blocks whose language is not registered fall back to
 * escaped HTML (unchanged guard in `highlightWithHljs`), and `highlightAuto` auto-detects only among the
 * languages registered below.
 */
import hljs from 'highlight.js/lib/core';

import ada from 'highlight.js/lib/languages/ada';
import bash from 'highlight.js/lib/languages/bash';
import c from 'highlight.js/lib/languages/c';
import cpp from 'highlight.js/lib/languages/cpp';
import csharp from 'highlight.js/lib/languages/csharp';
import css from 'highlight.js/lib/languages/css';
import dart from 'highlight.js/lib/languages/dart';
import diff from 'highlight.js/lib/languages/diff';
import dockerfile from 'highlight.js/lib/languages/dockerfile';
import go from 'highlight.js/lib/languages/go';
import haskell from 'highlight.js/lib/languages/haskell';
import ini from 'highlight.js/lib/languages/ini';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import kotlin from 'highlight.js/lib/languages/kotlin';
import markdown from 'highlight.js/lib/languages/markdown';
import matlab from 'highlight.js/lib/languages/matlab';
import ocaml from 'highlight.js/lib/languages/ocaml';
import php from 'highlight.js/lib/languages/php';
import plaintext from 'highlight.js/lib/languages/plaintext';
import powershell from 'highlight.js/lib/languages/powershell';
import python from 'highlight.js/lib/languages/python';
import r from 'highlight.js/lib/languages/r';
import ruby from 'highlight.js/lib/languages/ruby';
import rust from 'highlight.js/lib/languages/rust';
import scss from 'highlight.js/lib/languages/scss';
import sql from 'highlight.js/lib/languages/sql';
import swift from 'highlight.js/lib/languages/swift';
import typescript from 'highlight.js/lib/languages/typescript';
import vhdl from 'highlight.js/lib/languages/vhdl';
import x86asm from 'highlight.js/lib/languages/x86asm';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';

/**
 * Maps the highlight.js grammar name to its imported definition. The key is the canonical language name;
 * highlight.js exposes the usual aliases automatically (e.g. `js`, `ts`, `py`, `sh`, `c++`, `c#`, `yml`).
 */
const languages = {
    ada,
    bash,
    c,
    cpp,
    csharp,
    css,
    dart,
    diff,
    dockerfile,
    go,
    haskell,
    ini,
    java,
    javascript,
    json,
    kotlin,
    markdown,
    matlab,
    ocaml,
    php,
    plaintext,
    powershell,
    python,
    r,
    ruby,
    rust,
    scss,
    sql,
    swift,
    typescript,
    vhdl,
    x86asm,
    xml,
    yaml,
};

for (const [name, definition] of Object.entries(languages)) {
    hljs.registerLanguage(name, definition);
}

export default hljs;
