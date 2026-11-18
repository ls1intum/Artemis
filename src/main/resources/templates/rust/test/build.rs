use std::error::Error;
use std::ffi::OsStr;
use std::fs::read_to_string;
use std::path::{Component, Path};
use std::{fs, io};

use syn::{parse_file, FnArg, ImplItem, Item, TraitItem, Type, TypeParamBound};

const SRC_DIR: &str = "${studentWorkingDirectoryNoSlash}";

fn main() {
    println!("cargo::rerun-if-changed={SRC_DIR}");
    if let Err(err) = visit_dirs(Path::new(SRC_DIR), &process_file) {
        eprintln!("Failed to analyze submission: {err}");
    }
}

fn visit_dirs<F: Fn(&Path) -> Result<(), Box<dyn Error>>>(
    dir: &Path,
    cb: &F,
) -> Result<(), Box<dyn Error>> {
    for entry in fs::read_dir(dir).map_err(|e| {
        io::Error::new(
            e.kind(),
            format!("Failed to read directory {}: {}", dir.display(), e),
        )
    })? {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            visit_dirs(&path, cb)?;
        } else {
            cb(&entry.path())?;
        }
    }
    Ok(())
}

fn process_file(path: &Path) -> Result<(), Box<dyn Error>> {
    if path.extension() != Some(OsStr::new("rs")) {
        return Ok(());
    }

    let file_content = read_to_string(path)?;

    let ast = parse_file(&file_content)?;

    let module = path
        .strip_prefix(SRC_DIR)?
        .with_extension("")
        .components()
        .map(Component::as_os_str)
        .collect::<Vec<_>>()
        .join(OsStr::new("_"));
    let module = module.to_str().ok_or("invalid UTF-8 in path")?;

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

                let self_ident = if let Some(i) = self_path.path.segments.last().map(|s| &s.ident) {
                    i
                } else {
                    continue;
                };

                if impl_.trait_.is_some() {
                    let trait_ident = if let Some(i) = impl_
                        .trait_
                        .as_ref()
                        .unwrap()
                        .1
                        .segments
                        .last()
                        .map(|s| &s.ident)
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
                        ImplItem::Fn(f) => {
                            println!(
                                "cargo::rustc-cfg=structure_{module}_impl_{self_ident}_fn_{}",
                                f.sig.ident
                            );

                            if matches!(f.sig.inputs.first(), Some(&FnArg::Receiver(_))) {
                                println!(
                                    "cargo::rustc-cfg=structure_{module}_impl_{self_ident}_method_{}",
                                    f.sig.ident
                                );
                            }
                        }
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

                for supertrait in trait_.supertraits {
                    let supertrait = match supertrait {
                        TypeParamBound::Trait(supertrait) => supertrait,
                        _ => continue,
                    };
                    let supertrait = &supertrait.path.segments.last().unwrap().ident;
                    println!(
                        "cargo::rustc-cfg=structure_{module}_trait_{}_supertrait_{supertrait}",
                        trait_.ident
                    );
                }

                for item in trait_.items {
                    match item {
                        TraitItem::Const(c) => println!(
                            "cargo::rustc-cfg=structure_{module}_trait_{}_const_{}",
                            trait_.ident, c.ident
                        ),
                        TraitItem::Fn(f) => {
                            println!(
                                "cargo::rustc-cfg=structure_{module}_trait_{}_fn_{}",
                                trait_.ident, f.sig.ident
                            );

                            if matches!(f.sig.inputs.first(), Some(&FnArg::Receiver(_))) {
                                println!(
                                    "cargo::rustc-cfg=structure_{module}_trait_{}_method_{}",
                                    trait_.ident, f.sig.ident
                                );
                            }
                        }
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

    Ok(())
}
