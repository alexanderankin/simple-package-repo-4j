package simple.repo.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Arch {
    amd64,
    arm64,
    /**
     * <code>sudo apt install qemu-system-arm</code>
     */
    arm,
    /**
     * <code>sudo apt install qemu-system-misc</code>
     */
    riscv64,
    /**
     * package intended for all architectures
     */
    unknown,
    ;

    private static final Map<String, Arch> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));



    public static Arch current() {
        return MAP.getOrDefault(System.getProperty("os.arch"), unknown);
    }
}
