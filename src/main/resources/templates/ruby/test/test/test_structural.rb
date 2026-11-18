# frozen_string_literal: true

require_relative "test_helper"

class TestStructural < Minitest::Test
  def test_context_structure
    require "context"

    assert(defined?(Context), "Context is not defined")
    assert(Context.instance_of?(Class), "Context is not a class")
    assert_equal([], Context.instance_method(:initialize).parameters,
                 "The constructor of Context does not have the expected parameters")
    assert(Context.public_method_defined?(:dates), "Context has not attribute reader for 'dates'")
    assert(Context.public_method_defined?(:dates=), "Context has not attribute writer for 'dates'")
    assert(Context.public_method_defined?(:sort_algorithm), "Context has not attribute reader for 'sort_algorithm'")
    assert(Context.public_method_defined?(:sort_algorithm=), "Context has not attribute writer for 'sort_algorithm'")
    assert(Context.public_method_defined?(:sort), "Context has no method 'sort'")
  end

  def test_policy_structure
    require "policy"

    assert(defined?(Policy), "Policy is not defined")
    assert(Policy.instance_of?(Class), "Policy is not a class")
    assert_equal([%i[req context]], Policy.instance_method(:initialize).parameters,
                 "The constructor of Policy does not have the expected parameters")
    assert(Policy.public_method_defined?(:context), "Policy has not attribute reader for 'context'")
    assert(Policy.public_method_defined?(:context=), "Policy has not attribute writer for 'context'")
    assert(Policy.public_method_defined?(:configure), "Policy has no method 'configure'")
  end
end
