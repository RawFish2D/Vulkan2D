package ua.rawfish2d.vk2d;

import lombok.Getter;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWVidMode;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;

@Getter
public class WindowVK {
	private long handle;
	private boolean fullScreen = false;
	private int displayWidth;
	private int displayHeight;

	public void create(int width, int height, String title) {
		if (!glfwInit()) {
			throw new RuntimeException("Failed to init GLFW!");
		}
		if (!glfwVulkanSupported()) {
			throw new AssertionError("GLFW failed to find the Vulkan loader");
		}

		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);

		displayWidth = width;
		displayHeight = height;
		handle = glfwCreateWindow(width, height, title, 0, 0);
	}

	public void showWindow() {
		glfwShowWindow(handle);
	}

	public void setFullscreen(boolean fullscreen, boolean windowed, int width, int height) {
		if (fullscreen != this.fullScreen) {
			if (this.handle != 0L) {
				this.fullScreen = fullscreen;
				final long monitor = glfwGetPrimaryMonitor();
				final GLFWVidMode mode = glfwGetVideoMode(monitor);
				if (mode == null) {
					return;
				}
				if (fullscreen) {
					if (windowed) {
						glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_FALSE);
						glfwSetWindowMonitor(handle, 0L, 0, 0, mode.width(), mode.height(), mode.refreshRate());
						resizeWindow(mode.width(), mode.height());
					} else {
						glfwSetWindowMonitor(handle, monitor, 0, 0, width, height, mode.refreshRate());
					}
				} else {
					glfwSetWindowAttrib(handle, GLFW_DECORATED, GLFW_TRUE);
					glfwSetWindowMonitor(handle, 0L, 0, 0, displayWidth, displayHeight, mode.refreshRate());
					resizeWindow(width, height);
					glfwSetWindowPos(handle, 50, 50);
				}

//				setVsync(vsync);
			}
		}
	}

	public void setFullscreen(boolean fullscreen, boolean windowed) {
		if (fullscreen != this.fullScreen) {
			if (this.handle != 0L) {
				final long monitor = glfwGetPrimaryMonitor();
				final GLFWVidMode mode = glfwGetVideoMode(monitor);
				if (mode == null) {
					return;
				}
				setFullscreen(fullscreen, windowed, mode.width(), mode.height());
			}
		}
	}

	public void resizeWindow(int w, int h) {
		if (handle != 0L) {
			glfwSetWindowSize(handle, w, h);
			displayWidth = w;
			displayHeight = h;
		}
	}

	public void setFramebufferSizeCallback(GLFWFramebufferSizeCallback framebufferSizeCallback) {
		glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback);
	}

	public void setKeyCallback(GLFWKeyCallback keyCallback) {
		glfwSetKeyCallback(handle, keyCallback);
	}

	public void getFramebufferSize(IntBuffer widthOut, IntBuffer heightOut) {
		glfwGetFramebufferSize(handle, widthOut, heightOut);
	}

	public boolean shouldClose() {
		return glfwWindowShouldClose(handle);
	}

	public void destroyWindow() {
		glfwDestroyWindow(handle);
	}

	public void terminate() {
		glfwTerminate();
	}
}
