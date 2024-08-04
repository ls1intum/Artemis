#![allow(dead_code)]

use std::io::Read;

use syn::{
    Fields, ImplItem, ImplItemFn,
    Item::{self, Impl, Struct, Trait},
    ItemImpl, ItemStruct, ItemTrait, TraitItem, TraitItemFn,
};

pub fn check_struct_names<'a, I: IntoIterator<Item = &'a str>>(items: &[Item], names: I) {
    for name in names {
        find_struct(items, name).unwrap();
    }
}

pub fn find_struct<'a>(items: &'a [Item], name: &str) -> Option<&'a ItemStruct> {
    find_by_name(
        items,
        name,
        |i| match i {
            Struct(s) => Some(s),
            _ => None,
        },
        |s| &s.ident,
    )
}

pub fn check_struct_field_names<'a, I: IntoIterator<Item = &'a str>>(fields: &Fields, names: I) {
    let p = match fields {
        Fields::Named(f) => &f.named,
        _ => panic!("the struct should have named fields"),
    };

    let field_names: Vec<_> = p.iter().map(|f| f.ident.as_ref().unwrap()).collect();

    for name in names {
        field_names
            .iter()
            .copied()
            .find(|&n| n == name)
            .unwrap_or_else(|| panic!("a field named \"{name}\" should be inside the struct"));
    }
}

pub fn find_impl<'a>(items: &'a [Item], name: &str) -> Option<&'a ItemImpl> {
    items.iter().find_map(|i| {
        let im = match i {
            Impl(im) => im,
            _ => return None,
        };
        let self_name = match im.self_ty.as_ref() {
            syn::Type::Path(p) => &p.path.segments.last().unwrap().ident,
            _ => return None,
        };

        if self_name != name {
            return None;
        }

        Some(im)
    })
}

pub fn find_impl_for<'a>(items: &'a [Item], name: &str, for_trait: &str) -> Option<&'a ItemImpl> {
    items.iter().find_map(|i| {
        let im = match i {
            Impl(im) => im,
            _ => return None,
        };
        let self_name = match im.self_ty.as_ref() {
            syn::Type::Path(p) => &p.path.segments.last().unwrap().ident,
            _ => return None,
        };
        let trait_name = match im.trait_.as_ref() {
            Some((_, path, _)) => &path.segments.last().unwrap().ident,
            _ => return None,
        };

        if self_name != name || trait_name != for_trait {
            return None;
        }

        Some(im)
    })
}

pub fn check_impl_function_names<'a, I: IntoIterator<Item = &'a str>>(
    items: &[ImplItem],
    names: I,
) {
    for name in names {
        find_impl_function(items, name).unwrap();
    }
}

pub fn find_impl_function<'a>(items: &'a [ImplItem], name: &str) -> Option<&'a ImplItemFn> {
    find_by_name(
        items,
        name,
        |i| match i {
            ImplItem::Fn(f) => Some(f),
            _ => None,
        },
        |f| &f.sig.ident,
    )
}

pub fn check_trait_names<'a, I: IntoIterator<Item = &'a str>>(items: &[Item], names: I) {
    for name in names {
        find_trait(items, name).unwrap();
    }
}

pub fn find_trait<'a>(items: &'a [Item], name: &str) -> Option<&'a ItemTrait> {
    find_by_name(
        items,
        name,
        |i| match i {
            Trait(t) => Some(t),
            _ => None,
        },
        |t| &t.ident,
    )
}

pub fn check_trait_function_names<'a, I: IntoIterator<Item = &'a str>>(
    items: &[TraitItem],
    names: I,
) {
    for name in names {
        find_trait_function(items, name).unwrap();
    }
}

pub fn find_trait_function<'a>(items: &'a [TraitItem], name: &str) -> Option<&'a TraitItemFn> {
    find_by_name(
        items,
        name,
        |i| match i {
            TraitItem::Fn(f) => Some(f),
            _ => None,
        },
        |f| &f.sig.ident,
    )
}

fn find_by_name<
    'a,
    Item,
    I: IntoIterator<Item = Item>,
    M: FnMut(Item) -> Option<R>,
    R,
    N: FnMut(&R) -> C,
    C: PartialEq<&'a str>,
>(
    items: I,
    name: &'a str,
    item_matcher: M,
    mut name_getter: N,
) -> Option<R> {
    items
        .into_iter()
        .filter_map(item_matcher)
        .find(|i| name_getter(i) == name)
}

pub fn parse_file<P: AsRef<std::path::Path>>(path: P) -> syn::File {
    let mut file = std::fs::File::open(path).unwrap();
    let mut content = String::new();
    file.read_to_string(&mut content).unwrap();
    syn::parse_file(&content).unwrap()
}
