package ua.rawfish2d.vk2d.utils;

import java.util.Random;

public class MiscUtils {
	private static final Random rng = new Random();

	public static int random(int min, int max) {
		int max2 = (max - min) + 1;
		if (max2 < 0)
			max2 = min + 1;
		return rng.nextInt(max2) + min;
	}

	public static float random(float min, float max) {
		return min + rng.nextFloat() * (max - min);
	}

	public static double random(double min, double max) {
		return min + rng.nextDouble() * (max - min);
	}

	public static double clamp(double val, double min, double max) {
		return Math.max(min, Math.min(max, val));
	}

	public static float clamp(float val, float min, float max) {
		return Math.max(min, Math.min(max, val));
	}

	public static int clamp(int val, int min, int max) {
		return Math.max(min, Math.min(max, val));
	}
}
