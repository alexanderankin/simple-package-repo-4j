package simple.repo;

import java.util.Objects;

public class Version {
    // it is this when not running from a jar
    public static final String DEFAULT_VERSION = "0.0.1-SNAPSHOT";

    // version when available, otherwise DEFAULT_VERSION
    public static final String VERSION = Objects.requireNonNullElse(Version.class.getPackage().getImplementationVersion(), DEFAULT_VERSION);
}
