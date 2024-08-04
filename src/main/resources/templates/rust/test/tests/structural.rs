mod structural_helpers;

use structural_helpers::*;

#[test]
fn test_sort_strategy_trait() {
    let ast = parse_file("./assignment/src/sort_strategy.rs");
    check_trait_names(&ast.items, ["SortStrategy"]);
}

#[test]
fn test_sort_strategy_methods() {
    let ast = parse_file("./assignment/src/sort_strategy.rs");
    let sort_strategy = find_trait(&ast.items, "SortStrategy").unwrap();
    check_trait_function_names(&sort_strategy.items, ["perform_sort"]);
}

#[test]
fn test_context_fields() {
    let ast = parse_file("./assignment/src/context.rs");
    let context = find_struct(&ast.items, "Context").unwrap();
    check_struct_field_names(&context.fields, ["sort_algorithm"]);
}

#[test]
fn test_context_methods() {
    let ast = parse_file("./assignment/src/context.rs");
    let context_impl = find_impl(&ast.items, "Context").unwrap();
    check_impl_function_names(&context_impl.items, ["sort"]);
}

#[test]
fn test_policy_fields() {
    let ast = parse_file("./assignment/src/policy.rs");
    let policy = find_struct(&ast.items, "Policy").unwrap();
    check_struct_field_names(&policy.fields, ["context"]);
}

#[test]
fn test_policy_methods() {
    let ast = parse_file("./assignment/src/policy.rs");
    let policy_impl = find_impl(&ast.items, "Policy").unwrap();
    check_impl_function_names(&policy_impl.items, ["new", "configure"]);
}

#[test]
fn test_bubble_sort_struct() {
    let ast = parse_file("./assignment/src/bubble_sort.rs");
    find_struct(&ast.items, "BubbleSort").unwrap();
    find_impl_for(&ast.items, "BubbleSort", "SortStrategy").unwrap();
}

#[test]
fn test_merge_sort_struct() {
    let ast = parse_file("./assignment/src/merge_sort.rs");
    find_struct(&ast.items, "MergeSort").unwrap();
    find_impl_for(&ast.items, "MergeSort", "SortStrategy").unwrap();
}
