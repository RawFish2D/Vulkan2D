package ua.rawfish2d.vk2d.common;

import lombok.Setter;
import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import ua.rawfish2d.vk2d.utils.MathUtils;

import java.nio.DoubleBuffer;

import static org.lwjgl.glfw.GLFW.*;

public class Camera {
	private final Vector3f position = new Vector3f(0f, 0f, 6f);
	private final Vector3f front = new Vector3f(0f, 0f, -1f);
	private final Vector3f up = new Vector3f(0f);
	private final Vector3f right = new Vector3f(0f);
	private final Vector3f worldUp = new Vector3f(0f, 1f, 0f);
	@Setter
	private float fov = 75f;
	// euler Angles
	@Setter
	private float yaw = 270f;
	@Setter
	private float pitch = 0f;
	// camera options
	@Setter
	private float movementSpeed = 0.1f;
	@Setter
	private float mouseSensitivity = 0.2f;
	private boolean mouseLocked = false;
	private double newX;
	private double newY;
	private final long hwnd;
	private final double screenWidthHalf;
	private final double screenHeightHalf;
	private final int windowWidth;
	private final int windowHeight;
	private final float nearPlane = 0.1f;
	private final float farPlane = 512f;
	// cache
	private final Matrix4f viewMatrix = new Matrix4f();
	private final Matrix4f projectionMatrix = new Matrix4f();
	private final Matrix4f customViewMatrix = new Matrix4f();

	public Camera(int windowWidth, int windowHeight, long windowHandle) {
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		this.screenWidthHalf = windowWidth / 2d;
		this.screenHeightHalf = windowHeight / 2d;
		this.newX = screenWidthHalf;
		this.newY = screenHeightHalf;
		this.hwnd = windowHandle;
	}

	public void setAngles(float pitch, float yaw) {
		this.pitch = pitch;
		this.yaw = yaw;
		updateCameraVectors();
	}

	public void setPos(float x, float y, float z) {
		position.set(x, y, z);
	}

	// returns the view matrix calculated using Euler Angles and the LookAt Matrix
	public Matrix4f getViewMatrix() {
		viewMatrix.identity().lookAt(position.x, position.y, position.z,
				position.x + front.x, position.y + front.y, position.z + front.z,
				up.x, up.y, up.z);
		return viewMatrix;
	}

	public Matrix4f getProjectionMatrix() {
		final float aspectRatio = (float) windowWidth / (float) windowHeight;
		projectionMatrix.identity().setPerspective(
				MathUtils.toRad(fov),
				aspectRatio, nearPlane, farPlane);
		return projectionMatrix;
	}

	public Matrix4f getMatrixForPos(float x, float y, float z) {
		customViewMatrix.identity()
				.lookAt(x, y, z,
						x + front.x, y + front.y, z + front.z,
						up.x, up.y, up.z);
		return customViewMatrix;
	}

	// processes input received from any keyboard-like input system. Accepts input parameter in the form of camera defined ENUM (to abstract it from windowing systems)
	public void processKeyboard(Input input) {
		final int forwardKey = GLFW_KEY_W;
		final int backwardKey = GLFW_KEY_S;
		final int leftKey = GLFW_KEY_A;
		final int rightKey = GLFW_KEY_D;
		final int upKey = GLFW_KEY_SPACE;
		final int downKey = GLFW_KEY_LEFT_SHIFT;

		float velocity = movementSpeed;
		if (input.isPressed(GLFW_KEY_LEFT_CONTROL)) {
			velocity *= 3f;
		}
		if (input.isPressed(GLFW_KEY_LEFT_ALT)) {
			velocity *= 10f;
		}
		if (input.isPressed(forwardKey)) {
			position.add(front.mul(velocity));
		} else if (input.isPressed(backwardKey)) {
			position.sub(front.mul(velocity));
		}

		if (input.isPressed(leftKey)) {
			position.sub(right.mul(velocity));
		} else if (input.isPressed(rightKey)) {
			position.add(right.mul(velocity));
		}

		if (input.isPressed(upKey)) {
			position.add(up.mul(velocity));
		} else if (input.isPressed(downKey)) {
			position.sub(up.mul(velocity));
		}
	}

	// processes input received from a mouse input system. Expects the offset value in both the x and y direction.
	private void processMouseMovement(double xoffset, double yoffset) {
		yaw += (float) (xoffset * mouseSensitivity);
		pitch -= (float) (yoffset * mouseSensitivity);

		// make sure that when pitch is out of bounds, screen doesn't get flipped
		if (pitch > 89.0f)
			pitch = 89.0f;
		if (pitch < -89.0f)
			pitch = -89.0f;

		// update Front, Right and Up Vectors using the updated Euler angles
		updateCameraVectors();
	}

	public void updateCameraVectors() {
		yaw = MathUtils.normalizeFast(yaw, 0f, 360f);
		pitch = MathUtils.normalizeFast(pitch + 89f, 0f, 89f * 2) - 89f;
		// calculate the new Front vector
		front.x = MathUtils.cos(MathUtils.toRad(yaw)) * MathUtils.cos(MathUtils.toRad(pitch));
		front.y = MathUtils.sin(MathUtils.toRad(pitch));
		front.z = MathUtils.sin(MathUtils.toRad(yaw)) * MathUtils.cos(MathUtils.toRad(pitch));
		VectorUtils.normalizeVector(front);
		// also re-calculate the Right and Up vector
		// normalize the vectors, because their length gets closer to 0 the more you look up or down which results in slower movement.
		VectorUtils.crossNormalize(front, worldUp, right);
		VectorUtils.crossNormalize(right, front, up);
	}

	private void normalizeVector(Vector3f inputVec) {
		float scalar = Math.invsqrt(Math.fma(inputVec.x, inputVec.x, Math.fma(inputVec.y, inputVec.y, inputVec.z * inputVec.z)));
		inputVec.x *= scalar;
		inputVec.y *= scalar;
		inputVec.z *= scalar;
	}

	public void processMouseMovement() {
		if (glfwGetMouseButton(hwnd, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS) {
			glfwSetInputMode(hwnd, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
			if (!mouseLocked) {
				glfwSetCursorPos(hwnd, screenWidthHalf, screenHeightHalf);
			}

			mouseLocked = true;
		} else {
			glfwSetInputMode(hwnd, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
			mouseLocked = false;
		}

		if (mouseLocked) {
			final DoubleBuffer x = BufferUtils.createDoubleBuffer(1);
			final DoubleBuffer y = BufferUtils.createDoubleBuffer(1);

			glfwGetCursorPos(hwnd, x, y);
			x.rewind();
			y.rewind();

			newX = x.get();
			newY = y.get();

			double deltaX = newX - screenWidthHalf;
			double deltaY = newY - screenHeightHalf;
			processMouseMovement(deltaX, deltaY);

			glfwSetCursorPos(hwnd, screenWidthHalf, screenHeightHalf);
		} else {
			processMouseMovement(0f, 0f);
		}
	}

	public Vector3f getPos() {
		return position;
	}
}
