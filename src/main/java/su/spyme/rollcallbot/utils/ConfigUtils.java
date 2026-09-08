package su.spyme.rollcallbot.utils;

import org.simpleyaml.configuration.ConfigurationSection;
import org.simpleyaml.configuration.file.YamlFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ConfigUtils {
    private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);

    public static YamlFile loadConfig(String name) throws IOException {
        YamlFile yamlFile = new YamlFile("storage/" + name + ".yml");
        if (!yamlFile.exists()) yamlFile.createNewFile();
        else yamlFile.load();
        return yamlFile;
    }

    public static List<String> getKeys(YamlFile config, String section) {
        ConfigurationSection configurationSection = config.getConfigurationSection(section);
        return configurationSection == null ? List.of() : configurationSection.getKeys(false).stream().toList();
    }

    public static void setAndSave(YamlFile config, String path, Object value) {
        config.set(path, value);
        try {
            config.save();
        } catch (IOException exception) {
            logger.error("Error while saving {}", config.getFilePath(), exception);
        }
    }
}
