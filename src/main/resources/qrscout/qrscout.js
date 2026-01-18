/**
 * qrscout.js
 *
 * Builds and runs the QR Scout UI from a configuration file.
 *
 */
/**
 * The game configuration.
 */
let config;

/**
 * Initialize the web app.
 */
async function init() {
  await loadConfig();

  startApp();
}

/**
 * Loads the configuration via HTTP(S).
 */
async function loadConfig() {
  const response = await fetch('./config.json');

  if (!response.ok) {
    alert('Failed to load config file:' + response.status);
    config = { "title" : "Error" };
  } else {
    try {
      config = await response.json();
    } catch (e) {
      alert('Your config file could not be parsed:\n\n' + e);

      config = { "title" : "Error" };
    }
  }

  if(!config.delimiter) {
    config.delimiter = '\t';
  }

  console.log('Config:', config);

  return config;
}

/**
 * Completely removes any contents in the 'main' element.
 */
function resetUI() {
  document.getElementById('main').replaceChildren();
}

/**
 * Builds the UI for the application.
 *
 * This primarily means building the various sections and the fields in each.
 *
 * If this function fails, the application will be unusable.
 */
function startApp() {
  console.log('Starting app ' + config.page_title);

  resetUI();

  let main = document.getElementById('main');

  let title = config.title ?? config.page_title ?? "QR Scout";
  let pageTitle = config.page_title ?? config.title ?? "QR Scout";

  main.appendChild(makeElement('h1', {}, pageTitle));

  document.getElementById('title').replaceChildren(title);
  document.getElementById('qr-title').replaceChildren(pageTitle);

  let container = makeElement('div', { 'id' : 'sections' });

  if(config.sections) {
    for (const section of config.sections) {
      let children = [];

      console.log('Creating section ' + section.name);

      children.push(makeElement('h2', { }, section.name));

      for (const field of section.fields) {
        console.log('Creating field ' + section.name + '/' + field.title);
        children.push(createField(field));
      }

      container.appendChild(makeElement('div', {
        'id' : 'section_' + section.name,
        'class' : 'section'
        }, children));
    }
  }

  document.getElementById('generate-qr-code').onclick = generateQRCode;

  // Ask the user before resetting the form
  document.getElementById('reset').onclick = function() {
    return confirm('Are you sure you want to reset the form?') && resetForm();
  };

  main.appendChild(container);

  LapTimer.initTimers();

  console.log('UI complete.');
}

/**
 * Resets the form, respecting the formResetBehavior of various fields.
 *
 * Note that 'preserve' formResetBehavior is handled by an event handler on
 * each field.
 */
function resetForm() {
  resetTimers();
  incrementFields();
}

/**
 * Increment fields that requested incrementing.
 */
function incrementFields() {
  const incrementFields = document.querySelectorAll('.increment');

  incrementFields.forEach(increment => {
console.log('Incrementing field', increment);
    try {
      const incremented = parseInt(increment.value) + 1;
      increment.value = increment.defaultValue = incremented;
    } catch (e) {
      console.log(e);
    }
  });
}

/**
 * Resets any timers that have been added to the page.
 */
function resetTimers() {
  const timers = document.querySelectorAll('.lap-timer');

  timers.forEach(timer => { timer.resetTimer(); });
}

/**
 * Creates a field. This returns an element containing the complete UI
 * for the field.
 *
 * This can be a simple element (e.g. a text <input>) or a whole pile
 * of elements supporting an on-screen field (e.g. a timer).
 *
 * Each field is constructed in such a way that if the field's form-reset
 * behavior is "preserve" (i.e. "don't reset"), then that field's default
 * value is updated each time the field's value is changed. This simplifies
 * the process of resetting the form by allowing the browser to handle the
 * form-reset process.
 */
function createField(field) {
  let children = [];

  children.push(makeElement('h3', { }, [ field.title ]));

  // Different field types need different types of interfaces
  let element;

  switch(field.type) {
    // "text", "number", "boolean", "range", "select", "counter", "timer", "multi-select", or "image"

    case 'text' :
      element = makeTextField(field);
      break;

    case 'number' :
      element = makeNumberField(field);
      break;

    case 'counter' :
    case 'range' :
      element = makeCounterField(field);
      break;

    case 'boolean' :
      element = makeBooleanField(field);
      break;

    case 'timer' :
      element = makeTimerField(field);
      break;

    case 'select' :
    case 'multi-select' :
      element = makeSelectField(field);
      break;

    case 'image':
     element = makeImageField(field);
     break;

    default:
      alert('Unsupported field type for field "' + field.title + '": ' + field.type);
      break;
  }

  children.push(element);

  return makeElement('div', {
    'class' : 'field'
  }, children);
}

function makeTextField(field) {
  let attrs = {
	    'id' : 'field_' + field.code,
	    'type' : 'text',
	    'name' : field.code
  };

  let element = makeElement('input', attrs);

  if(field.defaultValue) {
    element.value = field.defaultValue;
    element.defaultValue = field.defaultValue;
  }

  if(field.formResetBehavior == 'preserve') {
    // Update the default value with each change
    element.addEventListener('change', function() { this.defaultValue = this.value; });
  }

  return element;
}

function makeNumberField(field) {
  let attrs = {
    'id' : 'field_' + field.code,
    'type' : 'text',
    'class' : 'number',
    'readonly' : 'readonly',
    'name' : field.code,
    'value' : field.defaultValue,
    'defaultValue' : field.defaultValue
  };

  if(field.formResetBehavior == 'increment') {
    attrs.class += ' increment';
  }

  let number = makeElement('input', attrs);

  if(field.formResetBehavior == 'preserve') {
    // Update the default value with each change
    number.addEventListener('change', function() { this.defaultValue = this.value; });
  }

  return number;
}

function makeCounterField(field) {
  let items = [];

  if(!field.hasOwnProperty('min')) {
    field.min = 0;
  }
  if(!field.hasOwnProperty('max')) {
    field.max = 999999;
  }

  if(!field.hasOwnProperty('defaultValue')) {
    field.defaultValue = field.min;
  }

  let attrs = {
    'id' : 'field_' + field.code,
    'type' : 'text',
    'class' : 'counter',
    'readonly' : 'readonly',
    'name' : field.code,
    'value' : field.defaultValue,
    'defaultValue' : field.defaultValue
  };

  if(field.formResetBehavior == 'increment') {
    attrs.class += ' increment';
  }

  if(field.type == 'range') {
    attrs.type = 'number';
    attrs.min = field.min;
    attrs.max = field.max;
  }

  let number = makeElement('input', attrs);

  if(field.formResetBehavior == 'preserve') {
    // Update the default value with each change
    number.addEventListener('change', function() { this.defaultValue = this.value; });
  }

  // Create two - and + buttons that just raise and lower the number.
  // Clamp the numbers to the field's min and max values
  items.push(makeElement('a', {
    'class' : 'counter-button',
    'onclick' : function () {
      let value = parseInt(number.value);
      if(isNaN(value)) { value = field.defaultValue; }
      number.value = Math.max(field.min, value - 1); // Prevent underflow

      // Notify any event listeners
      number.dispatchEvent(new Event('change', { bubbles: true }));

      return false;
    }}, [ '\u2013' ]));

  items.push(number);

  items.push(makeElement('a', {
    'class' : 'counter-button',
    'onclick' : function () {
      let value = parseInt(number.value);
      if(isNaN(value)) { value = field.defaultValue; }
      number.value = Math.min(field.max, value + 1); // Prevent overflow

      // Notify any event listeners
      number.dispatchEvent(new Event('change', { bubbles: true }));

      return false;
    }}, [ '+' ]));

  return makeElement('div', {
    'class' : 'number-wrapper'
  }, items);
}

function makeSelectField(field) {
  let items = [];

  if(field.type === 'select') {
    items.push(makeElement('option', { 'value' : '' }));
  }

  // field.choices is an object of choice:name
  if(field.choices) {
    Object.keys(field.choices).forEach(key => {
      let attrs = {
        'value' : key
      };
      if(field.defaultValue == key
         || (Array.isArray(field.defaultValue)
             && field.defaultValue.includes(key))) {
        attrs.selected = 'selected';
      }
      items.push(makeElement('option', attrs, [ field.choices[key] ]));
    });
  }

  let attrs = {
    'id' : 'field_' + field.code,
    'name' : field.code,
  };

  if(field.type == 'multi-select') {
    attrs.multiple = 'multiple';
  }

  let select = makeElement('select', attrs, items);
  if(field.formResetBehavior == 'preserve') {
    select.addEventListener('change', function() {
      Array.from(this.options).forEach(option => {
        option.defaultSelected = option.selected;
      });
    });
  }

  return select;
}

function makeBooleanField(field) {
  let attrs = {
    'id' : 'field_' + field.code,
    'type' : 'checkbox',
    'value' : 'Y',
    'name' : field.code
  }
console.log('Creating boolean field ' + field.code);
  if(field.hasOwnProperty('defaultValue')) {
    if(field.defaultValue) {
      attrs.checked = 'checked';
      attrs.defaultChecked = 'checked';
    }
  }

  element = makeElement('input', attrs);

  if(field.formResetBehavior == 'preserve') {
    // Update the default value with each change
    element.addEventListener('change', function() { this.defaultChecked = this.checked; });
  }

  return element;
}

function makeTimerField(field) {
  const template = document.getElementById('lap-timer-template');
  const cloneFragment = template.content.cloneNode(true);

  const clone = cloneFragment.querySelector('.lap-timer');

  clone.id = 'field_' + field.code;
  if(field.outputType) {
    clone.dataset.outputType = field.outputType;
  }
  if(field.listSeparator) {
    clone.dataset.listSeparator = field.listSeparator;
  }
  if(field.formResetBehavior) {
    clone.dataset.formResetBehavior = field.formResetBehavior;
  }

  return clone;
}

function makeImageField(field) {
  let style = { };
  let attrs = {
    'id' : 'field_' + field.code,
    'src' : field.defaultValue ?? 'none',
    'title' : field.alt ?? field.title,
    'alt' : field.alt ?? field.title,
    'style' : style
  };

  if(field.width) {
    style.width = field.width;
  }
  if(field.height) {
    style.height = field.height;
  }

  let img = makeElement('img', attrs);

  if(field.hasOwnProperty('value')) {
    img.value = field.value;
  }

  img.onclick = function () {
    displayImage(this.src);
  };

  return img;
}

/**
 * Display an image full-screen.
 */
function displayImage(url) {
  let img = makeElement('img', {
    'src' : url,
    'style' : { "width" : "100%;" }
  });

  let popup = document.getElementById('popup');
  popup.replaceChildren(img);

  popup.classList.toggle('hidden');
  popup.onclick = function() {
    this.classList.toggle('hidden');
  };
}

/**
 * Make an element of the specified tag with optional properties and
 * child elements.
 */
function makeElement(tag, props = {}, ...children) {
  const element = document.createElement(tag);

  Object.entries(props).forEach(([key, value]) => {
    if (key === 'class') element.className = value;
    else if (key === 'style') Object.assign(element.style, value);
    else if (key.startsWith('on')) element.addEventListener(key.slice(2), value);
    else element[key] = value;
  });

  children.flat().forEach(child => {
    element.append(
      child instanceof Node ? child : document.createTextNode(child)
    );
  });

  return element;
}

/**
 * Generate a QR code containing the data and show it on the screen.
 */
function generateQRCode() {
  let data = assembleData();

  const maxSize = 0.6; // percentage of viewport
  const qrSize = Math.min(window.innerWidth, window.innerHeight) * maxSize;

  console.log('Generating QR code of width ' + qrSize);
  const qr = new QRious({
    element: document.createElement('canvas'),
    value: data,
    size: qrSize,
  });

  let qrContainer = document.getElementById('qr-container');
  qrContainer.replaceChildren(qr.element);
  let qrPopup = document.getElementById('qr-popup');
  qrPopup.style.display = 'block';
  document.getElementById('qr-close').onclick = function () {
    qrPopup.style.display='none';
  };
}

/**
 * Pulls all the data together from the form into a single string and
 * returns it.
 */
function assembleData() {
  const fields = document.querySelectorAll('.field input, .field select, .field img, .lap-timer');

  let data = '';
  let count = 0;
  fields.forEach(field => {
    if(count++ > 0) { data += config.delimiter; }
    let tag = field.tagName.toLowerCase();
// console.log('gathering data from field ' + count + ': ' + tag + ' / ' + field.id);
    if (tag === 'input') {
      if (field.type == 'checkbox') {
        data += field.checked ? 'Y' : 'N';
      } else {
        data += field.value;
      }
    } else if (tag === 'select') {
      let value = '';
      Array.from(field.options).forEach(option => {
        if (value.length > 0) { value += ',' }
        if(option.selected) {
          value = value + option.value;
        }
      });
      data += value;
    } else if (tag === 'img') {
      if(field.hasOwnProperty('value')) {
        data += field.value; // This is an extension we added
      } else {
        data += field.src; // This is how the reference implementation works
      }
    } else if (field.classList.contains('lap-timer')) {
      data += field.getTimerInfo();
    }
  });

  console.log('Assembled data from ' + fields.length + ' field(s): ', data);

  return data;
}

document.addEventListener('DOMContentLoaded', function() {
  // Run the init function after page load.
  init();
});

