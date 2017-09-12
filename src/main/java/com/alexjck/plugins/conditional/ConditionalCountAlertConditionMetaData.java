package com.alexjck.plugins.conditional;

import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

import java.net.URI;
import java.util.Collections;
import java.util.Set;

public class ConditionalCountAlertConditionMetaData implements PluginMetaData {
    private static final String PLUGIN_PROPERTIES = "com.alexjck.graylog.plugins.graylog-plugin-alert-conditional-count/graylog-plugin.properties";

    @Override
    public String getUniqueId() {
        return "com.alexjck.plugins.conditional.alerts.ConditionalCountAlertCondition";
    }

    @Override
    public String getName() {
        return "Message Conditional Count Alert Condition";
    }

    @Override
    public String getAuthor() {
        return "Alexander Campos <alexander.campos@mercadolibre.com>";
    }

    @Override
    public URI getURL() {
        return URI.create("https://github.com/alcampos/graylog-plugin-alert-conditional-count");
    }

    @Override
    public Version getVersion() {
        return Version.fromPluginProperties(getClass(), PLUGIN_PROPERTIES, "version", Version.from(0, 0, 1, "unknown"));
    }

    @Override
    public String getDescription() {
        return "This condition is triggered when there are more or less messages (matching a defined query) than the threshold.";
    }

    @Override
    public Version getRequiredVersion() {
        return Version.fromPluginProperties(getClass(), PLUGIN_PROPERTIES, "graylog.version", Version.from(2, 3, 0));
    }

    @Override
    public Set<ServerStatus.Capability> getRequiredCapabilities() {
        return Collections.emptySet();
    }
}
