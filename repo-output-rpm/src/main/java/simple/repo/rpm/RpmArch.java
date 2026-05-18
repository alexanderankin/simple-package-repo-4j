package simple.repo.rpm;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import simple.repo.model.Arch;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum RpmArch {
    x86_64("amd64"),
    aarch64("aarch64"),
    armhfp("arm"),
    riscv64("riscv64"),
    ;

    private static final Map<String, RpmArch> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(RpmArch::getJdkName, Function.identity()));

    private final String jdkName;

    public static RpmArch fromArch(Arch arch) {
        return MAP.get(arch.name());
    }
}
