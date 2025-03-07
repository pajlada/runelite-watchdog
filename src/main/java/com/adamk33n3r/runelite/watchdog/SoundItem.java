package com.adamk33n3r.runelite.watchdog;

import lombok.*;

import java.io.File;

@Getter
@RequiredArgsConstructor
public class SoundItem {
    private final File file;
    private final Integer gain;
    private final Integer repeatSeconds;
}
