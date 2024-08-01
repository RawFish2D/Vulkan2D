package ua.rawfish2d;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import ua.rawfish2d.vk2d.VkBuffer;
import ua.rawfish2d.vk2d.attrib.AttribFormat;
import ua.rawfish2d.vk2d.common.PredictableRandom;
import ua.rawfish2d.vk2d.init.VkDeviceInstance;
import ua.rawfish2d.vk2d.utils.TimeHelper;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class BulletTest {
	public final List<Bullet> bullets;
	public final int windowWidth;
	public final int windowHeight;
	private final VkBuffer vkVertexBuffer;
	private final VkBuffer vkIndexBuffer;
	private final VkBuffer vkSSBO;
	private final AttribFormat attribFormat;
	private final int verticesPerObject = 4;
	private final TimeHelper updateTimer = new TimeHelper();
	public boolean pause = false;

	public BulletTest(int windowWidth, int windowHeight, int bulletCount, VkBuffer vkVertexBuffer, VkBuffer vkIndexBuffer, VkBuffer vkSSBO, VkDeviceInstance vkDeviceInstance, AttribFormat attribFormat) {
		this.bullets = new ArrayList<>(bulletCount);
		this.windowWidth = windowWidth;
		this.windowHeight = windowHeight;
		this.vkVertexBuffer = vkVertexBuffer;
		this.vkIndexBuffer = vkIndexBuffer;
		this.vkSSBO = vkSSBO;
		this.attribFormat = attribFormat;
		createBullets(bulletCount);
		updateAll();
	}

	public void resetBullets() {
		PredictableRandom.setSeed(0);
		for (Bullet bullet : bullets) {
			bullet.randomPos();
			bullet.randomMotion();
		}
		updateAll();
	}

	private void createBullets(int bulletsCount) {
		bullets.clear();
		PredictableRandom.setSeed(0);
		for (int a = 0; a < bulletsCount; ++a) {
			Bullet bullet = new Bullet();
			bullet.randomPos();
			bullet.randomMotion();
			bullets.add(bullet);
		}
	}

	private float uv(float x, float size) {
		return (1f / size) * x;
	}

	public void updateVertex() {
		final ByteBuffer vertexBuffer = vkVertexBuffer.getStagingBuffer();
		final float halfSize = 24f / 2f;
		int pos = 0;
		pos -= 4;
		for (int a = 0; a < 10; ++a) {
			final float x0 = -halfSize;
			final float y0 = -halfSize;
			final float x1 = halfSize;
			final float y1 = halfSize;
			vertexBuffer.putFloat(pos += 4, x0).putFloat(pos += 4, y0);
			vertexBuffer.putFloat(pos += 4, x0).putFloat(pos += 4, y1);
			vertexBuffer.putFloat(pos += 4, x1).putFloat(pos += 4, y1);
			vertexBuffer.putFloat(pos += 4, x1).putFloat(pos += 4, y0);
		}
	}

	public void updateTexCoords() {
		final ByteBuffer vertexBuffer = vkVertexBuffer.getStagingBuffer();
		// update texture coords
		int pos = attribFormat.getSequentialAttribPosition(1);
		pos -= 4;
		for (int a = 0; a < 10; ++a) {
			final float u0 = uv(a * 32f, 320f);
			final float u1 = uv((a + 1) * 32f, 320f);
			final float v0 = 0f;
			final float v1 = 1f;
			vertexBuffer.putFloat(pos += 4, u0).putFloat(pos += 4, v0);
			vertexBuffer.putFloat(pos += 4, u0).putFloat(pos += 4, v1);
			vertexBuffer.putFloat(pos += 4, u1).putFloat(pos += 4, v1);
			vertexBuffer.putFloat(pos += 4, u1).putFloat(pos += 4, v0);
		}
	}

	public void updateSSBO() {
		vkSSBO.getStagingBuffer().clear();
		final FloatBuffer ssbo = vkSSBO.getStagingBuffer().asFloatBuffer();
		int pos = 0;
		for (final Bullet bullet : bullets) {
			bullet.update();
			final float x = bullet.pos.x;
			final float y = bullet.pos.y;
			ssbo.put(pos++, x).put(pos++, y);
		}
	}

	public void updateIndexBuffer() {
		final ByteBuffer indexBuffer = vkIndexBuffer.getStagingBuffer();
		indexBuffer.clear();
		for (int a = 0; a < bullets.size() * verticesPerObject; a += verticesPerObject) {
			indexBuffer.putInt(a);
			indexBuffer.putInt(a + 1);
			indexBuffer.putInt(a + 2);
			indexBuffer.putInt(a + 2);
			indexBuffer.putInt(a + 3);
			indexBuffer.putInt(a);
		}
		int remaining = indexBuffer.remaining();
		if (remaining != 0) {
			System.out.println("indexBuffer remaining: " + remaining);
		}
		indexBuffer.flip();
	}

	public void updateAll() {
		// upload data
		vkVertexBuffer.getStagingBuffer().clear();
		updateVertex();
		updateTexCoords();
		vkSSBO.getStagingBuffer().clear();
		updateSSBO();

		updateIndexBuffer();
	}

	public void uploadBuffers(MemoryStack stack, VkCommandBuffer vkCommandBuffer) {
		vkVertexBuffer.uploadFromStagingBuffer(stack, vkCommandBuffer);
		vkIndexBuffer.uploadFromStagingBuffer(stack, vkCommandBuffer);
	}

	public boolean shouldUpdate() {
		if (pause) {
			return false;
		}
		if (updateTimer.hasReachedMilli(1000 / 75)) {
			updateTimer.reset();
			return true;
		}
		return false;
	}

	public void updateBulletPos() {
//		updateVertex();
		updateSSBO();
	}

	public void uploadBulletPos(MemoryStack stack, VkCommandBuffer commandBuffer) {
		vkSSBO.uploadFromStagingBuffer(stack, commandBuffer);
	}
}
