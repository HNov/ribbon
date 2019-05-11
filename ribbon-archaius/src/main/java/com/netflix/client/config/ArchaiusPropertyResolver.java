package com.netflix.client.config;

import com.netflix.config.ConfigurationManager;
import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.commons.configuration.event.ConfigurationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ArchaiusPropertyResolver implements PropertyResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ArchaiusPropertyResolver.class);

    public static final ArchaiusPropertyResolver INSTANCE = new ArchaiusPropertyResolver();

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        LOG.debug("Loading property {}", key);

        if (Integer.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getInteger(key, null));
        } else if (Boolean.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getBoolean(key, null));
        } else if (Float.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getFloat(key, null));
        } else if (Long.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getLong(key, null));
        } else if (Double.class.equals(type)) {
            return Optional.ofNullable((T) ConfigurationManager.getConfigInstance().getDouble(key, null));
        } else if (TimeUnit.class.equals(type)) {
            return Optional.ofNullable((T) TimeUnit.valueOf(ConfigurationManager.getConfigInstance().getString(key, null)));
        } else {
            return Optional.ofNullable(ConfigurationManager.getConfigInstance().getStringArray(key))
                    .filter(ar -> ar.length > 0)
                    .map(ar -> Arrays.stream(ar).collect(Collectors.joining(",")))
                    .map(value -> {
                        if (type.equals(String.class)) {
                            return (T)value;
                        } else {
                            return PropertyResolver.resolveWithValueOf(type, value)
                                    .orElseThrow(() -> new IllegalArgumentException("Unable to convert value to desired type " + type));
                        }
                    });
        }    }

    @Override
    public void onChange(Runnable action) {
        ConfigurationManager.getConfigInstance().addConfigurationListener(new ConfigurationListener() {
            @Override
            public void configurationChanged(ConfigurationEvent event) {
                if (!event.isBeforeUpdate()) {
                    action.run();
                }
            }
        });
    }
}