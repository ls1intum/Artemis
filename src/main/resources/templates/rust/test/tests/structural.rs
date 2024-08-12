mod structural_helpers;

use structural_helpers::*;

#[test]
fn test_sort_strategy_trait() {
    let ast = parse_file("./assignment/src/sort_strategy.rs");
    check_trait_names(&ast.items, ["SortStrategy"])
        .unwrap_or_else(|name| panic!("A trait named \"{name}\" should be defined"));
}

#[test]
fn test_sort_strategy_supertrait() {
    let ast = parse_file("./assignment/src/sort_strategy.rs");
    let sort_strategy = find_trait(&ast.items, "SortStrategy")
        .expect("A trait named \"SortStrategy\" should be defined");
    check_trait_supertrait(sort_strategy, "Any")
        .unwrap_or_else(|_| panic!("SortStrategy should have \"Any\" as a supertrait"));
}

#[test]
fn test_sort_strategy_methods() {
    let ast = parse_file("./assignment/src/sort_strategy.rs");
    let sort_strategy = find_trait(&ast.items, "SortStrategy")
        .expect("A trait named \"SortStrategy\" should be defined");
    check_trait_function_names(&sort_strategy.items, ["perform_sort"])
        .unwrap_or_else(|name| panic!("SortStrategy should define the function \"{name}\""));
}

#[test]
fn test_context_fields() {
    let ast = parse_file("./assignment/src/context.rs");
    let context =
        find_struct(&ast.items, "Context").expect("A struct named \"Context\" should be defined");
    check_struct_field_names(&context.fields, ["sort_algorithm"])
        .unwrap_or_else(|name| panic!("Context should define the field \"{name}\""));
}

#[test]
fn test_context_methods() {
    let ast = parse_file("./assignment/src/context.rs");
    let context_impl =
        find_impl(&ast.items, "Context").expect("SortStrategy should implement functions");
    check_impl_function_names(&context_impl.items, ["new", "sort", "sort_algorithm"])
        .unwrap_or_else(|name| panic!("Context should implement the function \"{name}\""));
}

#[test]
fn test_policy_fields() {
    let ast = parse_file("./assignment/src/policy.rs");
    let policy =
        find_struct(&ast.items, "Policy").expect("A struct named \"Policy\" should be defined");
    check_struct_field_names(&policy.fields, ["context"])
        .unwrap_or_else(|name| panic!("Policy should define the field \"{name}\""));
}

#[test]
fn test_policy_methods() {
    let ast = parse_file("./assignment/src/policy.rs");
    let policy_impl = find_impl(&ast.items, "Policy").expect("Policy should implement functions");
    check_impl_function_names(&policy_impl.items, ["new", "configure"])
        .unwrap_or_else(|name| panic!("Policy should implement the function \"{name}\""));
}

#[test]
fn test_bubble_sort_struct() {
    let ast = parse_file("./assignment/src/bubble_sort.rs");
    find_struct(&ast.items, "BubbleSort").expect("A struct named \"BubbleSort\" should be defined");
    find_impl_for(&ast.items, "BubbleSort", "SortStrategy")
        .expect("BubbleSort should implement the trait \"SortStrategy\"");
}

#[test]
fn test_merge_sort_struct() {
    let ast = parse_file("./assignment/src/merge_sort.rs");
    find_struct(&ast.items, "MergeSort").expect("A struct named \"MergeSort\" should be defined");
    find_impl_for(&ast.items, "MergeSort", "SortStrategy")
        .expect("MergeSort should implement the trait \"SortStrategy\"");
}
