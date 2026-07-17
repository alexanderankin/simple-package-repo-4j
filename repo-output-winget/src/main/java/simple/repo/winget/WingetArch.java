package simple.repo.winget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import simple.repo.model.Arch;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum WingetArch {
    x64(Arch.amd64),
    arm64(Arch.arm64),
    arm(Arch.arm),
    neutral(Arch.unknown);

    private static final Map<Arch, WingetArch> MAP = Arrays.stream(values())
            .collect(Collectors.toMap(WingetArch::arch, Function.identity()));
    private final Arch arch;

    public static WingetArch from(Arch arch) {
        return MAP.getOrDefault(arch, neutral);
    }
}
