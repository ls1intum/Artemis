#[cfg(test)]
mod tests {
    use rust_template_exercise::*;

    #[test]
    fn test_addition() {
        let actual = add(2, 2);
        let expected = 4;
        assert_eq!(expected, actual, "expected: {expected}, actual: {actual}");
    }
}
