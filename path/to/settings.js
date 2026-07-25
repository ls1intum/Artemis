/**
 * Adjust the JavaScript code to make the field responsive and fully visible on different screen resolutions and window sizes.
 */

// Import required libraries
import $ from 'jquery';
import 'bootstrap';

// Select the field container
const fieldContainer = $('#field-container');

// Add a new CSS class to the field container
fieldContainer.addClass('field-responsive');

// Style the field label
fieldContainer.find('.field-label').css({
  'font-weight': 'bold',
  'margin-bottom': '10px'
});

// Style the field input
fieldContainer.find('.field-input').css({
  'width': '100%',
  'height': '40px',
  'padding': '10px',
  'border': '1px solid #ccc',
  'border-radius': '5px'
});

// Style the field unit
fieldContainer.find('.field-unit').css({
  'font-size': '14px',
  'color': '#666',
  'margin-left': '10px'
});

// Add event listener to handle window resize
$(window).resize(function() {
  // Update the field container width
  fieldContainer.css('width', $(window).width() * 0.8);
});