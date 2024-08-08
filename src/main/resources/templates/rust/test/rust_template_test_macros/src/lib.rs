//! Compile-time source code reflection
//!
//! Use this for conditional compilation.

use proc_macro::TokenStream;

use quote::{format_ident, quote, ToTokens};
use syn::{parse_macro_input, Ident, Item, ItemFn, Path};

#[proc_macro_attribute]
pub fn require_struct(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_struct)
}

#[proc_macro_attribute]
pub fn require_struct_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_struct)
}

#[proc_macro_attribute]
pub fn require_trait(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_trait)
}

#[proc_macro_attribute]
pub fn require_trait_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_trait)
}

#[proc_macro_attribute]
pub fn require_enum(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_enum)
}

#[proc_macro_attribute]
pub fn require_enum_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_enum)
}

#[proc_macro_attribute]
pub fn require_function(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_function)
}

#[proc_macro_attribute]
pub fn require_function_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_function)
}

#[proc_macro_attribute]
pub fn require_impl_for_trait(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_impl_for_trait)
}

#[proc_macro_attribute]
pub fn require_impl_for_trait_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_impl_for_trait)
}

#[proc_macro_attribute]
pub fn require_impl_function(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_impl_function)
}

#[proc_macro_attribute]
pub fn require_impl_function_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_impl_function)
}

#[proc_macro_attribute]
pub fn require_impl_const(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_impl_const)
}

#[proc_macro_attribute]
pub fn require_impl_const_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_impl_const)
}

#[proc_macro_attribute]
pub fn require_impl_type(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_impl_type)
}

#[proc_macro_attribute]
pub fn require_impl_type_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_impl_type)
}

#[proc_macro_attribute]
pub fn require_trait_function(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_trait_function)
}

#[proc_macro_attribute]
pub fn require_trait_function_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_trait_function)
}

#[proc_macro_attribute]
pub fn require_trait_const(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_trait_const)
}

#[proc_macro_attribute]
pub fn require_trait_const_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_trait_const)
}

#[proc_macro_attribute]
pub fn require_trait_type(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_item(attr, item, make_cfg_trait_type)
}

#[proc_macro_attribute]
pub fn require_trait_type_or_fail(attr: TokenStream, item: TokenStream) -> TokenStream {
    require_for_function(attr, item, make_cfg_trait_type)
}

fn require_for_item<F: FnOnce(Path) -> Ident>(
    attr: TokenStream,
    item: TokenStream,
    make_cfg: F,
) -> TokenStream {
    let path = parse_macro_input!(attr as Path);
    let original_item = parse_macro_input!(item as Item);

    let cfg = make_cfg(path);

    quote! (
        #[cfg(#cfg)]
        #original_item
    )
    .into()
}

fn require_for_function<F: FnOnce(Path) -> Ident>(
    attr: TokenStream,
    item: TokenStream,
    make_cfg: F,
) -> TokenStream {
    let path = parse_macro_input!(attr as Path);
    let original_fn = parse_macro_input!(item as ItemFn);

    let failure_message = format!("missing {}", path.to_token_stream());

    let cfg = make_cfg(path);

    let ItemFn {
        attrs,
        vis,
        sig,
        block,
    } = original_fn;

    quote!(
        #(#attrs)*
        #vis #sig {
            #[cfg(not(#cfg))]
            panic!(#failure_message);
            #[cfg(#cfg)]
            #block
        }
    )
    .into()
}

fn make_cfg_struct(path: Path) -> Ident {
    let (module_path, struct_name) = split_path(path);
    format_ident!("structure_{module_path}_struct_{struct_name}")
}

fn make_cfg_trait(path: Path) -> Ident {
    let (module_path, trait_name) = split_path(path);
    format_ident!("structure_{module_path}_trait_{trait_name}")
}

fn make_cfg_enum(path: Path) -> Ident {
    let (module_path, enum_name) = split_path(path);
    format_ident!("structure_{module_path}_enum_{enum_name}")
}

fn make_cfg_function(path: Path) -> Ident {
    let (module_path, function_name) = split_path(path);
    format_ident!("structure_{module_path}_fn_{function_name}")
}

fn make_cfg_impl_for_trait(path: Path) -> Ident {
    let (module_path, self_type, trait_name) = split_path2(path);
    format_ident!("structure_{module_path}_impl_{trait_name}_for_{self_type}")
}

fn make_cfg_impl_function(path: Path) -> Ident {
    let (module_path, self_type, function) = split_path2(path);
    format_ident!("structure_{module_path}_impl_{self_type}_fn_{function}")
}

fn make_cfg_impl_const(path: Path) -> Ident {
    let (module_path, self_type, const_name) = split_path2(path);
    format_ident!("structure_{module_path}_impl_{self_type}_const_{const_name}")
}

fn make_cfg_impl_type(path: Path) -> Ident {
    let (module_path, self_type, type_name) = split_path2(path);
    format_ident!("structure_{module_path}_impl_{self_type}_type_{type_name}")
}

fn make_cfg_trait_function(path: Path) -> Ident {
    let (module_path, trait_type, function) = split_path2(path);
    format_ident!("structure_{module_path}_trait_{trait_type}_fn_{function}")
}

fn make_cfg_trait_const(path: Path) -> Ident {
    let (module_path, trait_type, const_name) = split_path2(path);
    format_ident!("structure_{module_path}_trait_{trait_type}_const_{const_name}")
}

fn make_cfg_trait_type(path: Path) -> Ident {
    let (module_path, trait_type, type_name) = split_path2(path);
    format_ident!("structure_{module_path}_trait_{trait_type}_type_{type_name}")
}

fn split_path(path: Path) -> (String, String) {
    let path_segments: Vec<_> = path.segments.iter().map(|s| &s.ident).collect();
    let (item_name, path_segments) = path_segments.split_last().unwrap();

    let item_name = item_name.to_string();
    let module_path = path_segments
        .iter()
        .copied()
        .map(Ident::to_string)
        .collect::<Vec<_>>()
        .join("_");

    (module_path, item_name)
}

fn split_path2(path: Path) -> (String, String, String) {
    let path_segments: Vec<_> = path.segments.iter().map(|s| &s.ident).collect();
    let (item2_name, path_segments) = path_segments.split_last().unwrap();
    let (item1_name, path_segments) = path_segments.split_last().unwrap();

    let item1_name = item1_name.to_string();
    let item2_name = item2_name.to_string();
    let module_path = path_segments
        .iter()
        .copied()
        .map(Ident::to_string)
        .collect::<Vec<_>>()
        .join("_");

    (module_path, item1_name, item2_name)
}
