package fr.citizen.hiboupostal.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Dessin d'un parchemin médiéval (noir, or ancien, rouge sombre) sans texture externe.
 */
public final class Parchment {
    private Parchment() {}

    public static final int INK = 0xFF3B2412;        // encre brune
    public static final int INK_FAINT = 0xFF6B5535;  // encre pâle
    public static final int CRIMSON = 0xFF7A1212;    // rouge sombre
    public static final int GOLD = 0xFFB08D3C;       // or ancien
    private static final int FRAME = 0xFF120C06;     // cadre noir
    private static final int PAPER = 0xFFE9D9B1;
    private static final int PAPER_DARK = 0xFFD8C390;

    public static void draw(GuiGraphics g, int x, int y, int w, int h) {
        // ombre portée
        g.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x66000000);
        // cadre noir + liseré d'or
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, FRAME);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, GOLD);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, FRAME);
        // papier
        g.fill(x, y, x + w, y + h, PAPER);
        // bords vieillis
        g.fill(x, y, x + w, y + 3, PAPER_DARK);
        g.fill(x, y + h - 3, x + w, y + h, PAPER_DARK);
        g.fill(x, y, x + 3, y + h, PAPER_DARK);
        g.fill(x + w - 3, y, x + w, y + h, PAPER_DARK);
        // ornements dorés aux coins
        corner(g, x + 5, y + 5);
        corner(g, x + w - 10, y + 5);
        corner(g, x + 5, y + h - 10);
        corner(g, x + w - 10, y + h - 10);
    }

    private static void corner(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y, x + 3, y + 5, GOLD);
        g.fill(x, y + 2, x + 5, y + 3, GOLD);
        g.fill(x + 2, y + 2, x + 3, y + 3, CRIMSON);
    }

    /** Ligne décorative dorée. */
    public static void rule(GuiGraphics g, int x1, int x2, int y) {
        g.fill(x1, y, x2, y + 1, GOLD);
        int mid = (x1 + x2) / 2;
        g.fill(mid - 2, y - 1, mid + 2, y + 2, CRIMSON);
    }

    /** Sceau de cire rouge. */
    public static void seal(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 6, cy - 4, cx + 6, cy + 4, 0xFF8E1414);
        g.fill(cx - 4, cy - 6, cx + 4, cy + 6, 0xFF8E1414);
        g.fill(cx - 5, cy - 5, cx + 5, cy + 5, 0xFF8E1414);
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFB52222);
        g.fill(cx - 1, cy - 2, cx + 1, cy + 2, 0xFF5E0B0B);
        g.fill(cx - 2, cy - 1, cx + 2, cy + 1, 0xFF5E0B0B);
    }
}
