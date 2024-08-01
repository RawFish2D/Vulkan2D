package ua.rawfish2d.vk2d.utils;

import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

public class MathUtils {
	public static double clamp(double val, double min, double max) {
		return Math.max(min, Math.min(max, val));
	}

	public static float clamp(float val, float min, float max) {
		return Math.max(min, Math.min(max, val));
	}

	public static int clamp(int val, int min, int max) {
		return Math.max(min, Math.min(max, val));
	}

	public static float cos(float v) {
		return (float) Math.cos(v);
	}

	public static float sin(float v) {
		return (float) Math.sin(v);
	}

	public static float cos(double v) {
		return (float) Math.cos(v);
	}

	public static float sin(double v) {
		return (float) Math.sin(v);
	}

	public static float toRad(float deg) {
		return (float) Math.toRadians(deg);
	}

	public static float toDeg(float rad) {
		return (float) Math.toDegrees(rad);
	}

	public static float toRad(double deg) {
		return (float) Math.toRadians(deg);
	}

	public static float toDeg(double rad) {
		return (float) Math.toDegrees(rad);
	}

	public static float sqrt(float value) {
		return (float) Math.sqrt((double) value);
	}

	public static float sqrt(double value) {
		return (float) Math.sqrt(value);
	}

	public static int floor(float value) {
		return (int) Math.floor(value);
	}

	public static int floor(double value) {
		return (int) Math.floor(value);
	}

	public static int ceil(float value) {
		return (int) Math.ceil(value);
	}

	public static int ceil(double value) {
		return (int) Math.ceil(value);
	}

	public static int round(float value) {
		return (int) Math.round(value);
	}

	public static int round(double value) {
		return (int) Math.round(value);
	}

	public static double getDistance(Vector3d pos1, Vector3d pos2) {
		double d0 = pos1.x - pos2.x;
		double d1 = pos1.y - pos2.y;
		double d2 = pos1.z - pos2.z;
		return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
	}

	public static float getDistance(Vector3f pos1, Vector3f pos2) {
		float d0 = pos1.x - pos2.x;
		float d1 = pos1.y - pos2.y;
		float d2 = pos1.z - pos2.z;
		return MathUtils.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
	}

	public static float getDistance(Vector3i pos1, Vector3i pos2) {
		float d0 = pos1.x - pos2.x;
		float d1 = pos1.y - pos2.y;
		float d2 = pos1.z - pos2.z;
		return MathUtils.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
	}

	public static float getDistance(Vector2f pos1, Vector2f pos2) {
		float d0 = pos1.x - pos2.x;
		float d1 = pos1.y - pos2.y;
		return MathUtils.sqrt(d0 * d0 + d1 * d1);
	}

	public static double getDistance(double px1, double py1, double pz1, double px2, double py2, double pz2) {
		double d0 = px1 - px2;
		double d1 = py1 - py2;
		double d2 = pz1 - pz2;
		return Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
	}

	public static float getDistance(float px1, float py1, float pz1, float px2, float py2, float pz2) {
		float d0 = px1 - px2;
		float d1 = py1 - py2;
		float d2 = pz1 - pz2;
		return MathUtils.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
	}

	public static double distanceManhattan(Vector3d pos1, Vector3d pos2) {
		double d0 = Math.abs(pos1.x - pos2.x);
		double d1 = Math.abs(pos1.y - pos2.y);
		double d2 = Math.abs(pos1.z - pos2.z);
		return d0 + d1 + d2;
	}

	public static float distanceManhattan(Vector3f pos1, Vector3f pos2) {
		float d0 = Math.abs(pos1.x - pos2.x);
		float d1 = Math.abs(pos1.y - pos2.y);
		float d2 = Math.abs(pos1.z - pos2.z);
		return d0 + d1 + d2;
	}

	public static int distanceManhattan(Vector3i pos1, Vector3i pos2) {
		int d0 = Math.abs(pos1.x - pos2.x);
		int d1 = Math.abs(pos1.y - pos2.y);
		int d2 = Math.abs(pos1.z - pos2.z);
		return d0 + d1 + d2;
	}

	public static double normalizeFast(double value, double min, double max) {
		if (value == min || value == max) {
			return value;
		} else if (value < 0) {
			return max - (Math.abs(value) % max);
		} else {
			return value % max;
		}
	}

	public static float normalizeFast(float value, float min, float max) {
		if (value == min || value == max) {
			return value;
		} else if (value < 0) {
			return max - (Math.abs(value) % max);
		} else {
			return value % max;
		}
	}

	public static double normalizeSlow(double value, double min, double max) {
		max -= min;
		while (value > max) {
			value -= max;
		}
		while (value < 0) {
			value += max;
		}
		return value + min;
	}

	public static float normalizeSlow(float value, float min, float max) {
		max -= min;
		while (value > max) {
			value -= max;
		}
		while (value < 0) {
			value += max;
		}
		return value + min;
	}

	public static int normalizeSlow(int value, int min, int max) {
		max -= min;
		while (value > max) {
			value -= max;
		}
		while (value < 0) {
			value += max;
		}
		return value + min;
	}

	public static float fmod(float a, float b) {
		final int result = (int) Math.floor(a / b);
		return a - result * b;
	}
}
