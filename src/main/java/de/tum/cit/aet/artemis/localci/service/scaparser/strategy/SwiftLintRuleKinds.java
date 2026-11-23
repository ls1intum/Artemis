package de.tum.cit.aet.artemis.localci.service.scaparser.strategy;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps the identifier of a SwiftLint rule to the kind that SwiftLint assigns to it.
 * <p>
 * SwiftLint reports no category with a violation, only the identifier of the violated rule. The kind of a rule is a property of the rule itself,
 * which the command {@code swiftlint rules} lists. The kinds are the tool categories of SwiftLint: {@value #KIND_STYLE}, {@value #KIND_IDIOMATIC}, {@value #KIND_LINT},
 * {@value #KIND_METRICS} and {@value #KIND_PERFORMANCE}. A rule that is not listed, such as a custom rule or a rule of a newer SwiftLint version, belongs to
 * {@value #KIND_MISCELLANEOUS}.
 * <p>
 * The lists below were generated from {@code swiftlint rules} of SwiftLint 0.63.1, including the opt-in and analyzer rules. To update them, run that command
 * and move changed identifiers to the list of their kind.
 */
public final class SwiftLintRuleKinds {

    public static final String KIND_STYLE = "style";

    public static final String KIND_IDIOMATIC = "idiomatic";

    public static final String KIND_LINT = "lint";

    public static final String KIND_METRICS = "metrics";

    public static final String KIND_PERFORMANCE = "performance";

    public static final String KIND_MISCELLANEOUS = "miscellaneous";

    // @formatter:off
    private static final String STYLE_RULES = """
            attribute_name_spacing attributes closing_brace closure_end_indentation closure_parameter_position closure_spacing collection_alignment
            colon comma comma_inheritance computed_accessors_order conditional_returns_on_newline contrasted_opening_brace control_statement
            custom_rules direct_return empty_enum_arguments empty_parameters empty_parentheses_with_trailing_closure explicit_self file_header
            file_types_order function_name_whitespace identifier_name implicit_getter implicit_optional_initialization implicit_return
            inclusive_language indentation_width leading_whitespace let_var_whitespace literal_expression_end_indentation modifier_order
            multiline_arguments multiline_arguments_brackets multiline_call_arguments multiline_function_chains multiline_literal_brackets
            multiline_parameters multiline_parameters_brackets multiple_closures_with_trailing_closure no_space_in_method_call
            non_overridable_class_declaration number_separator opening_brace operator_usage_whitespace optional_enum_case_matching period_spacing
            prefer_self_in_static_references prefer_self_type_over_type_of_self prefixed_toplevel_constant protocol_property_accessors_order
            redundant_discardable_let redundant_self return_arrow_whitespace self_binding shorthand_argument shorthand_operator single_test_class
            sorted_enum_cases sorted_imports statement_position superfluous_else switch_case_alignment switch_case_on_newline trailing_closure
            trailing_comma trailing_newline trailing_whitespace type_contents_order unneeded_parentheses_in_closure_argument unused_optional_binding
            vertical_parameter_alignment vertical_parameter_alignment_on_call vertical_whitespace vertical_whitespace_between_cases
            vertical_whitespace_closing_braces vertical_whitespace_opening_braces void_return
            """;

    private static final String IDIOMATIC_RULES = """
            anonymous_argument_in_multiline_closure block_based_kvo convenience_type discouraged_assert discouraged_none_name discouraged_object_literal
            discouraged_optional_boolean discouraged_optional_collection duplicate_imports explicit_acl explicit_enum_raw_value explicit_init
            explicit_top_level_acl explicit_type_interface extension_access_modifier fallthrough fatal_error_message file_name file_name_no_space
            for_where force_cast force_try force_unwrapping function_default_parameter_at_end generic_type_name implicitly_unwrapped_optional
            is_disjoint joined_default_parameter legacy_cggeometry_functions legacy_constant legacy_constructor legacy_hashing legacy_multiple
            legacy_nsgeometry_functions legacy_objc_type legacy_random nimble_operator no_empty_block no_extension_access_modifier no_fallthrough_only
            no_grouping_extension no_magic_numbers object_literal one_declaration_per_file pattern_matching_keywords prefer_asset_symbols
            prefer_condition_list prefer_key_path prefer_nimble prefer_type_checking prefer_zero_over_explicit_init private_over_fileprivate
            redundant_nil_coalescing redundant_objc_attribute redundant_set_access_control redundant_string_enum_value redundant_type_annotation
            redundant_void_return return_value_from_void_function shorthand_optional_binding static_operator static_over_final_class strict_fileprivate
            syntactic_sugar toggle_bool trailing_semicolon type_name unavailable_condition unavailable_function unneeded_break_in_switch
            unneeded_synthesized_initializer untyped_error_in_catch unused_enumerated void_function_in_ternary xct_specific_matcher xctfail_message
            """;

    private static final String LINT_RULES = """
            accessibility_label_for_image accessibility_trait_for_button array_init async_without_await balanced_xctest_lifecycle
            blanket_disable_command capture_variable class_delegate_protocol comment_spacing compiler_protocol_init deployment_target
            discarded_notification_center_observer discouraged_direct_init duplicate_conditions duplicate_enum_cases
            duplicated_key_in_dictionary_literal dynamic_inline empty_xctest_method expiring_todo ibinspectable_in_extension identical_operands
            incompatible_concurrency_annotation invalid_swiftlint_command local_doc_comment lower_acl_than_parent mark missing_docs
            non_optional_string_data_conversion notification_center_detachment ns_number_init_as_function_reference nslocalizedstring_key
            nslocalizedstring_require_bundle nsobject_prefer_isequal optional_data_string_conversion orphaned_doc_comment overridden_super_call
            override_in_extension private_action private_outlet private_subject private_swiftui_state private_unit_test prohibited_interface_builder
            prohibited_super_call quick_discouraged_call quick_discouraged_focused_test quick_discouraged_pending_test
            raw_value_for_camel_cased_codable_enum redundant_sendable required_deinit required_enum_case self_in_property_initialization strong_iboutlet
            superfluous_disable_command test_case_accessibility todo typesafe_array_init unhandled_throwing_task unneeded_escaping unneeded_override
            unneeded_throws_rethrows unowned_variable_capture unused_closure_parameter unused_control_flow_label unused_declaration unused_import
            unused_parameter unused_setter_value valid_ibinspectable weak_delegate yoda_condition
            """;

    private static final String METRICS_RULES = """
            closure_body_length cyclomatic_complexity enum_case_associated_values_count file_length function_body_length function_parameter_count
            large_tuple line_length nesting type_body_length
            """;

    private static final String PERFORMANCE_RULES = """
            contains_over_filter_count contains_over_filter_is_empty contains_over_first_not_nil contains_over_range_nil_comparison
            empty_collection_literal empty_count empty_string final_test_case first_where flatmap_over_map_reduce last_where reduce_boolean reduce_into
            sorted_first_last
            """;

    // @formatter:on

    private static final Map<String, String> KIND_BY_RULE = createKindByRule();

    private SwiftLintRuleKinds() {
    }

    /**
     * Returns the kind of the given SwiftLint rule.
     *
     * @param ruleIdentifier the identifier of the rule, for example {@code trailing_whitespace}
     * @return the kind of the rule, or {@value #KIND_MISCELLANEOUS} if the rule is not known
     */
    public static String kindOf(String ruleIdentifier) {
        return KIND_BY_RULE.getOrDefault(ruleIdentifier, KIND_MISCELLANEOUS);
    }

    private static Map<String, String> createKindByRule() {
        Map<String, String> kindByRule = new HashMap<>();
        addRules(kindByRule, KIND_STYLE, STYLE_RULES);
        addRules(kindByRule, KIND_IDIOMATIC, IDIOMATIC_RULES);
        addRules(kindByRule, KIND_LINT, LINT_RULES);
        addRules(kindByRule, KIND_METRICS, METRICS_RULES);
        addRules(kindByRule, KIND_PERFORMANCE, PERFORMANCE_RULES);
        return Map.copyOf(kindByRule);
    }

    private static void addRules(Map<String, String> kindByRule, String kind, String rules) {
        for (String rule : rules.split("\\s+")) {
            if (!rule.isEmpty()) {
                kindByRule.put(rule, kind);
            }
        }
    }
}
