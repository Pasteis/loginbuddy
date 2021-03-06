package net.loginbuddy.service.config;

import java.util.Properties;
import java.util.logging.Logger;

public class PropertiesUtil {

    private Logger LOGGER = Logger.getLogger(String.valueOf(PropertiesUtil.class));

    private Properties props;

    public PropertiesUtil(Properties props) {
        this.props = props;
    }

    public boolean isConfigured() {
        return props != null;
    }

    public long getLongProperty(String property) {
        LOGGER.fine(String.format("Requested property: '%s'", property));
        return Long.parseLong((String) props.get(property));
    }
}
