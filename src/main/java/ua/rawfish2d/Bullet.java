package ua.rawfish2d;

import org.joml.Vector2f;
import ua.rawfish2d.vk2d.common.PredictableRandom;

public class Bullet {
	public final Vector2f pos = new Vector2f();
	public final Vector2f motion = new Vector2f();

	public void setPos(float x, float y) {
		pos.set(x, y);
	}

	public void update() {
		pos.x += motion.x;
		pos.y += motion.y;

		if (pos.x < -BulletInfo.size.x || pos.x > BulletInfo.windowWidth + BulletInfo.size.x ||
				pos.y < -BulletInfo.size.y || pos.y > BulletInfo.windowHeight + BulletInfo.size.y) {
			pos.x = BulletInfo.windowWidth / 2f;
			pos.y = BulletInfo.windowHeight / 2f;
			randomMotion();
		}
	}

	public void randomMotion() {
		float speed = PredictableRandom.random(1.0f, 3.0f);
		float rngAngle = PredictableRandom.random(0.0f, 360.0f);
		float rad = (float) Math.toRadians(rngAngle);
		float x = (float) (Math.cos(rad) * speed);
		float y = (float) (Math.sin(rad) * speed);
		motion.set(x, y);
	}

	public void randomPos() {
		float rngX = PredictableRandom.random(0f, BulletInfo.windowWidth);
		float rngY = PredictableRandom.random(0f, BulletInfo.windowHeight);
		pos.set(rngX, rngY);
	}

	public static class BulletInfo {
		public static Vector2f size = new Vector2f(24f, 24f);
		public static float windowWidth = 1024;
		public static float windowHeight = 768;
	}
}