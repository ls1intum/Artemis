//! Compile-time source code reflection
//!
//! Use this for conditional compilation.

use proc_macro::TokenStream;

use quote::{format_ident, quote};
use syn::{parse_macro_input, Item, Path};

#[proc_macro_attribute]
pub fn require_struct(attr: TokenStream, item: TokenStream) -> TokenStream {
    let path = parse_macro_input!(attr as Path);
    let original_item = parse_macro_input!(item as Item);

    let identifiers: Vec<_> = path.segments.iter().map(|s| s.ident.to_string()).collect();
    let (struct_name, module_path) = identifiers.split_last().unwrap();
    let module_path = module_path.join("_");

    let cfg = format_ident!("structure_{module_path}_struct_{struct_name}");

    let res = quote! {
        #[cfg(#cfg)]
        #original_item
    };

    res.into()
}

#[proc_macro_attribute]
pub fn require_trait(attr: TokenStream, item: TokenStream) -> TokenStream {
    let path = parse_macro_input!(attr as Path);
    let original_item = parse_macro_input!(item as Item);

    let identifiers: Vec<_> = path.segments.iter().map(|s| s.ident.to_string()).collect();
    let (trait_name, module_path) = identifiers.split_last().unwrap();
    let module_path = module_path.join("_");

    let cfg = format_ident!("structure_{module_path}_trait_{trait_name}");

    let res = quote! {
        #[cfg(#cfg)]
        #original_item
    };

    res.into()
}
