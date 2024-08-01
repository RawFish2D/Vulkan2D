package ua.rawfish2d.vk2d.utils;

import java.util.Locale;

public class ExecutionTimer {
	private final TimeHelper timer = new TimeHelper();
	private long time = 0;
	private int callsCount = 0;
	private double executionTimeAvg = 0d;
	private double executionTime = 0d;

	public void preCall() {
		time = System.nanoTime();
	}

	public void postCall() {
		callsCount++;
		final double delta = System.nanoTime() - time;
		executionTime += delta / 1000000d;

		if (timer.hasReachedMilli(1000)) {
			timer.reset();
			executionTimeAvg = executionTime / (double) callsCount;
			callsCount = 0;
			executionTime = 0d;
		}
	}

	public double getAvgTime() {
		return executionTimeAvg;
	}

	public String getFormatedTime() {
		return String.format(Locale.US, "%.4f", executionTimeAvg);
	}
}
