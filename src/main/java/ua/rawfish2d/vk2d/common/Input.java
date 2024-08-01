package ua.rawfish2d.vk2d.common;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Input {
	private final List<KeyBind> keys = new ArrayList<>();

	public void keyUpdate(int key, int scancode, int action) {
		//System.out.printf("key %d scancode %d action %d\n", key, scancode, action);
		if (keys.stream().noneMatch(k -> k.getKey() == key)) {
			final KeyBind keyBind = new KeyBind(key);
			keyBind.onAction(action);
			keys.add(keyBind);
			System.out.println("added new keybind " + (char) key);
		} else {
			Optional<KeyBind> optionalKeyBind = keys.stream().filter(k -> k.getKey() == key).findFirst();
			if (optionalKeyBind.isPresent()) {
				final KeyBind keyBind = optionalKeyBind.get();
				keyBind.onAction(action);
				//System.out.println("keybind pressed " + (char) key);
			}
		}
	}

	public boolean isPressed(int key) {
		Optional<KeyBind> optionalKeyBind = keys.stream().filter(k -> k.getKey() == key).findFirst();
		if (optionalKeyBind.isPresent()) {
			final KeyBind keyBind = optionalKeyBind.get();
			return keyBind.pressed;
		}
		return false;
	}

	public static class KeyBind {
		@Getter
		private final int key;
		private boolean pressed;

		public KeyBind(int key) {
			this.key = key;
		}

		public void onAction(int action) {
			if (action >= 1) {
				pressed = true;
			} else if (action == 0) {
				pressed = false;
			}
		}
	}
}
