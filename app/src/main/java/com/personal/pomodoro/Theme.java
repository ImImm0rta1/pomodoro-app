package com.personal.pomodoro;

/** Colour themes, inspired by the reference designs. Pure data, no resources or libraries. */
final class Theme {
    final String name;
    final boolean dark;       // cards are dark
    final boolean lightBars;  // page background is light, so status bar icons should be dark
    final boolean dots;       // dot-matrix digits on the countdown
    final boolean ticks;      // tick marks around the countdown ring
    final int bg, card, ink, mut, line, acc, acc2, accSoft, onAcc, swOn, glow;
    final int hBg, hInk, hMut, hLine, hAcc, hHead; // home carousel card colours
    final int swThumbOn;      // switch knob colour while the switch is on
    final int[] border;       // gradient colours for the home card border, or null
    final int[][] blobs;      // soft background glows: {argb, centreX%, centreY%, radius% of width}
    final int art;            // vector drawable shown behind the content, or 0
    final boolean artTop;     // true = art sits at the top of the screen, false = at the bottom

    /** c = bg, card, ink, mut, line, acc, acc2, accSoft, onAcc, swOn, glow, hBg, hInk, hMut, hLine, hAcc, hHead, swThumbOn */
    Theme(String name, boolean dark, boolean lightBars, boolean dots, boolean ticks,
          int[] c, int[] border, int[][] blobs, int art, boolean artTop) {
        this.name = name; this.dark = dark; this.lightBars = lightBars; this.dots = dots; this.ticks = ticks;
        int i = 0;
        bg = c[i++]; card = c[i++]; ink = c[i++]; mut = c[i++]; line = c[i++];
        acc = c[i++]; acc2 = c[i++]; accSoft = c[i++]; onAcc = c[i++]; swOn = c[i++]; glow = c[i++];
        hBg = c[i++]; hInk = c[i++]; hMut = c[i++]; hLine = c[i++]; hAcc = c[i++]; hHead = c[i++];
        swThumbOn = c[i++];
        this.border = border; this.blobs = blobs; this.art = art; this.artTop = artTop;
    }

    static final Theme[] ALL = {
        // dark, warm red-orange glow
        new Theme("Ember", true, false, false, false, new int[]{
                0xFF080504, 0xE61A1210, 0xFFFFFFFF, 0xFFA89A94, 0xFF2C201C,
                0xFFFF4A2B, 0xFFFFB35C, 0x33FF4A2B, 0xFFFFFFFF, 0xFFFF4A2B, 0x66FF4A2B,
                0xFF150E0C, 0xFFFFFFFF, 0xFFA89A94, 0xFF2E211D, 0xFFFF4A2B, 0xFFFFB35C, 0xFFFFFFFF},
                null,
                new int[][]{{0x66FF5A2E, 10, 92, 85}, {0x33FFB35C, 95, 6, 60}}, 0, false),

        // graphite, dot-matrix digits, blue to yellow border
        new Theme("Matrix", true, false, true, false, new int[]{
                0xFF050608, 0xE6121316, 0xFFFFFFFF, 0xFF8A8D93, 0xFF24262B,
                0xFF3D7BFF, 0xFFF0E36B, 0x333D7BFF, 0xFFFFFFFF, 0xFF3D7BFF, 0x223D7BFF,
                0xFF0E0F12, 0xFFFFFFFF, 0xFF8A8D93, 0xFF2A2C31, 0xFF3D7BFF, 0xFFF0E36B, 0xFFFFFFFF},
                new int[]{0xFF2F6BFF, 0xFFE8E36A, 0xFF1F4FE0},
                new int[][]{{0x403D7BFF, 88, 0, 70}, {0x28F0E36B, 4, 100, 60}}, 0, false),

        // soft light pink
        new Theme("Blush", false, true, false, false, new int[]{
                0xFFFBEFF3, 0xFFFFFFFF, 0xFF2A1520, 0xFF8C6B77, 0xFFF3DCE4,
                0xFFEE3F6C, 0xFFFF9AB3, 0x1FEE3F6C, 0xFFFFFFFF, 0xFFEE3F6C, 0x40EE3F6C,
                0xFFFFFFFF, 0xFF2A1520, 0xFF8C6B77, 0xFFF3DCE4, 0xFFEE3F6C, 0xFFFF9AB3, 0xFFFFFFFF},
                null,
                new int[][]{{0x55FF9AB3, 100, 0, 80}, {0x33FFB38A, 0, 100, 70}}, 0, false),

        // frosted glass on a blurred orange / blue / pink backdrop, dark carousel cards
        new Theme("Aurora", false, true, false, false, new int[]{
                0xFFE9F1F5, 0xB3FFFFFF, 0xFF101828, 0xFF5F6B7A, 0x1A101828,
                0xFF1D5CE8, 0xFFFF8A3D, 0x1F1D5CE8, 0xFFFFFFFF, 0xFF1D5CE8, 0x55FF8A3D,
                0xFF1C1C1F, 0xFFFFFFFF, 0xFFA6A6AD, 0xFF38383D, 0xFFFF8A3D, 0xFFFFFFFF, 0xFFFFFFFF},
                null,
                new int[][]{{0x99FF7A2F, 14, 94, 72}, {0x884F8CFF, 6, 6, 66}, {0x77FF6B6B, 100, 88, 62}}, 0, false),

        // deep purple page, white cards, hot pink ring with tick marks
        new Theme("Neon", false, false, false, true, new int[]{
                0xFF1B0B33, 0xFFFFFFFF, 0xFF14101F, 0xFF7A7490, 0xFFEDE7F6,
                0xFFFF3D6E, 0xFFFFB3C7, 0x1FFF3D6E, 0xFFFFFFFF, 0xFFFF3D6E, 0x33FF3D8B,
                0xFFFFFFFF, 0xFF14101F, 0xFF7A7490, 0xFFEDE7F6, 0xFFFF3D6E, 0xFFFF3D6E, 0xFFFFFFFF},
                null,
                new int[][]{{0x66FF3D8B, 12, 0, 80}, {0x669A4DFF, 100, 100, 88}}, 0, false),

        // light grey, black and amber
        new Theme("Mono", false, true, false, false, new int[]{
                0xFFECECEC, 0xFFFFFFFF, 0xFF16181A, 0xFF7A7D80, 0xFFE2E2E2,
                0xFF16181A, 0xFFF5B83D, 0x14000000, 0xFFFFFFFF, 0xFFFFB300, 0x00000000,
                0xFFFFFFFF, 0xFF16181A, 0xFF7A7D80, 0xFFECECEC, 0xFF16181A, 0xFFF5B83D, 0xFF16181A},
                null,
                new int[][]{}, 0, false),

        // twilight sky: stars, a low sun and layered purple hills
        new Theme("Dusk", true, false, false, false, new int[]{
                0xFF1E1240, 0xE6241A4A, 0xFFFFFFFF, 0xFFB9AEDB, 0xFF3A2D66,
                0xFFFF8A5B, 0xFFFFD27A, 0x33FF8A5B, 0xFF2B1308, 0xFFFF8A5B, 0x55FF6B6B,
                0xFF170E33, 0xFFFFFFFF, 0xFFB9AEDB, 0xFF3A2D66, 0xFFFF8A5B, 0xFFFFD27A, 0xFFFFFFFF},
                null,
                new int[][]{{0x55FF6B6B, 50, 100, 90}, {0x449A4DFF, 0, 0, 70}}, R.drawable.bg_dusk, false),

        // seaside: layered waves and a low sun
        new Theme("Ocean", false, true, false, false, new int[]{
                0xFFD9F1F5, 0xCCFFFFFF, 0xFF0B2E3A, 0xFF4D7380, 0x1A0B2E3A,
                0xFF0A8FA8, 0xFFFFC857, 0x1F0A8FA8, 0xFFFFFFFF, 0xFF0A8FA8, 0x5538D5E0,
                0xFF0B3C4F, 0xFFFFFFFF, 0xFF9FC7D2, 0xFF1E5A70, 0xFF3EE0C5, 0xFFFFC857, 0xFFFFFFFF},
                null,
                new int[][]{{0x6638D5E0, 100, 0, 80}, {0x33FFC857, 0, 30, 50}}, R.drawable.bg_ocean, false),

        // cherry blossom branch across the top, plum carousel cards
        new Theme("Sakura", false, true, false, false, new int[]{
                0xFFFFF3F0, 0xF2FFFFFF, 0xFF3A2230, 0xFF8F6F7C, 0xFFF5DDE0,
                0xFFD9648A, 0xFFE7B567, 0x1FD9648A, 0xFFFFFFFF, 0xFFD9648A, 0x55FF8FB0,
                0xFF3A1F33, 0xFFFFFFFF, 0xFFD8B7C8, 0xFF58384F, 0xFFFF8FB0, 0xFFFFD6A5, 0xFFFFFFFF},
                null,
                new int[][]{{0x44FFB3C7, 100, 0, 75}}, R.drawable.bg_sakura, true),

        // night forest: pine rows and a moon
        new Theme("Forest", true, false, false, false, new int[]{
                0xFF0A1713, 0xE6162B24, 0xFFEAF5EE, 0xFF8DB0A0, 0xFF25423A,
                0xFF5EE0A0, 0xFFF2D16B, 0x335EE0A0, 0xFF062A1B, 0xFF5EE0A0, 0x445EE0A0,
                0xFF0B1A15, 0xFFEAF5EE, 0xFF8DB0A0, 0xFF25423A, 0xFF5EE0A0, 0xFFF2D16B, 0xFFFFFFFF},
                null,
                new int[][]{{0x335EE0A0, 90, 100, 70}}, R.drawable.bg_forest, false),
    };
}
