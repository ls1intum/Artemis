use crate::sort_strategy::SortStrategy;
use chrono::NaiveDate;
use std::ops::Deref;

pub struct Context {
    sort_algorithm: Option<Box<dyn SortStrategy<NaiveDate>>>,
}

impl Context {
    pub fn new() -> Context {
        Context {
            sort_algorithm: None,
        }
    }

    /// Runs the configured sorting algorithm.
    pub fn sort(&self, data: &mut [NaiveDate]) {
        let sort_algorithm = self
            .sort_algorithm
            .as_ref()
            .expect("sort_algorithm has to be set before sort() is called");

        sort_algorithm.perform_sort(data);
    }

    pub fn set_sort_algorithm(&mut self, sort_algorithm: Box<dyn SortStrategy<NaiveDate>>) {
        self.sort_algorithm = Some(sort_algorithm);
    }

    pub fn sort_algorithm(&self) -> &dyn SortStrategy<NaiveDate> {
        self.sort_algorithm
            .as_ref()
            .expect("sort_algorithm has to be set")
            .deref()
    }
}
