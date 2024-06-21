#[cfg(test)]
mod tests {
    use rust_template_exercise::*;

    #[test]
    fn test_addition() {
        let result = add(2, 2);
        assert_eq!(result, 4);
    }
}
