/**
 * Implements a start/stop/reset timer.
 *
 * The static initTimers method will attach LapTimer instance to each
 * element with the ".lap-timer" class.
 *
 * The format of the .lap-timer element should be:
 *
 * <div class="lap-timer">
 *   <div class="display" data-current>00.00</div>
 *   <div class="controls">
 *     <button type="button" data-start>
 *       (Start button content)
 *     </button>
 *     <button class="hidden" type="button" data-stop>
 *       (Stop button content)
 *     </button>
 *     <button type="button" data-lap>
 *       (Lap button content)
 *     </button>
 *     <button type="button" data-reset>
 *       (Reset button content)
 *     </button>
 *   </div>
 *   <div class="cumulative-display">
 *     <span data-total>00.00</span>
 *     (<span data-laps>0</span>)
 *   </div>
 * </div>
 */
class LapTimer {
  constructor(container) {
    this.container = container;
    this.currentEl = container.querySelector("[data-current]");
    this.totalEl = container.querySelector("[data-total]");
    this.lapsEl = container.querySelector("[data-laps]");
    this.startBtn = container.querySelector("[data-start]");
    this.stopBtn = container.querySelector("[data-stop]");
    this.lapBtn = container.querySelector("[data-lap]");
    this.resetBtn = container.querySelector("[data-reset]");
    this.outputType = container.dataset.outputType ?? 'average';
    this.listSeparator = container.dataset.listSeparator ?? ',';
    this.formResetBehavior = container.dataset.formResetBehavior ?? 'reset';

    this.startTime = 0;
    this.elapsedTime = 0;
    this.totalTime = 0;
    this.laps = [];
    this.interval = null;
    this.running = false;

    this.bindEvents();
    this.updateCurrent();
  }

  format(ms) {
    return (ms / 1000).toFixed(2);
  }

  updateCurrent() {
    this.currentEl.textContent = this.format(this.elapsedTime);
  }

  updateTotal() {
    let time;
    switch(this.outputType) {
      case 'average' :
        time = this.laps.length > 0 ? this.totalTime / this.laps.length : 0;
        break;

      default:
        time = this.totalTime;
        break;
    }
    this.totalEl.textContent = this.format(time);
    this.lapsEl.textContent = this.laps.length;
  }

  start() {
    this.startTime = Date.now() - this.elapsedTime;
    this.interval = setInterval(() => {
      this.elapsedTime = Date.now() - this.startTime;
      this.updateCurrent();
    }, 10);

    this.running = true;
    this.startBtn.classList.add('hidden');
    this.stopBtn.classList.remove('hidden');
  }

  stop() {
    clearInterval(this.interval);
    this.running = false;

    this.startBtn.classList.remove('hidden');
    this.stopBtn.classList.add('hidden');
  }

  toggle() {
    this.running ? this.stop() : this.start();
  }

  lap() {
    if (this.elapsedTime === 0) return;

    this.totalTime += this.elapsedTime;
    this.laps.push(this.elapsedTime);

    this.updateTotal();

    // Reset current lap
    this.elapsedTime = 0;
    this.startTime = Date.now();
    this.updateCurrent();
  }

  reset() {
    this.stop();

    this.elapsedTime = 0;
    this.totalTime = 0;
    this.laps = [];

    this.updateCurrent();
    this.updateTotal();
  }

  getTimerInfo() {
    switch(this.outputType) {
      case 'list' :
        if(this.laps.length > 0 ) {
          return this.laps.reduce((acc, num, index) => {
            const formatted = (num / 1000).toFixed(3);
            return acc + (index === 0 ? '' : this.listSeparator) + formatted;
          }, '');
        } else {
          return '';
        }

      case 'total' :
        if (this.laps.length > 0) {
          let sum = this.laps.reduce((acc, curr) => acc + curr, 0);
          sum += this.elapsedTime;

          return (sum / 1000).toFixed(3);
        } else {
          return 0;
        }

      case 'average' :
        if (this.laps.length > 0) {
          const avg = this.totalTime / this.laps.length;

          return (avg / 1000).toFixed(3);
        } else {
          return 0;
        }

      default :
        return 'undefined';
    }
  }

  bindEvents() {
    this.container.getTimerInfo = () => this.getTimerInfo();
    if(this.formResetBehavior == 'preserve') {
      this.container.resetTimer = function () {};
    } else {
      this.container.resetTimer = () => this.reset();
    }
    this.startBtn.addEventListener("click", () => this.toggle());
    this.stopBtn.addEventListener("click", () => this.toggle());
    this.lapBtn.addEventListener("click", () => this.lap());
    this.resetBtn.addEventListener("click", () => this.reset());
  }

  static initTimers() {
    console.log('Initializing timers...');
    let count = 0;
    document.querySelectorAll(".lap-timer").forEach(container => {
      new LapTimer(container);
      count++;
    });
    console.log('Initialized ' + count + ' timer(s)');
  }
}
