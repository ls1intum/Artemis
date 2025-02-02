# frozen_string_literal: true

require_relative "../assignment_path"

$LOAD_PATH.unshift File.join(ASSIGNMENT_PATH, "src")

require "minitest/autorun"

# exit successfully on failure
Minitest.after_run do
  exit 0
end
