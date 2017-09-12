package com.alexjck.plugins.conditional;

import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.PluginModule;

import java.util.Collection;
import java.util.Collections;

public class ConditionalCountAlertConditionPlugin implements Plugin {
    @Override
    public PluginMetaData metadata() {
        return new ConditionalCountAlertConditionMetaData();
    }

    @Override
    public Collection<PluginModule> modules () {
        return Collections.<PluginModule>singletonList(new ConditionalCountAlertConditionModule());
    }
}
