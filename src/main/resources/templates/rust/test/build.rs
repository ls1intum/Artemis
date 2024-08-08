use std::ffi::OsStr;
use std::fs::read_to_string;
use std::path::{Component, Path};
use std::{fs, io};

use syn::{parse_file, ImplItem, Item, TraitItem, Type};

const SRC_DIR: &str = "assignment/src";

fn main() {
    println!("cargo::rerun-if-changed={SRC_DIR}");
    visit_dirs(Path::new(SRC_DIR), &process_file).unwrap();
}

fn visit_dirs<F: Fn(&Path)>(dir: &Path, cb: &F) -> io::Result<()> {
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            visit_dirs(&path, cb)?;
        } else {
            cb(&entry.path());
        }
    }
    Ok(())
}

fn process_file(path: &Path) {
    if path.extension() != Some(OsStr::new("rs")) {
        return;
    }

    let file_content = read_to_string(path).unwrap();

    let ast = parse_file(&file_content).unwrap();

    let module = path
        .strip_prefix(SRC_DIR)
        .unwrap()
        .with_extension("")
        .components()
        .map(Component::as_os_str)
        .collect::<Vec<_>>()
        .join(OsStr::new("_"));
    let module = module.to_str().unwrap();

    for item in ast.items {
        match item {
            Item::Enum(e) => {
                println!("cargo::rustc-cfg=structure_{module}_enum_{}", e.ident);

                for v in e.variants {
                    println!(
                        "cargo::rustc-cfg=structure_{module}_enum_{}_variant_{}",
                        e.ident, v.ident
                    );
                }
            }
            Item::Fn(f) => println!("cargo::rustc-cfg=structure_{module}_fn_{}", f.sig.ident),
            Item::Impl(impl_) => {
                let self_path = if let Type::Path(p) = *impl_.self_ty {
                    p
                } else {
                    continue;
                };

                let self_ident = if let Some(i) = self_path.path.get_ident() {
                    i
                } else {
                    continue;
                };

                if impl_.trait_.is_some() {
                    let trait_ident = if let Some(i) = impl_.trait_.as_ref().unwrap().1.get_ident()
                    {
                        i
                    } else {
                        continue;
                    };
                    println!(
                        "cargo::rustc-cfg=structure_{module}_impl_{trait_ident}_for_{self_ident}"
                    );
                    continue;
                }

                for item in impl_.items {
                    match item {
                        ImplItem::Const(c) => println!(
                            "cargo::rustc-cfg=structure_{module}_impl_{self_ident}_const_{}",
                            c.ident
                        ),
                        ImplItem::Fn(f) => println!(
                            "cargo::rustc-cfg=structure_{module}_impl_{self_ident}_fn_{}",
                            f.sig.ident
                        ),
                        ImplItem::Type(t) => println!(
                            "cargo::rustc-cfg=structure_{module}_impl_{self_ident}_type_{}",
                            t.ident
                        ),
                        _ => continue,
                    }
                }
            }
            Item::Struct(struct_) => {
                println!(
                    "cargo::rustc-cfg=structure_{module}_struct_{}",
                    struct_.ident
                );

                for field in struct_.fields {
                    if let Some(field_ident) = field.ident {
                        println!(
                            "cargo::rustc-cfg=structure_{module}_struct_{}_field_{field_ident}",
                            struct_.ident
                        );
                    }
                }
            }
            Item::Trait(trait_) => {
                println!("cargo::rustc-cfg=structure_{module}_trait_{}", trait_.ident);

                for item in trait_.items {
                    match item {
                        TraitItem::Const(c) => println!(
                            "cargo::rustc-cfg=structure_{module}_trait_{}_const_{}",
                            trait_.ident, c.ident
                        ),
                        TraitItem::Fn(f) => println!(
                            "cargo::rustc-cfg=structure_{module}_trait_{}_fn_{}",
                            trait_.ident, f.sig.ident
                        ),
                        TraitItem::Type(t) => println!(
                            "cargo::rustc-cfg=structure_{module}_trait_{}_type_{}",
                            trait_.ident, t.ident
                        ),
                        _ => continue,
                    }
                }
            }
            Item::Union(union_) => {
                println!("cargo::rustc-cfg=structure_{module}_union_{}", union_.ident);

                for field in union_.fields.named {
                    if let Some(field_ident) = field.ident {
                        println!(
                            "cargo::rustc-cfg=structure_{module}_union_{}_field_{field_ident}",
                            union_.ident
                        );
                    }
                }
            }
            _ => continue,
        }
    }
}
