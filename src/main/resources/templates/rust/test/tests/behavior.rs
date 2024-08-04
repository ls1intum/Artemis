use std::any::TypeId;

use chrono::NaiveDate;
use rust_template_exercise::{
    bubble_sort::BubbleSort, context::Context, merge_sort::MergeSort, policy::Policy,
    sort_strategy::SortStrategy,
};

// We can't use the opt variants because the Option methods aren't const yet
#[allow(deprecated)]
const DATES_UNORDERED: [NaiveDate; 4] = [
    NaiveDate::from_ymd(2018, 11, 8),
    NaiveDate::from_ymd(2017, 4, 15),
    NaiveDate::from_ymd(2016, 2, 15),
    NaiveDate::from_ymd(2017, 9, 15),
];
#[allow(deprecated)]
const DATES_ORDERED: [NaiveDate; 4] = [
    NaiveDate::from_ymd(2016, 2, 15),
    NaiveDate::from_ymd(2017, 4, 15),
    NaiveDate::from_ymd(2017, 9, 15),
    NaiveDate::from_ymd(2018, 11, 8),
];

#[test]
fn test_bubble_sort() {
    let bubble_sort = BubbleSort;
    let mut dates = DATES_UNORDERED;
    bubble_sort.perform_sort(&mut dates);
    assert_eq!(dates, DATES_ORDERED, "BubbleSort does not sort correctly");
}

#[test]
fn test_merge_sort() {
    let merge_sort = MergeSort;
    let mut dates = DATES_UNORDERED;
    merge_sort.perform_sort(&mut dates);
    assert_eq!(dates, DATES_ORDERED, "MergeSort does not sort correctly");
}

#[test]
fn test_use_merge_sort_for_big_list() {
    let context = Context::new();
    let mut policy = Policy::new(&context);

    let data = [NaiveDate::default(); 20];
    policy.configure(&data);

    let sort_strategy = context.sort_algorithm();
    let sort_strategy = sort_strategy.as_ref().unwrap().as_ref();

    assert_eq!(
        sort_strategy.type_id(),
        TypeId::of::<MergeSort>(),
        "The sort algorithm of Context was not MergeSort for a list with more than 10 dates."
    );
}

#[test]
fn test_use_bubble_sort_for_small_list() {
    let context = Context::new();
    let mut policy = Policy::new(&context);

    let data = [NaiveDate::default(); 10];
    policy.configure(&data);

    let sort_strategy = context.sort_algorithm();
    let sort_strategy = sort_strategy.as_ref().unwrap().as_ref();

    assert_eq!(
        sort_strategy.type_id(),
        TypeId::of::<BubbleSort>(),
        "The sort algorithm of Context was not BubbleSort for a list with less or equal than 10 dates."
    );
}
