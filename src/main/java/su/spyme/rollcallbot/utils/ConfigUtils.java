package su.spyme.rollcallbot.utils;

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
        createSectionIfNotExist(config, section);
        return config.getConfigurationSection(section).getKeys(false).stream().toList();
    }

    public static void createSectionIfNotExist(YamlFile config, String path) {
        if (!config.contains(path)) {
            setAndSave(config, path + ".temp", 10);
            setAndSave(config, path + ".temp", null);
        }
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
