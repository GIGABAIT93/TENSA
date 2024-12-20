package ua.co.tensa.modules.event;

import ua.co.tensa.Message;
import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlConfiguration;
import ua.co.tensa.config.data.EventsYAML;
import java.util.Collections;
import java.util.List;


public class EventsModule extends YamlConfiguration {

	private static YamlConfiguration config;

	public static void initialise() {
		config = EventsYAML.getInstance().getReloadedFile();
	}

	public static void reload() {
		initialise();
	}

	public static void enable() {
		initialise();
		Message.info("Events Manager module enabled");
	}

	public static void disable() {
		initialise();
		config = null;
	}

	public enum Events {
		on_join_commands("on_join_commands"), on_leave_commands("on_leave_commands"),
		on_server_switch("on_server_switch"), on_server_kick("on_server_kick"),
		on_server_running("on_server_running"), on_server_stop("on_server_stop");

		private final String key;

		Events(String key) {
			this.key = key;
		}

		public boolean enabled() {
			return config != null && config.getBoolean("events." + this.key + ".enabled");
		}

		@SuppressWarnings("rawtypes")
		public List commands() {
			ConfigurationSection section = config != null ? config.getConfigurationSection("events") : null;
			return section != null ? section.getList(this.key + ".commands") : Collections.emptyList();
		}
	}
}
