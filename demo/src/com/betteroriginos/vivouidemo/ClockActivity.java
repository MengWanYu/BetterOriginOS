package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.ui.VivoBottomBar;
import com.betteroriginos.vivouidemo.ui.VivoButton;
import com.betteroriginos.vivouidemo.ui.VivoIcons;
import com.betteroriginos.vivouidemo.ui.VivoPopup;
import com.betteroriginos.vivouidemo.ui.VivoScaffold;
import com.betteroriginos.vivouidemo.ui.VivoSheet;
import com.betteroriginos.vivouidemo.ui.VivoSwitch;
import com.betteroriginos.vivouidemo.ui.VivoTheme;
import com.betteroriginos.vivouidemo.ui.VivoToast;
import com.betteroriginos.vivouidemo.ui.VivoTopBar;
import com.betteroriginos.vivouidemo.ui.VivoUi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ClockActivity extends Activity {
    private VivoScaffold scaffold;
    private VivoTopBar topBar;
    private VivoBottomBar bottomBar;
    private LinearLayout body;
    private int currentTab;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastWallSecond = -1;

    private final List<Alarm> alarms = new ArrayList<>();
    private final List<City> cities = new ArrayList<>();
    private final List<WorldRef> worldRefs = new ArrayList<>();

    private TextView countdownView;
    private View emptyStateRow;
    private String lastCountdown;
    private TextView worldHeaderTime;
    private TextView stopwatchText;
    private VivoButton swLeft;
    private VivoButton swRight;
    private LinearLayout lapGroup;
    private TextView timerText;
    private VivoButton timerStart;
    private VivoButton timerReset;
    private final List<TextView> presetChips = new ArrayList<>();

    private long stopwatchAccum;
    private long stopwatchStart;
    private boolean stopwatchRunning;
    private final List<Long> laps = new ArrayList<>();

    private long timerPreset = 5 * 60 * 1000L;
    private long timerRemaining = timerPreset;
    private long timerEnd;
    private boolean timerRunning;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.elapsedRealtime();
            if (stopwatchRunning) {
                updateStopwatchText(now);
            }
            if (timerRunning) {
                long remain = timerEnd - now;
                if (remain <= 0) {
                    remain = 0;
                    timerRunning = false;
                    timerRemaining = 0;
                    updateTimerText();
                    updateTimerButtons();
                    VivoToast.show(ClockActivity.this, "计时结束");
                } else {
                    timerRemaining = remain;
                    updateTimerText();
                }
            }
            long wall = System.currentTimeMillis() / 1000L;
            if (wall != lastWallSecond) {
                lastWallSecond = wall;
                updateWallContent();
                scaffold.invalidateBars();
            }
            handler.postDelayed(this, 33);
        }
    };

    private static class Alarm {
        String time;
        String label;
        String repeat;
        boolean enabled;

        Alarm(String time, String label, String repeat, boolean enabled) {
            this.time = time;
            this.label = label;
            this.repeat = repeat;
            this.enabled = enabled;
        }
    }

    private static class City {
        String name;
        String zone;

        City(String name, String zone) {
            this.name = name;
            this.zone = zone;
        }
    }

    private static class WorldRef {
        TextView time;
        String zone;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VivoTheme.applyWindow(this);
        seedData();

        scaffold = new VivoScaffold(this);
        body = scaffold.getBody();

        topBar = new VivoTopBar(this, true);
        topBar.setLargeHeightDp(84f);
        topBar.setTitle(getString(R.string.clock_tab_alarm));
        topBar.addAction("plus", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddClicked();
            }
        });
        topBar.addAction("waveform", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ClockActivity.this, getString(R.string.clock_voice_toast));
            }
        });
        final ImageView[] moreRef = new ImageView[1];
        moreRef[0] = topBar.addAction("more", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoPopup.show(moreRef[0], new String[]{"设置", "帮助", "关于闹钟"},
                        new String[]{"gear", "help", "info"},
                        new VivoPopup.OnItemClickListener() {
                            @Override
                            public void onItemClick(int index) {
                                VivoToast.show(ClockActivity.this, "菜单项：" + index);
                            }
                        });
            }
        });
        scaffold.setTopBar(topBar);

        bottomBar = new VivoBottomBar(this);
        bottomBar.setItemsWithResources(new String[]{
                        getString(R.string.clock_tab_alarm),
                        getString(R.string.clock_tab_world),
                        getString(R.string.clock_tab_stopwatch),
                        getString(R.string.clock_tab_timer)},
                new int[]{R.drawable.ic_vivo_tab_clock, R.drawable.ic_vivo_tab_world,
                        R.drawable.ic_vivo_tab_stopwatch, R.drawable.ic_vivo_tab_timer});
        bottomBar.setOnItemSelectedListener(new VivoBottomBar.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int index) {
                showTab(index);
            }
        });
        scaffold.setBottomBar(bottomBar);

        setContentView(scaffold);
        scaffold.requestApplyInsets();
        showTab(0);
    }

    private void seedData() {
        alarms.add(new Alarm("07:30", "起床", "工作日", true));
        alarms.add(new Alarm("09:00", "周末", "周六、周日", false));
        alarms.add(new Alarm("13:30", "午休", "工作日", false));
        cities.add(new City("北京", "Asia/Shanghai"));
        cities.add(new City("东京", "Asia/Tokyo"));
        cities.add(new City("纽约", "America/New_York"));
        cities.add(new City("伦敦", "Europe/London"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
    }

    private void showTab(int index) {
        currentTab = index;
        int[] titles = {R.string.clock_tab_alarm, R.string.clock_tab_world,
                R.string.clock_tab_stopwatch, R.string.clock_tab_timer};
        topBar.setTitle(getString(titles[index]));
        body.removeAllViews();
        countdownView = null;
        emptyStateRow = null;
        lastCountdown = null;
        worldHeaderTime = null;
        worldRefs.clear();
        stopwatchText = null;
        swLeft = null;
        swRight = null;
        lapGroup = null;
        timerText = null;
        timerStart = null;
        timerReset = null;
        presetChips.clear();
        if (index == 0) {
            buildAlarmTab();
        } else if (index == 1) {
            buildWorldTab();
        } else if (index == 2) {
            buildStopwatchTab();
        } else {
            buildTimerTab();
        }
        scaffold.getScroll().scrollTo(0, 0);
        scaffold.invalidateBars();
    }

    private void buildAlarmTab() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(VivoTheme.dpi(this, 24), VivoTheme.dpi(this, 10),
                VivoTheme.dpi(this, 24), 0);
        countdownView = new TextView(this);
        VivoTheme.text(countdownView, 14f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        countdownView.setLineSpacing(VivoTheme.dp(this, 2), 1f);
        countdownView.setMaxLines(2);
        header.addView(countdownView);
        VivoUi.addFullWidth(body, header, 6, 0);

        View sleep = buildSleepRow();
        LinearLayout.LayoutParams sleepLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 56));
        sleepLp.leftMargin = VivoTheme.dpi(this, 20);
        sleepLp.rightMargin = VivoTheme.dpi(this, 20);
        sleepLp.topMargin = VivoTheme.dpi(this, 10);
        body.addView(sleep, sleepLp);

        emptyStateRow = buildEmptyStateRow();
        LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 68));
        emptyLp.leftMargin = VivoTheme.dpi(this, 24);
        emptyLp.rightMargin = VivoTheme.dpi(this, 24);
        emptyLp.topMargin = VivoTheme.dpi(this, 6);
        body.addView(emptyStateRow, emptyLp);

        TextView other = VivoUi.sectionLabel(this, getString(R.string.clock_other_alarms));
        LinearLayout.LayoutParams otherLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        otherLp.leftMargin = VivoTheme.dpi(this, 24);
        otherLp.rightMargin = VivoTheme.dpi(this, 24);
        otherLp.topMargin = VivoTheme.dpi(this, 16);
        otherLp.bottomMargin = VivoTheme.dpi(this, 4);
        body.addView(other, otherLp);

        if (alarms.isEmpty()) {
            TextView hint = VivoUi.bodyText(this, getString(R.string.clock_empty_hint));
            hint.setPadding(VivoTheme.dpi(this, 24), VivoTheme.dpi(this, 12),
                    VivoTheme.dpi(this, 24), VivoTheme.dpi(this, 12));
            body.addView(hint, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (Alarm alarm : alarms) {
                addAlarmCard(alarm);
            }
        }
        updateAlarmHeader();
    }

    private View buildSleepRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(VivoTheme.dpi(this, 4), 0, VivoTheme.dpi(this, 8), 0);
        row.setBackground(VivoTheme.iconButtonBackground(this));
        row.setClickable(true);
        ImageView bed = new ImageView(this);
        bed.setImageDrawable(VivoIcons.of("bed",
                VivoTheme.color(this, R.color.vivo_accent), 22));
        LinearLayout.LayoutParams bedLp = new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 22), VivoTheme.dpi(this, 22));
        bedLp.rightMargin = VivoTheme.dpi(this, 12);
        row.addView(bed, bedLp);
        TextView text = new TextView(this);
        VivoTheme.text(text, 15f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_MEDIUM);
        text.setText(getString(R.string.clock_sleep_alarm));
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView arrow = new ImageView(this);
        arrow.setImageDrawable(VivoIcons.of("chevron",
                VivoTheme.color(this, R.color.vivo_text_tertiary), 16));
        row.addView(arrow, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 16), VivoTheme.dpi(this, 16)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ClockActivity.this, "演示：睡眠 | 起床闹钟");
            }
        });
        return row;
    }

    private View buildEmptyStateRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        VivoTheme.text(title, 28f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_BOLD);
        title.setText(getString(R.string.clock_no_alarm));
        row.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView set = new TextView(this);
        VivoTheme.text(set, 15f, VivoTheme.color(this, R.color.vivo_accent), VivoTheme.W_MEDIUM);
        set.setText(getString(R.string.clock_set));
        set.setPadding(VivoTheme.dpi(this, 12), VivoTheme.dpi(this, 8),
                VivoTheme.dpi(this, 12), VivoTheme.dpi(this, 8));
        set.setClickable(true);
        set.setBackground(VivoTheme.iconButtonBackground(this));
        set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAddAlarmSheet();
            }
        });
        row.addView(set);
        return row;
    }

    private void addAlarmCard(final Alarm alarm) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(VivoTheme.rowBackground(this, true, true));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(VivoTheme.dpi(this, 20), 0, VivoTheme.dpi(this, 18), 0);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView time = new TextView(this);
        VivoTheme.text(time, 40f, VivoTheme.color(this, R.color.vivo_text_primary), VivoTheme.W_SEMI);
        time.setFontFeatureSettings("tnum");
        time.setText(alarm.time);
        texts.addView(time);
        TextView label = new TextView(this);
        VivoTheme.text(label, 14f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        label.setText(alarm.repeat);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = VivoTheme.dpi(this, 2);
        texts.addView(label, labelLp);
        content.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final VivoSwitch toggle = new VivoSwitch(this);
        toggle.setChecked(alarm.enabled, false);
        toggle.setOnCheckedChangeListener(new VivoSwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(VivoSwitch view, boolean checked) {
                alarm.enabled = checked;
                updateAlarmHeader();
                scaffold.invalidateBars();
            }
        });
        content.addView(toggle, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 48), VivoTheme.dpi(this, 28)));

        card.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 96)));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ClockActivity.this, "编辑闹钟 " + alarm.time);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = VivoTheme.dpi(this, 20);
        lp.rightMargin = VivoTheme.dpi(this, 20);
        lp.topMargin = VivoTheme.dpi(this, 8);
        body.addView(card, lp);
    }

    private void updateAlarmHeader() {
        if (countdownView == null) {
            return;
        }
        boolean anyEnabled = false;
        for (Alarm alarm : alarms) {
            if (alarm.enabled) {
                anyEnabled = true;
                break;
            }
        }
        if (emptyStateRow != null) {
            emptyStateRow.setVisibility(anyEnabled ? View.GONE : View.VISIBLE);
        }
        if (!anyEnabled) {
            if (!"none".equals(lastCountdown)) {
                lastCountdown = "none";
                countdownView.setText("没有启用的闹钟");
            }
            return;
        }
        Calendar now = Calendar.getInstance();
        long nowMs = now.getTimeInMillis();
        long best = Long.MAX_VALUE;
        for (Alarm alarm : alarms) {
            if (!alarm.enabled) {
                continue;
            }
            long occurrence = nextOccurrence(alarm.time, now);
            if (occurrence < best) {
                best = occurrence;
            }
        }
        if (best == Long.MAX_VALUE) {
            return;
        }
        long diff = Math.max(0L, best - nowMs);
        long totalMinutes = (diff + 59999L) / 60000L;
        long days = totalMinutes / 1440L;
        long hours = (totalMinutes % 1440L) / 60L;
        long minutes = totalMinutes % 60L;
        String key = days + ":" + hours + ":" + minutes;
        if (key.equals(lastCountdown)) {
            return;
        }
        lastCountdown = key;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(getString(R.string.clock_countdown_prefix));
        appendCountdownPart(sb, String.format(Locale.US, "%02d", days),
                getString(R.string.clock_unit_day));
        appendCountdownPart(sb, String.format(Locale.US, "%02d", hours),
                getString(R.string.clock_unit_hour));
        appendCountdownPart(sb, String.format(Locale.US, "%02d", minutes),
                getString(R.string.clock_unit_minute));
        sb.append(getString(R.string.clock_countdown_suffix));
        countdownView.setText(sb);
    }

    private void appendCountdownPart(SpannableStringBuilder sb, String number, String unit) {
        int start = sb.length();
        sb.append(number);
        int end = sb.length();
        sb.setSpan(new RelativeSizeSpan(1.75f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(VivoTheme.color(this, R.color.vivo_text_primary)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new BackgroundColorSpan(VivoTheme.color(this, R.color.vivo_accent_soft)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int unitStart = sb.length();
        sb.append(unit);
        int unitEnd = sb.length();
        sb.setSpan(new RelativeSizeSpan(0.86f), unitStart, unitEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(VivoTheme.color(this, R.color.vivo_text_secondary)),
                unitStart, unitEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private long nextOccurrence(String hhmm, Calendar now) {
        try {
            String[] parts = hhmm.split(":");
            Calendar next = (Calendar) now.clone();
            next.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            next.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            if (next.getTimeInMillis() <= now.getTimeInMillis()) {
                next.add(Calendar.DAY_OF_YEAR, 1);
            }
            return next.getTimeInMillis();
        } catch (Throwable t) {
            return Long.MAX_VALUE;
        }
    }

    private void buildWorldTab() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(VivoTheme.dpi(this, 24), VivoTheme.dpi(this, 2),
                VivoTheme.dpi(this, 24), VivoTheme.dpi(this, 4));

        TextView city = new TextView(this);
        VivoTheme.text(city, 15f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_MEDIUM);
        city.setText("北京");
        header.addView(city);

        worldHeaderTime = new TextView(this);
        VivoTheme.text(worldHeaderTime, 56f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_SEMI);
        worldHeaderTime.setFontFeatureSettings("tnum");
        worldHeaderTime.setLetterSpacing(-0.03f);
        LinearLayout.LayoutParams wtLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wtLp.topMargin = VivoTheme.dpi(this, 2);
        header.addView(worldHeaderTime, wtLp);

        TextView date = new TextView(this);
        VivoTheme.text(date, 13f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        date.setText(currentDate());
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = VivoTheme.dpi(this, 2);
        header.addView(date, dLp);
        VivoUi.addFullWidth(body, header, 2, 12);

        LinearLayout group = VivoUi.group(this);
        for (City c : cities) {
            group.addView(worldRow(c));
        }
        addWorldAddRow(group);
        VivoUi.addGroup(body, group, 8, 8);
        updateWorldTimes();
    }

    private View worldRow(final City city) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(VivoTheme.dpi(this, 18), 0, VivoTheme.dpi(this, 18), 0);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        VivoTheme.text(name, 15f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_MEDIUM);
        name.setText(city.name);
        texts.addView(name);
        TextView offset = new TextView(this);
        VivoTheme.text(offset, 12f, VivoTheme.color(this, R.color.vivo_text_secondary),
                VivoTheme.W_REGULAR);
        offset.setText(offsetText(city.zone));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = VivoTheme.dpi(this, 2);
        texts.addView(offset, olp);
        content.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView time = new TextView(this);
        VivoTheme.text(time, 24f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_SEMI);
        time.setFontFeatureSettings("tnum");
        content.addView(time);

        WorldRef ref = new WorldRef();
        ref.time = time;
        ref.zone = city.zone;
        worldRefs.add(ref);

        row.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 64)));
        View divider = new View(this);
        divider.setTag("row_divider");
        divider.setBackgroundColor(VivoTheme.color(this, R.color.vivo_divider));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dlp.leftMargin = VivoTheme.dpi(this, 18);
        row.addView(divider, dlp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VivoToast.show(ClockActivity.this, city.name + " " + time.getText());
            }
        });
        return row;
    }

    private void addWorldAddRow(LinearLayout group) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(VivoTheme.dpi(this, 18), 0, VivoTheme.dpi(this, 16), 0);
        ImageView plus = new ImageView(this);
        plus.setImageDrawable(VivoIcons.of("plus",
                VivoTheme.color(this, R.color.vivo_accent), 20));
        row.addView(plus, new LinearLayout.LayoutParams(
                VivoTheme.dpi(this, 20), VivoTheme.dpi(this, 20)));
        TextView text = new TextView(this);
        VivoTheme.text(text, 15f, VivoTheme.color(this, R.color.vivo_accent), VivoTheme.W_MEDIUM);
        text.setText("添加城市");
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.leftMargin = VivoTheme.dpi(this, 14);
        row.addView(text, textLp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAddCitySheet();
            }
        });
        group.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 56)));
    }

    private void buildStopwatchTab() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(0, VivoTheme.dpi(this, 18), 0, VivoTheme.dpi(this, 8));

        stopwatchText = new TextView(this);
        VivoTheme.text(stopwatchText, 56f, VivoTheme.color(this, R.color.vivo_text_primary),
                VivoTheme.W_SEMI);
        stopwatchText.setFontFeatureSettings("tnum");
        stopwatchText.setLetterSpacing(-0.02f);
        stopwatchText.setText(formatStopwatch(currentStopwatchMs()));
        header.addView(stopwatchText);
        VivoUi.addFullWidth(body, header, 4, 0);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(VivoTheme.dpi(this, 14), 0, VivoTheme.dpi(this, 14), 0);

        swLeft = new VivoButton(this);
        swLeft.setStyle(VivoButton.SECONDARY);
        swLeft.setSize(VivoButton.SIZE_XL);
        swLeft.setText(getString(R.string.clock_stopwatch_lap));
        swLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (stopwatchRunning) {
                    addLap();
                } else {
                    resetStopwatch();
                }
            }
        });
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 46), 1f);
        leftLp.rightMargin = VivoTheme.dpi(this, 6);
        buttonRow.addView(swLeft, leftLp);

        swRight = new VivoButton(this);
        swRight.setStyle(VivoButton.PRIMARY);
        swRight.setSize(VivoButton.SIZE_XL);
        swRight.setText(getString(R.string.clock_stopwatch_start));
        swRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (stopwatchRunning) {
                    stopwatchAccum += SystemClock.elapsedRealtime() - stopwatchStart;
                    stopwatchRunning = false;
                } else {
                    stopwatchStart = SystemClock.elapsedRealtime();
                    stopwatchRunning = true;
                }
                updateStopwatchButtons();
                updateStopwatchText(SystemClock.elapsedRealtime());
            }
        });
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 46), 1f);
        rightLp.leftMargin = VivoTheme.dpi(this, 6);
        buttonRow.addView(swRight, rightLp);
        VivoUi.addFullWidth(body, buttonRow, 20, 12);

        lapGroup = VivoUi.group(this);
        VivoUi.addToBody(body, lapGroup, 8, 8);
        rebuildLaps();
        updateStopwatchButtons();
    }

    private void rebuildLaps() {
        if (lapGroup == null) {
            return;
        }
        lapGroup.removeAllViews();
        if (laps.isEmpty()) {
            LinearLayout hint = new LinearLayout(this);
            hint.setGravity(Gravity.CENTER);
            TextView text = new TextView(this);
            VivoTheme.text(text, 13f, VivoTheme.color(this, R.color.vivo_text_secondary),
                    VivoTheme.W_REGULAR);
            text.setText("开始后记录计次");
            hint.addView(text);
            lapGroup.addView(hint, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 64)));
            styleLapRows();
            return;
        }
        long previous = 0;
        for (int i = 0; i < laps.size(); i++) {
            final int number = i + 1;
            long total = laps.get(i);
            long split = total - previous;
            previous = total;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setPadding(VivoTheme.dpi(this, 18), 0, VivoTheme.dpi(this, 18), 0);
            TextView label = new TextView(this);
            VivoTheme.text(label, 14f, VivoTheme.color(this, R.color.vivo_text_secondary),
                    VivoTheme.W_REGULAR);
            label.setText("计次 " + number);
            content.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView splitView = new TextView(this);
            VivoTheme.text(splitView, 14f, VivoTheme.color(this, R.color.vivo_text_secondary),
                    VivoTheme.W_REGULAR);
            splitView.setFontFeatureSettings("tnum");
            splitView.setText(formatStopwatch(split));
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sLp.rightMargin = VivoTheme.dpi(this, 16);
            content.addView(splitView, sLp);
            TextView totalView = new TextView(this);
            VivoTheme.text(totalView, 14f, VivoTheme.color(this, R.color.vivo_text_primary),
                    VivoTheme.W_MEDIUM);
            totalView.setFontFeatureSettings("tnum");
            totalView.setText(formatStopwatch(total));
            content.addView(totalView);

            row.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, VivoTheme.dpi(this, 48)));
            View divider = new View(this);
            divider.setTag("row_divider");
            divider.setBackgroundColor(VivoTheme.color(this, R.color.vivo_divider));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            dlp.leftMargin = VivoTheme.dpi(this, 18);
            row.addView(divider, dlp);
            lapGroup.addView(row);
        }
        styleLapRows();
    }

    private void styleLapRows() {
        if (lapGroup == null) {
            return;
        }
        int count = lapGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = lapGroup.getChildAt(i);
            row.setBackground(VivoTheme.rowBackground(this, i == 0, i == count - 1));
            View divider = row.findViewWithTag("row_divider");
            if (divider != null) {
                divider.setVisibility(i == count - 1 ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void addLap() {
        laps.add(currentStopwatchMs());
        rebuildLaps();
        scaffold.invalidateBars();
    }

    private void resetStopwatch() {
        stopwatchRunning = false;
        stopwatchAccum = 0;
        laps.clear();
        updateStopwatchText(SystemClock.elapsedRealtime());
        updateStopwatchButtons();
        rebuildLaps();
    }

    private long currentStopwatchMs() {
        return stopwatchAccum + (stopwatchRunning
                ? SystemClock.elapsedRealtime() - stopwatchStart : 0);
    }

    private void updateStopwatchText(long now) {
        if (stopwatchText == null) {
            return;
        }
        stopwatchText.setText(formatStopwatch(currentStopwatchMs()));
    }

    private void updateStopwatchButtons() {
        if (swLeft == null || swRight == null) {
            return;
        }
        boolean hasTime = currentStopwatchMs() > 0;
        swLeft.setText(stopwatchRunning ? getString(R.string.clock_stopwatch_lap)
                : getString(R.string.clock_stopwatch_reset));
        swLeft.setEnabled(stopwatchRunning || hasTime);
        if (stopwatchRunning) {
            swRight.setText(getString(R.string.clock_stopwatch_pause));
        } else if (hasTime) {
            swRight.setText("继续");
        } else {
            swRight.setText(getString(R.string.clock_stopwatch_start));
        }
    }

    private void buildTimerTab() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, VivoTheme.dpi(this, 26), 0, 0);

        timerText = new TextView(this);
        VivoTheme.text(timerText, 64f, VivoTheme.color(this, R.color.vivo_text_primary), VivoTheme.W_SEMI);
        timerText.setFontFeatureSettings("tnum");
        timerText.setLetterSpacing(-0.03f);
        timerText.setText(formatTimer(timerRemaining));
        header.addView(timerText);
        VivoUi.addFullWidth(body, header, 4, 0);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(VivoTheme.dpi(this, 18), 0, VivoTheme.dpi(this, 18), 0);
        int[] minutes = new int[]{1, 3, 5, 10};
        for (int i = 0; i < minutes.length; i++) {
            final int minute = minutes[i];
            TextView chip = new TextView(this);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(13f);
            chip.setTypeface(VivoTheme.typeface(VivoTheme.W_MEDIUM));
            chip.setText(minute + " 分钟");
            chip.setHeight(VivoTheme.dpi(this, 38));
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    timerRunning = false;
                    timerPreset = minute * 60 * 1000L;
                    timerRemaining = timerPreset;
                    updateTimerText();
                    updateTimerButtons();
                    refreshPresetChips();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, VivoTheme.dpi(this, 38), 1f);
            lp.leftMargin = VivoTheme.dpi(this, 3);
            lp.rightMargin = VivoTheme.dpi(this, 3);
            presetRow.addView(chip, lp);
            presetChips.add(chip);
        }
        VivoUi.addFullWidth(body, presetRow, 22, 16);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(VivoTheme.dpi(this, 14), 0, VivoTheme.dpi(this, 14), 0);

        timerReset = new VivoButton(this);
        timerReset.setStyle(VivoButton.SECONDARY);
        timerReset.setSize(VivoButton.SIZE_XL);
        timerReset.setText(getString(R.string.clock_timer_reset));
        timerReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timerRunning = false;
                timerRemaining = timerPreset;
                updateTimerText();
                updateTimerButtons();
            }
        });
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 46), 1f);
        resetLp.rightMargin = VivoTheme.dpi(this, 6);
        buttonRow.addView(timerReset, resetLp);

        timerStart = new VivoButton(this);
        timerStart.setStyle(VivoButton.PRIMARY);
        timerStart.setSize(VivoButton.SIZE_XL);
        timerStart.setText(getString(R.string.clock_timer_start));
        timerStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long now = SystemClock.elapsedRealtime();
                if (timerRunning) {
                    timerRemaining = Math.max(0, timerEnd - now);
                    timerRunning = false;
                } else {
                    if (timerRemaining <= 0) {
                        timerRemaining = timerPreset;
                    }
                    timerEnd = now + timerRemaining;
                    timerRunning = true;
                }
                updateTimerButtons();
                updateTimerText();
            }
        });
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(
                0, VivoTheme.dpi(this, 46), 1f);
        startLp.leftMargin = VivoTheme.dpi(this, 6);
        buttonRow.addView(timerStart, startLp);
        VivoUi.addFullWidth(body, buttonRow, 6, 8);
        refreshPresetChips();
        updateTimerButtons();
    }

    private void refreshPresetChips() {
        for (int i = 0; i < presetChips.size(); i++) {
            TextView chip = presetChips.get(i);
            boolean selected = timerPreset == (new int[]{1, 3, 5, 10}[i]) * 60 * 1000L;
            chip.setTextColor(selected
                    ? VivoTheme.color(this, R.color.vivo_accent)
                    : VivoTheme.color(this, R.color.vivo_text_primary));
            chip.setTypeface(VivoTheme.typeface(selected ? VivoTheme.W_SEMI : VivoTheme.W_MEDIUM));
            chip.setBackground(VivoTheme.rounded(this,
                    VivoTheme.color(this, selected ? R.color.vivo_accent_soft : R.color.vivo_button_gray),
                    19f));
        }
    }

    private void updateTimerText() {
        if (timerText != null) {
            timerText.setText(formatTimer(timerRemaining));
        }
    }

    private void updateTimerButtons() {
        if (timerStart == null) {
            return;
        }
        timerStart.setText(timerRunning ? getString(R.string.clock_stopwatch_pause)
                : getString(R.string.clock_timer_start));
        if (timerReset != null) {
            timerReset.setEnabled(timerRemaining != timerPreset || timerRunning);
        }
    }

    private void updateWallContent() {
        if (currentTab == 0) {
            updateAlarmHeader();
        }
        updateWorldTimes();
    }

    private void updateWorldTimes() {
        if (worldHeaderTime != null) {
            worldHeaderTime.setText(timeInZone("Asia/Shanghai"));
        }
        for (WorldRef ref : worldRefs) {
            ref.time.setText(timeInZone(ref.zone));
        }
    }

    private String currentTime() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }

    private String currentDate() {
        return new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
    }

    private String timeInZone(String zone) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.CHINA);
        f.setTimeZone(TimeZone.getTimeZone(zone));
        return f.format(new Date());
    }

    private String offsetText(String zone) {
        Calendar now = Calendar.getInstance();
        int base = TimeZone.getTimeZone("Asia/Shanghai").getOffset(now.getTimeInMillis());
        int other = TimeZone.getTimeZone(zone).getOffset(now.getTimeInMillis());
        int diff = Math.round((other - base) / 3600000f);
        if (diff == 0) {
            return "与北京同时";
        }
        return diff > 0 ? "比北京快 " + diff + " 小时" : "比北京慢 " + (-diff) + " 小时";
    }

    private String formatStopwatch(long ms) {
        long minutes = ms / 60000L;
        long seconds = (ms / 1000L) % 60L;
        long centis = (ms / 10L) % 100L;
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis);
    }

    private String formatTimer(long ms) {
        long totalSeconds = Math.max(0, ms + 999) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void onAddClicked() {
        if (currentTab == 0) {
            openAddAlarmSheet();
        } else if (currentTab == 1) {
            openAddCitySheet();
        } else {
            VivoToast.show(this, "此页签没有新增操作");
        }
    }

    private void openAddAlarmSheet() {
        VivoSheet.show(this, "新建闹钟",
                new String[]{"明天 07:30", "明天 08:00", "明天 09:30"},
                new String[]{"clock", "clock", "clock"},
                new int[]{VivoTheme.color(this, R.color.vivo_accent),
                        VivoTheme.color(this, R.color.vivo_accent),
                        VivoTheme.color(this, R.color.vivo_accent)},
                new VivoSheet.OnActionClickListener() {
                    @Override
                    public void onActionClick(int index) {
                        String[] times = {"07:30", "08:00", "09:30"};
                        alarms.add(new Alarm(times[index], "新闹钟", "仅一次", true));
                        showTab(0);
                        VivoToast.show(ClockActivity.this, "已添加 " + times[index] + " 闹钟");
                    }
                });
    }

    private void openAddCitySheet() {
        final String[] names = {"巴黎", "悉尼", "迪拜"};
        final String[] zones = {"Europe/Paris", "Australia/Sydney", "Asia/Dubai"};
        VivoSheet.show(this, "添加城市", names,
                new String[]{"world", "world", "world"},
                new int[]{VivoTheme.color(this, R.color.vivo_accent),
                        VivoTheme.color(this, R.color.vivo_accent),
                        VivoTheme.color(this, R.color.vivo_accent)},
                new VivoSheet.OnActionClickListener() {
                    @Override
                    public void onActionClick(int index) {
                        for (City c : cities) {
                            if (c.name.equals(names[index])) {
                                VivoToast.show(ClockActivity.this, "已添加该城市");
                                return;
                            }
                        }
                        cities.add(new City(names[index], zones[index]));
                        showTab(1);
                        VivoToast.show(ClockActivity.this, "已添加 " + names[index]);
                    }
                });
    }
}
