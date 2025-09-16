package bor.tools.utils;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Timer class to manage timed events with optional callback execution.
 *
 */
@lombok.Data
public class Timer {

	private long timeout;
	private long startTime;
	private long endTime;
	private String name;
	private Runnable callback;
	private int runCount = 0;
	private int maxRunCount = 1;

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Thread thread;

	/**
	 * Default constructor initializing the timer with default values.
	 * <li> Timeout = 30 minutes
	 * <li> RepeatMax = -1 (infinite repeat)
	 * <li> Callback = null
	 *
	 */
	public Timer() {
		this("Timer", 30 * 60, -1, null);
	}

	public Timer(int timeout_seconds) {
		this("Timer", timeout_seconds * 60, -1, null);
	}


	/**
	 * Constructor initializing the timer with specified name, timeout, and
	 * callback. <br>
	 * Use repeatMax = -1 for infinite repeat
	 *
	 * @param name            - The name of the timer.
	 * @param timeout_seconds - The timeout duration in seconds.
	 * @param repeatMax        - the number of times the timer will run callback. Default is infinity
	 * @param callback        - The callback to be executed when the timer times out.
	 */
	public Timer(String name, int timeout_seconds, int repeatMax, Runnable callback) {
		this.name = name;
		this.maxRunCount = repeatMax;
		this.timeout = timeout_seconds;
		this.startTime = System.currentTimeMillis();
		this.endTime = startTime + (timeout_seconds * 1000);
		this.callback = callback;

		if (callback != null) {
			thread = new Thread(() -> {
				while (true) {
					try {
						Thread.sleep(1000);
						run();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			});
			thread.setName("Timer-" + name);
			thread.start();
		}
	}

	/**
	 * Checks if the timer has timed out.
	 *
	 * @return true if the timer has timed out, false otherwise.
	 */
	public boolean isTimeout() {
		return System.currentTimeMillis() >= endTime;
	}

	/**
	 * Resets the timer to the default timeout duration.
	 */
	public void reset() {
		this.startTime = System.currentTimeMillis();
		this.endTime = startTime + (this.timeout * 1000);
	}

	/**
	 * Sets the callback to be executed when the timer times out.
	 *
	 * @param callback The callback to be executed.
	 */
	public void setCallback(Runnable callback) {
		this.callback = callback;
	}

	/**
	 * Executes the callback if the timer has timed out and the run count is less
	 * than the maximum run count.
	 */
	public void run() {
		if (isTimeout() && (runCount <= 0 || runCount < maxRunCount)) {
			runCount++;
			callback.run();
		}
	}

}
