use std::time::Duration;
use std::{cell::RefCell, ops::RangeInclusive};

use chrono::{NaiveDate, TimeDelta};
use rand::{thread_rng, Rng};

use rust_template_exercise::{context::Context, policy::Policy};

const ITERATIONS: usize = 10;
const LENGTH_MIN: usize = 5;
const LENGTH_MAX: usize = 15;

/// Main function.
/// Add code to demonstrate your implementation here.
fn main() {
    // Init Context and Policy
    let context = RefCell::new(Context::new());
    let mut policy = Policy::new(&context);

    // Run multiple times to simulate different sorting strategies
    for _ in 0..ITERATIONS {
        let mut dates = create_random_dates();
        // Configure context
        policy.configure(&dates);
        println!("Unsorted Array of course dates = {dates:#?}");

        // Sort dates
        context.borrow().sort(&mut dates);

        println!("Sorted Array of course dates = {dates:#?}");
    }
}

/// Generates a [Vec] of random [NaiveDate] objects with a random length
/// between [LENGTH_MIN] and [LENGTH_MAX].
fn create_random_dates() -> Vec<NaiveDate> {
    let length = thread_rng().gen_range(LENGTH_MIN..=LENGTH_MAX);

    let date_format = "%Y-%m-%d";
    let low_date = NaiveDate::parse_from_str("2024-09-15", date_format).unwrap();
    let high_date = NaiveDate::parse_from_str("2025-01-15", date_format).unwrap();

    let mut dates = Vec::new();
    dates.resize_with(length, || random_date_within(low_date..=high_date));
    dates
}

/// Creates a random Date within the given range.
fn random_date_within(range: RangeInclusive<NaiveDate>) -> NaiveDate {
    let (start, end) = range.into_inner();

    let max_delta = end - start;
    let max_duration = max_delta.to_std().unwrap();

    let random_duration = thread_rng().gen_range(Duration::ZERO..=max_duration);
    let random_delta = TimeDelta::from_std(random_duration).unwrap();

    start + random_delta
}
