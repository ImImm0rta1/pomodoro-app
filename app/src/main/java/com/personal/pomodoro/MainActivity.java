package com.personal.pomodoro;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

public class MainActivity extends Activity {
    private static final int PICK = 7;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final String[] ORD = {"1st", "2nd", "3rd", "4th", "5th", "6th"};

    // 5x7 dot-matrix digits, rows separated by commas
    private static final String[] GLYPHS = {
            "01110,10001,10011,10101,11001,10001,01110",
            "00100,01100,00100,00100,00100,00100,01110",
            "01110,10001,00001,00010,00100,01000,11111",
            "11110,00001,00001,01110,00001,00001,11110",
            "00010,00110,01010,10010,11111,00010,00010",
            "11111,10000,11110,00001,00001,10001,01110",
            "00110,01000,10000,11110,10001,10001,01110",
            "11111,00001,00010,00100,01000,01000,01000",
            "01110,10001,10001,01110,10001,10001,01110",
            "01110,10001,10001,01111,00001,00010,01100"
    };
    private static final String DASH = "00000,00000,00000,11111,00000,00000,00000";

    private List<Scheduler.S> list;
    private FrameLayout frame;
    private ImageView artView;
    private ScrollView scroll;
    private LinearLayout content, dock;
    private int tab = 0, themeIdx = 0;
    private boolean dark, lightBars, dots, ticks;
    private int bg, card, ink, mut, line, acc, acc2, accSoft, onAcc, swOn, swThumbOn, glow;
    private int hBg, hInk, hMut, hLine, hAcc, hHead;
    private int[] border;
    private int[][] blobs;

    private long pendingId;
    private int pendingSlot;                 // 0 = first alert, k = extra alert k-1, -1 = new (draft) alert
    private Scheduler.Alert draft;
    private long draftFor;
    private int homePage = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<CountdownView> countdowns = new ArrayList<>();
    private long watchNext, watchPause;
    private final Runnable ticker = new Runnable() {
        @Override public void run() { tickNow(); }
    };

    // ---------- lifecycle ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        themeIdx = getSharedPreferences(Scheduler.PREFS, 0).getInt("theme", 0);
        if (themeIdx < 0 || themeIdx >= Theme.ALL.length) themeIdx = 0;

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        list = Scheduler.load(this);
        frame = new FrameLayout(this);
        setContentView(frame);
        artView = new ImageView(this);
        artView.setScaleType(ImageView.ScaleType.FIT_XY);
        artView.setAdjustViewBounds(true);
        frame.addView(artView, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        applyTheme();

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        frame.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(20), dp(16), dp(110));
        scroll.addView(content);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dlp.bottomMargin = dp(16);
        frame.addView(dock, dlp);

        Scheduler.schedule(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void tickNow() {
        if (countdowns.isEmpty()) return;
        long now = System.currentTimeMillis();
        if ((watchNext > 0 && now >= watchNext) || (watchPause > 0 && now >= watchPause)) {
            render();
            return;
        }
        for (CountdownView c : countdowns) c.invalidate();
        handler.postDelayed(ticker, 1000 - (now % 1000) + 20);
    }

    // ---------- theme ----------

    private void applyTheme() {
        Theme t = Theme.ALL[themeIdx];
        dark = t.dark; lightBars = t.lightBars; dots = t.dots; ticks = t.ticks;
        bg = t.bg; card = t.card; ink = t.ink; mut = t.mut; line = t.line;
        acc = t.acc; acc2 = t.acc2; accSoft = t.accSoft; onAcc = t.onAcc; swOn = t.swOn; swThumbOn = t.swThumbOn; glow = t.glow;
        hBg = t.hBg; hInk = t.hInk; hMut = t.hMut; hLine = t.hLine; hAcc = t.hAcc; hHead = t.hHead;
        border = t.border; blobs = t.blobs;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(!lightBars ? 0
                : (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        if (frame != null) frame.setBackground(backdrop());
        if (artView != null) {
            if (t.art == 0) {
                artView.setVisibility(View.GONE);
            } else {
                artView.setImageResource(t.art);
                FrameLayout.LayoutParams alp = (FrameLayout.LayoutParams) artView.getLayoutParams();
                alp.gravity = t.artTop ? Gravity.TOP : Gravity.BOTTOM;
                artView.setLayoutParams(alp);
                artView.setVisibility(View.VISIBLE);
            }
        }
    }

    /** Page colour plus a few soft radial glows, so no image files are needed. */
    private Drawable backdrop() {
        List<Drawable> layers = new ArrayList<>();
        GradientDrawable solid = new GradientDrawable();
        solid.setColor(bg);
        layers.add(solid);
        int w = getResources().getDisplayMetrics().widthPixels;
        for (int[] b : blobs) {
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientRadius(w * b[3] / 100f);
            g.setGradientCenter(b[1] / 100f, b[2] / 100f);
            g.setColors(new int[]{b[0], b[0] & 0x00FFFFFF});
            layers.add(g);
        }
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    // ---------- state ----------

    private void persist() {
        Scheduler.save(this, list);
        Scheduler.schedule(this);
    }

    private void changed() {
        persist();
        render();
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, PICK);
        } catch (Exception e) {
            // no file picker available
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            // keep going; the file may still be readable for now
        }
        String name = displayName(uri);
        for (Scheduler.S s : list) {
            if (s.id != pendingId) continue;
            if (pendingSlot == 0) {
                s.snd1 = uri.toString();
                s.n1 = name;
            } else if (pendingSlot == -1) {
                if (draft != null) { draft.uri = uri.toString(); draft.name = name; }
                render();
                return;
            } else {
                int k = pendingSlot - 1;
                if (k >= 0 && k < s.extra.size()) {
                    s.extra.get(k).uri = uri.toString();
                    s.extra.get(k).name = name;
                }
            }
            changed();
            return;
        }
    }

    private String displayName(Uri uri) {
        Cursor cur = null;
        try {
            cur = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception e) {
            // ignore
        } finally {
            if (cur != null) cur.close();
        }
        return "Custom file";
    }

    private void batterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e2) {
                // ignore
            }
        }
    }

    private void notificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Exception e) {
            // ignore
        }
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(frame.getWindowToken(), 0);
        } catch (Exception e) {
            // ignore
        }
    }

    // ---------- small view helpers ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private LinearLayout.LayoutParams margin(LinearLayout.LayoutParams p, int l, int t, int r, int b) {
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private TextView tv(String s, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private GradientDrawable box(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private void ripple(View v, Drawable shape) {
        v.setBackground(new RippleDrawable(
                ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000), shape, null));
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private LinearLayout panel() {
        LinearLayout c = column();
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(box(card, 20, line));
        return c;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(line);
        return d;
    }

    private TextView pill(String label, boolean filled, View.OnClickListener l) {
        TextView t = tv(label, 14, filled ? onAcc : acc, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        ripple(t, box(filled ? acc : accSoft, 20, 0));
        t.setOnClickListener(l);
        return t;
    }

    private void styleSwitch(Switch sw) {
        int[][] states = new int[][]{{android.R.attr.state_checked}, {}};
        sw.setTrackTintList(new ColorStateList(states, new int[]{swOn, dark ? 0xFF3A3F45 : 0xFFC4C9CE}));
        sw.setThumbTintList(new ColorStateList(states, new int[]{swThumbOn, 0xFFFFFFFF}));
    }

    private String fmt(int m) {
        int h = m / 60, mm = m % 60;
        return (h % 12 == 0 ? 12 : h % 12) + ":" + (mm < 10 ? "0" : "") + mm + (h < 12 ? " AM" : " PM");
    }

    private String clock(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return fmt(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
    }

    private boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance();
        Calendar y = Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR)
                && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    private String everyText(Scheduler.S s) {
        return "every " + s.everyVal + (s.everyUnit == 1 ? (s.everyVal == 1 ? " hr" : " hrs") : " min");
    }

    private View sectionLabel(String s) {
        TextView t = tv(s.toUpperCase(), 12, onPageMut(), true);
        t.setLetterSpacing(0.14f);
        return t;
    }

    /** Text colour for labels that sit directly on the page background (not inside a card). */
    private int onPageMut() {
        return lightBars ? mut : 0xFFC9C3D6;
    }

    private int onPageInk() {
        return lightBars ? ink : 0xFFFFFFFF;
    }

    private Scheduler.S byId(long id) {
        for (Scheduler.S s : list) if (s.id == id) return s;
        return null;
    }

    private EditText numberBox(int value, int maxLen) {
        EditText e = new EditText(this);
        e.setText(String.valueOf(value));
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setSelectAllOnFocus(true);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setTextColor(ink);
        e.setTextSize(16);
        e.setTypeface(null, Typeface.BOLD);
        e.setPadding(dp(8), dp(8), dp(8), dp(8));
        e.setBackground(box(lightBars || !dark ? 0x0F000000 : 0x22FFFFFF, 12, line));
        e.setHighlightColor(accSoft);
        GradientDrawable cursor = new GradientDrawable();
        cursor.setColor(acc);
        cursor.setSize(dp(2), dp(22));
        e.setTextCursorDrawable(cursor);
        return e;
    }

    private void onNumber(EditText e, final IntConsumer cb) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable ed) {
                try {
                    int v = Integer.parseInt(ed.toString().trim());
                    if (v > 0) cb.accept(v);
                } catch (Exception ex) {
                    // ignore partial input
                }
            }
        });
    }

    /** Two-option selector, e.g. Seconds | Minutes. */
    private View segment(String[] labels, int selected, final IntConsumer onSelect) {
        LinearLayout seg = row();
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        seg.setBackground(box(accSoft, 18, 0));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean sel = i == selected;
            TextView t = tv(labels[i], 13, sel ? onAcc : mut, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(12), dp(8), dp(12), dp(8));
            if (sel) t.setBackground(box(acc, 16, 0));
            t.setOnClickListener(v -> onSelect.accept(idx));
            seg.addView(t);
        }
        return seg;
    }

    // ---------- render ----------

    private void render() {
        handler.removeCallbacks(ticker);
        countdowns.clear();
        watchNext = 0;
        watchPause = 0;
        content.removeAllViews();
        if (tab == 0) home();
        else if (tab == 1) schedulesTab();
        else settingsTab();
        buildDock();
        if (!countdowns.isEmpty()) handler.postDelayed(ticker, 1000);
    }

    // ---------- dock ----------

    private void buildDock() {
        dock.removeAllViews();
        dock.setPadding(dp(8), dp(8), dp(8), dp(8));
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setBackground(box(card | 0xFF000000, 32, line));
        dock.setElevation(dp(10));
        String[] names = {"Home", "Schedules", "Settings"};
        for (int i = 0; i < 3; i++) {
            dock.addView(dockItem(i, names[i]), margin(lp(WRAP, WRAP, 0), i == 0 ? 0 : 4, 0, 0, 0));
        }
    }

    private View dockItem(final int i, String name) {
        boolean sel = i == tab;
        LinearLayout it = row();
        it.setPadding(dp(sel ? 16 : 14), dp(10), dp(sel ? 18 : 14), dp(10));
        if (sel) it.setBackground(box(accSoft, 24, 0));
        it.addView(new IconView(this, i, sel ? acc : mut), new LinearLayout.LayoutParams(dp(24), dp(24)));
        if (sel) it.addView(tv(name, 14, acc, true), margin(lp(WRAP, WRAP, 0), 8, 0, 0, 0));
        it.setOnClickListener(v -> { tab = i; render(); scroll.scrollTo(0, 0); });
        return it;
    }

    // ---------- home ----------

    private Drawable heroBg() {
        GradientDrawable base = box(hBg, 24, border == null ? hLine : 0);
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(dp(260));
        g.setGradientCenter(0.15f, 0.1f);
        g.setColors(new int[]{glow, 0x00000000});
        g.setCornerRadius(dp(24));
        return new LayerDrawable(new Drawable[]{base, g});
    }

    private View withBorder(View inner) {
        if (border == null) return inner;
        LinearLayout outer = column();
        outer.setPadding(dp(2), dp(2), dp(2), dp(2));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, border);
        g.setCornerRadius(dp(26));
        outer.setBackground(g);
        outer.addView(inner);
        return outer;
    }

    private View statCard(String big, String small) {
        LinearLayout c = panel();
        c.addView(tv(big, 24, ink, true));
        c.addView(tv(small, 12, mut, false));
        return c;
    }

    private void home() {
        long now = System.currentTimeMillis();
        final boolean master = Scheduler.master(this);
        final long pauseUntil = Scheduler.pauseUntil(this);
        final boolean paused = master && pauseUntil > now;
        final int vp = getResources().getDisplayMetrics().widthPixels - dp(32);

        // header
        LinearLayout hd = row();
        LinearLayout ttl = column();
        ttl.addView(tv("Break Reminder", 22, onPageInk(), true));
        ttl.addView(tv(!master ? "Reminders are off"
                : paused ? "Paused until " + clock(pauseUntil) : "Reminders are on", 13, onPageMut(), false));
        hd.addView(ttl, lp(0, WRAP, 1f));
        Switch sw = new Switch(this);
        styleSwitch(sw);
        sw.setChecked(master);
        sw.setOnCheckedChangeListener((v, checked) -> {
            Scheduler.setMaster(this, checked);
            Scheduler.schedule(this);
            render();
        });
        hd.addView(sw);
        content.addView(hd, margin(lp(MATCH, WRAP, 0), 0, 0, 0, 16));

        // one card per active schedule
        List<Scheduler.S> cards = new ArrayList<>();
        List<long[]> nexts = new ArrayList<>();
        if (master && !paused) {
            for (Scheduler.S s : list) {
                if (!s.on || s.end <= s.start) continue;
                long[] n = Scheduler.next(this, Collections.singletonList(s), now);
                if (n != null) { cards.add(s); nexts.add(n); }
            }
        }

        if (cards.isEmpty()) {
            String cap = !master ? "OFF" : paused ? "PAUSED" : "NO ALERTS";
            String msg = !master ? "Switch reminders on above"
                    : paused ? "Resumes at " + clock(pauseUntil) : "Turn on a schedule to begin";
            content.addView(statusCard(cap, msg));
            watchPause = paused ? pauseUntil : 0;
        } else {
            final int cw = cards.size() == 1 ? vp : (int) (vp * 0.84f);
            final int step = cw + dp(12);
            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setPadding(0, 0, Math.max(0, vp - cw), 0);
            long soonest = Long.MAX_VALUE;
            int soonestIdx = 0;
            for (int i = 0; i < cards.size(); i++) {
                Scheduler.S s = cards.get(i);
                long[] n = nexts.get(i);
                if (n[0] < soonest) { soonest = n[0]; soonestIdx = i; }
                strip.addView(heroCard(s, list.indexOf(s) + 1, n, now), margin(lp(cw, WRAP, 0), 0, 0, 12, 0));
            }
            watchNext = soonest;
            if (homePage < 0 || homePage >= cards.size()) homePage = soonestIdx;

            final Pager pager = new Pager(this);
            pager.step = step;
            pager.addView(strip);
            content.addView(pager, lp(MATCH, WRAP, 0));

            if (cards.size() > 1) {
                final View[] bars = new View[cards.size()];
                LinearLayout dotsRow = row();
                dotsRow.setGravity(Gravity.CENTER);
                for (int i = 0; i < bars.length; i++) {
                    bars[i] = new View(this);
                    bars[i].setBackground(box(i == homePage ? acc : off(), 3, 0));
                    dotsRow.addView(bars[i], margin(lp(dp(i == homePage ? 26 : 14), dp(4), 0), 3, 0, 3, 0));
                }
                content.addView(dotsRow, margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));
                pager.onPage = p -> {
                    homePage = p;
                    for (int i = 0; i < bars.length; i++) {
                        bars[i].setBackground(box(i == p ? acc : off(), 3, 0));
                        ViewGroup.LayoutParams lpp = bars[i].getLayoutParams();
                        lpp.width = dp(i == p ? 26 : 14);
                        bars[i].setLayoutParams(lpp);
                    }
                };
            }
            pager.post(() -> {
                pager.scrollTo(homePage * step, 0);
                pager.updateScales();
            });
        }

        if (master) content.addView(pauseCard(paused, pauseUntil), margin(lp(MATCH, WRAP, 0), 0, 16, 0, 0));

        // coming up
        long from = paused ? pauseUntil : now;
        List<Long> up = master ? Scheduler.upcoming(list, from, 3) : new ArrayList<Long>();
        if (!up.isEmpty()) {
            content.addView(sectionLabel("Coming up"), margin(lp(WRAP, WRAP, 0), 4, 20, 0, 8));
            LinearLayout chips = row();
            for (int i = 0; i < up.size(); i++) {
                TextView c = tv(clock(up.get(i)), 14, ink, true);
                c.setGravity(Gravity.CENTER);
                c.setPadding(dp(14), dp(10), dp(14), dp(10));
                c.setBackground(box(card, 16, line));
                chips.addView(c, margin(lp(WRAP, WRAP, 0), 0, 0, 8, 0));
            }
            content.addView(chips);
        }

        // quick stats
        int enabledCount = 0;
        for (Scheduler.S s : list) if (s.on) enabledCount++;
        LinearLayout stats = row();
        stats.addView(statCard(String.valueOf(master ? Scheduler.leftToday(list, now) : 0), "breaks left today"),
                margin(lp(0, WRAP, 1f), 0, 0, 6, 0));
        stats.addView(statCard(enabledCount + " of " + list.size(), "schedules on"),
                margin(lp(0, WRAP, 1f), 6, 0, 0, 0));
        content.addView(stats, margin(lp(MATCH, WRAP, 0), 0, 16, 0, 0));
    }

    private int off() {
        return dark || !lightBars ? 0x55FFFFFF : 0x33000000;
    }

    private View statusCard(String caption, String message) {
        LinearLayout inner = column();
        inner.setPadding(dp(20), dp(20), dp(20), dp(20));
        inner.setBackground(heroBg());
        TextView lbl = tv(caption, 12, hMut, true);
        lbl.setLetterSpacing(0.14f);
        lbl.setIncludeFontPadding(false);
        inner.addView(lbl);
        CountdownView cv = new CountdownView(this);
        cv.set(0, 0, caption);
        countdowns.add(cv);
        inner.addView(cv, lp(MATCH, dp(220), 0));
        TextView m = tv(message, 14, hMut, false);
        m.setGravity(Gravity.CENTER);
        m.setIncludeFontPadding(false);
        inner.addView(m, margin(lp(MATCH, WRAP, 0), 0, 16, 0, 0));
        return withBorder(inner);
    }

    private View heroCard(Scheduler.S s, int number, long[] n, long now) {
        int type = (int) n[1];
        LinearLayout inner = column();
        inner.setPadding(dp(20), dp(20), dp(20), dp(20));

        // the dot and the label share one line and one vertical centre
        LinearLayout top = row();
        View dot = new View(this);
        dot.setBackground(box(hAcc, 4, 0));
        top.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView lbl = tv((type == 1 ? "Next break" : ORD[Math.min(type - 1, 5)] + " alert").toUpperCase(),
                12, hMut, true);
        lbl.setLetterSpacing(0.14f);
        lbl.setIncludeFontPadding(false);
        top.addView(lbl, margin(lp(WRAP, WRAP, 0), 8, 0, 0, 0));
        inner.addView(top);

        long total = type == 1 ? s.everyMin() * 60000L
                : (type - 2 < s.extra.size() ? s.extra.get(type - 2).seconds() * 1000L : 30000L);
        CountdownView cv = new CountdownView(this);
        cv.set(n[0], total, type == 1 ? "UNTIL BREAK" : "UNTIL " + ORD[Math.min(type - 1, 5)].toUpperCase() + " ALERT");
        countdowns.add(cv);
        inner.addView(cv, margin(lp(MATCH, dp(220), 0), 0, 4, 0, 0));

        TextView when = tv("Rings at " + clock(n[0]) + (sameDay(n[0], now) ? "" : " tomorrow"), 15, hInk, true);
        when.setGravity(Gravity.CENTER);
        when.setIncludeFontPadding(false);
        inner.addView(when, margin(lp(MATCH, WRAP, 0), 0, 16, 0, 0));
        TextView info = tv(fmt(s.start) + " to " + fmt(s.end) + ", " + everyText(s), 12, hMut, false);
        info.setGravity(Gravity.CENTER);
        info.setIncludeFontPadding(false);
        inner.addView(info, margin(lp(MATCH, WRAP, 0), 0, 6, 0, 0));

        // the card number floats in the top-right corner and does not affect the layout
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackground(heroBg());
        wrap.addView(inner, new FrameLayout.LayoutParams(MATCH, WRAP));
        TextView num = tv(String.valueOf(number), 34, (hInk & 0x00FFFFFF) | 0x66000000, true);
        num.setIncludeFontPadding(false);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.END);
        nlp.setMargins(0, dp(15), dp(20), 0);
        wrap.addView(num, nlp);
        return withBorder(wrap);
    }

    private View pauseCard(boolean paused, long pauseUntil) {
        LinearLayout c = panel();
        if (paused) {
            c.addView(tv("Paused until " + clock(pauseUntil), 16, ink, true));
            c.addView(tv("No alerts will ring until then.", 12, mut, false));
            c.addView(pill("Resume now", true, v -> {
                Scheduler.setPause(this, 0);
                Scheduler.schedule(this);
                render();
            }), margin(lp(WRAP, WRAP, 0), 0, 12, 0, 0));
            return c;
        }
        android.content.SharedPreferences sp = getSharedPreferences(Scheduler.PREFS, 0);
        c.addView(tv("Pause reminders for", 16, ink, true));
        LinearLayout r = row();
        final EditText hrs = numberBox(sp.getInt("pauseH", 1), 2);
        final EditText mins = numberBox(sp.getInt("pauseM", 0), 3);
        r.addView(hrs, lp(dp(60), dp(44), 0));
        r.addView(tv(" hr", 14, mut, true), margin(lp(WRAP, WRAP, 0), 4, 0, 10, 0));
        r.addView(mins, lp(dp(64), dp(44), 0));
        r.addView(tv(" min", 14, mut, true), margin(lp(WRAP, WRAP, 0), 4, 0, 0, 0));
        View spacer = new View(this);
        r.addView(spacer, lp(0, 1, 1f));
        r.addView(pill("Pause", true, v -> {
            int h = 0, m = 0;
            try { h = Integer.parseInt(hrs.getText().toString().trim()); } catch (Exception e) { h = 0; }
            try { m = Integer.parseInt(mins.getText().toString().trim()); } catch (Exception e) { m = 0; }
            long total = h * 60L + m;
            if (total <= 0) {
                Toast.makeText(this, "Enter a time first", Toast.LENGTH_SHORT).show();
                return;
            }
            getSharedPreferences(Scheduler.PREFS, 0).edit().putInt("pauseH", h).putInt("pauseM", m).apply();
            Scheduler.setPause(this, System.currentTimeMillis() + total * 60000L);
            Scheduler.schedule(this);
            hideKeyboard();
            render();
        }));
        c.addView(r, margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));
        return c;
    }

    // ---------- schedules ----------

    private void schedulesTab() {
        LinearLayout hd = row();
        hd.addView(tv("Schedules", 22, onPageInk(), true), lp(0, WRAP, 1f));
        hd.addView(pill("Add", true, v -> {
            Scheduler.S s = new Scheduler.S(System.currentTimeMillis(), 12 * 60, 14 * 60, 25, 0, false);
            s.extra.add(new Scheduler.Alert(30, 0));
            s.open = true;
            list.add(s);
            changed();
        }));
        content.addView(hd);
        content.addView(tv("Each schedule has its own times, alerts, sounds and vibration.", 13, onPageMut(), false),
                margin(lp(MATCH, WRAP, 0), 0, 2, 0, 4));
        if (list.isEmpty()) {
            content.addView(tv("No schedules yet. Tap Add to create one.", 14, onPageMut(), false),
                    margin(lp(MATCH, WRAP, 0), 0, 24, 0, 0));
        }
        for (int i = 0; i < list.size(); i++) {
            content.addView(scheduleCard(list.get(i), i), margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));
        }
    }

    private View scheduleCard(final Scheduler.S s, int index) {
        LinearLayout c = panel();

        LinearLayout head = row();
        LinearLayout titles = column();
        titles.addView(tv("Schedule " + (index + 1) + (s.open ? "  \u25B4" : "  \u25BE"), 18, ink, true));
        titles.addView(tv(fmt(s.start) + " to " + fmt(s.end) + ", " + everyText(s)
                + ", " + (s.extra.size() + 1) + (s.extra.isEmpty() ? " alert" : " alerts"), 13, mut, false));
        titles.setOnClickListener(v -> { s.open = !s.open; render(); });
        head.addView(titles, lp(0, WRAP, 1f));
        Switch sw = new Switch(this);
        styleSwitch(sw);
        sw.setChecked(s.on);
        sw.setOnCheckedChangeListener((v, checked) -> { s.on = checked; changed(); });
        head.addView(sw);
        c.addView(head);

        if (!s.open) return c;

        // window
        final int dialogTheme = dark ? android.R.style.Theme_DeviceDefault_Dialog
                : android.R.style.Theme_DeviceDefault_Light_Dialog;
        LinearLayout times = row();
        times.addView(timeBox("From", s.start, v -> new TimePickerDialog(this, dialogTheme,
                (tp, h, m) -> { s.start = h * 60 + m; changed(); },
                s.start / 60, s.start % 60, false).show()), margin(lp(0, WRAP, 1f), 0, 0, 6, 0));
        times.addView(timeBox("To", s.end, v -> new TimePickerDialog(this, dialogTheme,
                (tp, h, m) -> { s.end = h * 60 + m; changed(); },
                s.end / 60, s.end % 60, false).show()), margin(lp(0, WRAP, 1f), 6, 0, 0, 0));
        c.addView(times, margin(lp(MATCH, WRAP, 0), 0, 14, 0, 0));
        if (s.end <= s.start) {
            c.addView(tv("End must be after start.", 13, dark ? 0xFFFF8A80 : 0xFFC62828, false),
                    margin(lp(MATCH, WRAP, 0), 0, 8, 0, 0));
        }

        // every
        LinearLayout er = row();
        er.addView(tv("Every", 16, ink, true), lp(0, WRAP, 1f));
        EditText every = numberBox(s.everyVal, 3);
        onNumber(every, v -> { s.everyVal = Math.min(v, s.everyUnit == 1 ? 24 : 720); persist(); });
        er.addView(every, margin(lp(dp(64), dp(44), 0), 0, 0, 8, 0));
        er.addView(segment(new String[]{"min", "hr"}, s.everyUnit, idx -> {
            s.everyUnit = idx;
            s.everyVal = Math.min(s.everyVal, idx == 1 ? 24 : 720);
            changed();
        }));
        c.addView(er, margin(lp(MATCH, WRAP, 0), 0, 14, 0, 0));

        // sound and vibration switches
        c.addView(divider(), margin(lp(MATCH, dp(1), 0), 0, 14, 0, 0));
        c.addView(toggleRow("Sound", "Turn off to use vibration only", s.sound,
                (b, on) -> { s.sound = on; changed(); }), margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));
        c.addView(toggleRow("Vibrate", "3 pulses for the 1st alert, 2 for each later one", s.vibrate,
                (b, on) -> { s.vibrate = on; changed(); }), margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));

        // alerts
        c.addView(divider(), margin(lp(MATCH, dp(1), 0), 0, 14, 0, 0));
        TextView al = tv("ALERTS", 12, mut, true);
        al.setLetterSpacing(0.14f);
        c.addView(al, margin(lp(WRAP, WRAP, 0), 0, 12, 0, 0));

        c.addView(firstAlertCard(s), margin(lp(MATCH, WRAP, 0), 0, 8, 0, 0));
        for (int k = 0; k < s.extra.size(); k++) {
            c.addView(alertCard(s, s.extra.get(k), k + 2, k + 1, false), margin(lp(MATCH, WRAP, 0), 0, 10, 0, 0));
        }
        if (draft != null && draftFor == s.id) {
            c.addView(alertCard(s, draft, s.extra.size() + 2, -1, true), margin(lp(MATCH, WRAP, 0), 0, 10, 0, 0));
        } else if (s.extra.size() < Scheduler.MAX_EXTRA) {
            c.addView(pill("+  Add alert", false, v -> {
                draft = new Scheduler.Alert(30, 0);
                draftFor = s.id;
                render();
            }), margin(lp(WRAP, WRAP, 0), 0, 12, 0, 0));
        }

        TextView del = tv("Delete schedule", 14, dark ? 0xFFFF8A80 : 0xFFC62828, true);
        del.setPadding(0, dp(6), 0, dp(6));
        del.setOnClickListener(v -> { list.remove(s); if (draftFor == s.id) draft = null; changed(); });
        c.addView(del, margin(lp(WRAP, WRAP, 0), 0, 14, 0, 0));
        return c;
    }

    private LinearLayout miniCard(boolean highlight) {
        LinearLayout m = column();
        m.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = box(dark ? 0x14FFFFFF : 0x0A000000, 16, 0);
        g.setStroke(dp(highlight ? 2 : 1), highlight ? acc : line);
        m.setBackground(g);
        return m;
    }

    private View firstAlertCard(final Scheduler.S s) {
        LinearLayout m = miniCard(false);
        LinearLayout h = row();
        h.addView(tv("Break alert", 15, ink, true), lp(0, WRAP, 1f));
        h.addView(tv("3 pulses", 12, mut, false));
        m.addView(h);
        m.addView(tv("Rings at the start of every break.", 12, mut, false), margin(lp(MATCH, WRAP, 0), 0, 2, 0, 0));
        if (s.sound) m.addView(soundPicker(s, 0, s.snd1, s.n1), margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));
        return m;
    }

    /** number = 2 for the 2nd alert, 3 for the 3rd, ...; slot as in pendingSlot. */
    private View alertCard(final Scheduler.S s, final Scheduler.Alert a, int number, final int slot, final boolean isDraft) {
        LinearLayout m = miniCard(isDraft);
        String ord = ORD[Math.min(number - 1, 5)];

        LinearLayout h = row();
        h.addView(tv(ord + " alert" + (isDraft ? " (new)" : ""), 15, ink, true), lp(0, WRAP, 1f));
        h.addView(tv("2 pulses", 12, mut, false));
        if (!isDraft) {
            TextView rm = tv("Remove", 12, dark ? 0xFFFF8A80 : 0xFFC62828, true);
            rm.setPadding(dp(14), 0, 0, 0);
            rm.setOnClickListener(v -> { s.extra.remove(a); changed(); });
            h.addView(rm);
        }
        m.addView(h);
        m.addView(tv("Counted from the previous alert.", 12, mut, false), margin(lp(MATCH, WRAP, 0), 0, 2, 0, 0));

        LinearLayout r = row();
        r.addView(tv(ord + " alert after", 14, ink, false), lp(0, WRAP, 1f));
        EditText val = numberBox(a.value, 4);
        onNumber(val, v -> {
            a.value = v;
            clampAlert(a);
            if (!isDraft) persist();
        });
        r.addView(val, margin(lp(dp(64), dp(40), 0), 0, 0, 8, 0));
        r.addView(segment(new String[]{"sec", "min"}, a.unit, idx -> {
            a.unit = idx;
            clampAlert(a);
            if (isDraft) render(); else changed();
        }));
        m.addView(r, margin(lp(MATCH, WRAP, 0), 0, 10, 0, 0));

        if (s.sound) m.addView(soundPicker(s, slot, a.uri, a.name), margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));

        if (isDraft) {
            LinearLayout b = row();
            b.addView(pill("Confirm alert", true, v -> {
                clampAlert(a);
                s.extra.add(a);
                draft = null;
                hideKeyboard();
                changed();
            }), margin(lp(WRAP, WRAP, 0), 0, 0, 8, 0));
            b.addView(pill("Cancel", false, v -> { draft = null; hideKeyboard(); render(); }));
            m.addView(b, margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));
        }
        return m;
    }

    private void clampAlert(Scheduler.Alert a) {
        if (a.unit == 0) a.value = Math.max(5, Math.min(3600, a.value));
        else a.value = Math.max(1, Math.min(180, a.value));
    }

    private View soundPicker(final Scheduler.S s, final int slot, String uri, String name) {
        boolean custom = !uri.isEmpty();
        LinearLayout col = column();
        col.addView(tv("Sound", 13, ink, true));
        col.addView(tv(custom ? (name.isEmpty() ? "Custom file" : name) : "Default notification tone",
                12, mut, false));
        LinearLayout r = row();
        r.addView(pill("Choose file", false, v -> { pendingId = s.id; pendingSlot = slot; pick(); }),
                margin(lp(WRAP, WRAP, 0), 0, 0, 8, 0));
        if (custom) {
            r.addView(pill("Reset", false, v -> {
                if (slot == 0) { s.snd1 = ""; s.n1 = ""; }
                else if (slot == -1) { if (draft != null) { draft.uri = ""; draft.name = ""; } }
                else if (slot - 1 < s.extra.size()) { s.extra.get(slot - 1).uri = ""; s.extra.get(slot - 1).name = ""; }
                if (slot == -1) render(); else changed();
            }));
        }
        col.addView(r, margin(lp(MATCH, WRAP, 0), 0, 8, 0, 0));
        return col;
    }

    private View toggleRow(String title, String sub, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = row();
        LinearLayout t = column();
        t.addView(tv(title, 15, ink, true));
        t.addView(tv(sub, 12, mut, false));
        r.addView(t, lp(0, WRAP, 1f));
        Switch sw = new Switch(this);
        styleSwitch(sw);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        r.addView(sw);
        return r;
    }

    private View timeBox(String label, int minutes, View.OnClickListener l) {
        LinearLayout b = column();
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        ripple(b, box(dark ? 0x14FFFFFF : 0x0A000000, 14, line));
        b.addView(tv(label, 12, mut, false));
        b.addView(tv(fmt(minutes), 20, ink, true));
        b.setOnClickListener(l);
        return b;
    }

    // ---------- settings ----------

    private void settingsTab() {
        content.addView(tv("Settings", 22, onPageInk(), true));

        content.addView(sectionLabel("Theme"), margin(lp(WRAP, WRAP, 0), 4, 20, 0, 8));
        LinearLayout themeCard = panel();
        themeCard.setPadding(dp(8), dp(14), dp(8), dp(12));
        LinearLayout swatches = column();
        for (int first = 0; first < Theme.ALL.length; first += 5) {
            LinearLayout r = row();
            for (int i = first; i < first + 5; i++) {
                if (i < Theme.ALL.length) r.addView(swatch(i), lp(0, WRAP, 1f));
                else r.addView(new View(this), lp(0, 1, 1f));
            }
            swatches.addView(r, margin(lp(MATCH, WRAP, 0), 0, first == 0 ? 0 : 14, 0, 0));
        }
        themeCard.addView(swatches);
        content.addView(themeCard);

        content.addView(sectionLabel("Vibration"), margin(lp(WRAP, WRAP, 0), 4, 20, 0, 8));
        LinearLayout vib = panel();
        vib.addView(tv("Test vibration", 15, ink, true));
        vib.addView(tv("Runs the same code as a real alert. If nothing vibrates, check your phone's "
                + "Sound and vibration settings for alarm and notification vibration.", 12, mut, false));
        LinearLayout vr = row();
        vr.addView(pill("3 pulses", false, v -> Scheduler.deliver(this, 1, true)), margin(lp(WRAP, WRAP, 0), 0, 0, 8, 0));
        vr.addView(pill("2 pulses", false, v -> Scheduler.deliver(this, 2, true)));
        vib.addView(vr, margin(lp(MATCH, WRAP, 0), 0, 12, 0, 0));
        content.addView(vib);

        content.addView(sectionLabel("Reliability"), margin(lp(WRAP, WRAP, 0), 4, 20, 0, 8));
        LinearLayout rel = panel();
        rel.addView(tv("Allow background running", 15, ink, true));
        rel.addView(tv("Stops Android from delaying alerts to save battery.", 12, mut, false));
        rel.addView(pill("Open setting", false, v -> batterySettings()), margin(lp(WRAP, WRAP, 0), 0, 10, 0, 0));
        rel.addView(divider(), margin(lp(MATCH, dp(1), 0), 0, 14, 0, 0));
        rel.addView(tv("Notifications", 15, ink, true), margin(lp(WRAP, WRAP, 0), 0, 12, 0, 0));
        rel.addView(tv("Needed for the on-screen banner. Sound and vibration also work without it.", 12, mut, false));
        rel.addView(pill("Open setting", false, v -> notificationSettings()), margin(lp(WRAP, WRAP, 0), 0, 10, 0, 0));
        content.addView(rel);

        content.addView(sectionLabel("About"), margin(lp(WRAP, WRAP, 0), 4, 20, 0, 8));
        LinearLayout about = panel();
        about.addView(tv("Break Reminder 1.5", 15, ink, true));
        about.addView(tv("Works fully offline. No ads, no accounts, no internet permission. "
                + "Sounds play on the alarm volume, up to 7 seconds each. Reminders keep running after a restart.",
                12, mut, false), margin(lp(MATCH, WRAP, 0), 0, 4, 0, 0));
        content.addView(about);
    }

    private View swatch(final int idx) {
        Theme th = Theme.ALL[idx];
        boolean sel = idx == themeIdx;
        LinearLayout col = column();
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.OVAL);
        outer.setColor(th.bg);
        outer.setStroke(dp(sel ? 3 : 1), sel ? acc : line);
        GradientDrawable innerDot = new GradientDrawable();
        innerDot.setShape(GradientDrawable.OVAL);
        innerDot.setColor(th.acc);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{outer, innerDot});
        ld.setLayerInset(1, dp(11), dp(11), dp(11), dp(11));
        View v = new View(this);
        v.setBackground(ld);
        col.addView(v, new LinearLayout.LayoutParams(dp(44), dp(44)));
        col.addView(tv(th.name, 10, sel ? ink : mut, sel), margin(lp(WRAP, WRAP, 0), 0, 6, 0, 0));
        col.setOnClickListener(x -> {
            themeIdx = idx;
            getSharedPreferences(Scheduler.PREFS, 0).edit().putInt("theme", idx).apply();
            applyTheme();
            render();
        });
        return col;
    }

    // ---------- custom views ----------

    /** Horizontal card carousel: snaps to one card at a time, the next card peeks in, far cards shrink and fade. */
    private final class Pager extends HorizontalScrollView {
        int step = 1;
        int page = -1;
        int flingDir;
        IntConsumer onPage;

        Pager(Context c) {
            super(c);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
        }

        @Override
        public void fling(int velocityX) {
            flingDir = velocityX > 0 ? 1 : -1; // no free-running fling; we snap instead
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            boolean r = super.onTouchEvent(ev);
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) snap();
            return r;
        }

        private void snap() {
            ViewGroup strip = (ViewGroup) getChildAt(0);
            if (strip == null) return;
            int count = strip.getChildCount();
            float pos = getScrollX() / (float) step;
            int target = flingDir > 0 ? (int) Math.floor(pos) + 1
                    : flingDir < 0 ? (int) Math.ceil(pos) - 1 : Math.round(pos);
            flingDir = 0;
            target = Math.max(0, Math.min(count - 1, target));
            smoothScrollTo(target * step, 0);
        }

        @Override
        protected void onScrollChanged(int l, int t, int ol, int ot) {
            super.onScrollChanged(l, t, ol, ot);
            updateScales();
        }

        void updateScales() {
            ViewGroup strip = (ViewGroup) getChildAt(0);
            if (strip == null || step <= 0) return;
            int sx = getScrollX();
            int best = 0;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < strip.getChildCount(); i++) {
                View c = strip.getChildAt(i);
                float d = Math.min(1f, Math.abs(sx - i * step) / (float) step);
                float sc = 1f - 0.07f * d;
                c.setPivotX(0f);
                c.setPivotY(c.getHeight() / 2f);
                c.setScaleX(sc);
                c.setScaleY(sc);
                c.setAlpha(1f - 0.45f * d);
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best != page) {
                page = best;
                if (onPage != null) onPage.accept(best);
            }
        }
    }

    /** Dock icons, drawn with a few lines so no image files are needed. */
    private final class IconView extends View {
        private final int kind, color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconView(Context c, int kind, int color) {
            super(c);
            this.kind = kind;
            this.color = color;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = getWidth() / 24f;
            c.scale(s, s);
            p.setColor(color);
            p.setStrokeWidth(2f);
            p.setStyle(Paint.Style.STROKE);
            if (kind == 0) {
                Path roof = new Path();
                roof.moveTo(3.5f, 11f);
                roof.lineTo(12f, 4f);
                roof.lineTo(20.5f, 11f);
                c.drawPath(roof, p);
                Path body = new Path();
                body.moveTo(6f, 9.5f);
                body.lineTo(6f, 20f);
                body.lineTo(18f, 20f);
                body.lineTo(18f, 9.5f);
                c.drawPath(body, p);
            } else if (kind == 1) {
                c.drawCircle(12f, 12f, 8.5f, p);
                Path hands = new Path();
                hands.moveTo(12f, 7f);
                hands.lineTo(12f, 12f);
                hands.lineTo(15.5f, 14f);
                c.drawPath(hands, p);
            } else {
                c.drawLine(4f, 7f, 20f, 7f, p);
                c.drawLine(4f, 12f, 20f, 12f, p);
                c.drawLine(4f, 17f, 20f, 17f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(9f, 7f, 2.6f, p);
                c.drawCircle(15f, 12f, 2.6f, p);
                c.drawCircle(8f, 17f, 2.6f, p);
            }
        }
    }

    /** Progress ring with the time left. Redrawn once a second, only while Home is open. */
    private final class CountdownView extends View {
        private long target, total;
        private String caption = "";
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cap = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);

        CountdownView(Context c) {
            super(c);
            track.setStyle(Paint.Style.STROKE);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            fill.setStyle(Paint.Style.FILL);
            tick.setStyle(Paint.Style.STROKE);
            tick.setStrokeCap(Paint.Cap.ROUND);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            txt.setFontFeatureSettings("tnum");
            cap.setTextAlign(Paint.Align.CENTER);
            cap.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            cap.setLetterSpacing(0.16f);
        }

        void set(long target, long total, String caption) {
            this.target = target;
            this.total = total;
            this.caption = caption;
            invalidate();
        }

        private String two(long v) { return v < 10 ? "0" + v : String.valueOf(v); }

        private String remaining(long rem) {
            long secs = (rem + 999) / 1000;
            long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
            return h > 0 ? h + ":" + two(m) + ":" + two(s) : two(m) + ":" + two(s);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float stroke = dp(10);
            float outer = Math.min(w, h) / 2f - dp(8);
            float r = ticks ? outer - dp(16) : outer - dp(6);

            if (ticks) {
                tick.setStrokeWidth(dp(2));
                for (int i = 0; i < 60; i++) {
                    boolean major = i % 5 == 0;
                    double a = Math.toRadians(i * 6.0 - 90.0);
                    float len = major ? dp(9) : dp(5);
                    tick.setColor(major ? hAcc : hLine);
                    c.drawLine((float) (cx + (outer - len) * Math.cos(a)), (float) (cy + (outer - len) * Math.sin(a)),
                            (float) (cx + outer * Math.cos(a)), (float) (cy + outer * Math.sin(a)), tick);
                }
            }

            track.setStrokeWidth(stroke);
            track.setColor(hLine);
            c.drawCircle(cx, cy, r, track);

            long now = System.currentTimeMillis();
            boolean on = target > 0;
            long rem = on ? Math.max(0L, target - now) : 0L;
            float frac = (on && total > 0) ? Math.min(1f, Math.max(0f, 1f - (float) rem / total)) : 0f;
            if (frac > 0.002f) {
                arc.setStrokeWidth(stroke);
                arc.setColor(hAcc);
                c.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, 360f * frac, false, arc);
                double a = Math.toRadians(-90.0 + 360.0 * frac);
                fill.setColor(hHead);
                c.drawCircle((float) (cx + r * Math.cos(a)), (float) (cy + r * Math.sin(a)), stroke * 0.62f, fill);
            }

            String s = on ? remaining(rem) : "--:--";
            float maxW = r * 1.45f;
            if (dots) {
                drawDots(c, s, cx, cy - dp(6), maxW);
            } else {
                txt.setColor(hInk);
                txt.setTextSize(sp(52));
                float tw = txt.measureText(s);
                if (tw > maxW) txt.setTextSize(sp(52) * maxW / tw);
                c.drawText(s, cx, cy + txt.getTextSize() * 0.32f - dp(6), txt);
            }

            cap.setColor(hMut);
            cap.setTextSize(sp(11));
            c.drawText(caption, cx, cy + r * 0.55f, cap);
        }

        private String glyph(char ch) {
            if (ch >= '0' && ch <= '9') return GLYPHS[ch - '0'];
            if (ch == '-') return DASH;
            return null;
        }

        private void drawDots(Canvas c, String s, float cx, float cy, float maxW) {
            int cols = 0;
            for (int i = 0; i < s.length(); i++) {
                cols += glyph(s.charAt(i)) == null ? 1 : 5;
                if (i < s.length() - 1) cols += 1;
            }
            float p = Math.min(maxW / cols, (float) dp(9));
            float x = cx - cols * p / 2f;
            float y0 = cy - 7 * p / 2f;
            float rr = p * 0.36f;
            int faint = (hInk & 0x00FFFFFF) | 0x22000000;
            for (int i = 0; i < s.length(); i++) {
                String g = glyph(s.charAt(i));
                if (g == null) {
                    fill.setColor(hInk);
                    c.drawCircle(x + p / 2f, y0 + 2.5f * p, rr, fill);
                    c.drawCircle(x + p / 2f, y0 + 4.5f * p, rr, fill);
                    x += p;
                } else {
                    String[] rows = g.split(",");
                    for (int rw = 0; rw < 7; rw++) {
                        for (int k = 0; k < 5; k++) {
                            fill.setColor(rows[rw].charAt(k) == '1' ? hInk : faint);
                            c.drawCircle(x + (k + 0.5f) * p, y0 + (rw + 0.5f) * p, rr, fill);
                        }
                    }
                    x += 5 * p;
                }
                x += p;
            }
        }
    }
}
