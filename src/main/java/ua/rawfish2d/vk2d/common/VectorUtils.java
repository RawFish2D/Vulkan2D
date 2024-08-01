package ua.rawfish2d.vk2d.common;

import org.joml.Math;
import org.joml.Vector3f;

public class VectorUtils {
	public static void crossNormalize(Vector3f firstVec, Vector3f secondVec, Vector3f outputVec) {
		// cross
		final float crossX = Math.fma(firstVec.y, secondVec.z, -firstVec.z * secondVec.y);
		final float crossY = Math.fma(firstVec.z, secondVec.x, -firstVec.x * secondVec.z);
		final float crossZ = Math.fma(firstVec.x, secondVec.y, -firstVec.y * secondVec.x);

		// normalize
		final float scalar = Math.invsqrt(Math.fma(crossX, crossX, Math.fma(crossY, crossY, crossZ * crossZ)));
		outputVec.x = crossX * scalar;
		outputVec.y = crossY * scalar;
		outputVec.z = crossZ * scalar;
	}

	public static void normalizeVector(Vector3f vec) {
		final float scalar = Math.invsqrt(Math.fma(vec.x, vec.x, Math.fma(vec.y, vec.y, vec.z * vec.z)));
		vec.x *= scalar;
		vec.y *= scalar;
		vec.z *= scalar;
	}
}
