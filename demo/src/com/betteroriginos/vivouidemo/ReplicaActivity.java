package com.betteroriginos.vivouidemo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.betteroriginos.vivouidemo.ui.VivoIcons;
import com.originui.replica.OriginBlur;
import com.originui.replica.OriginBottomBar;
import com.originui.replica.OriginOverlays;
import com.originui.replica.OriginRows;
import com.originui.replica.OriginSlider;
import com.originui.replica.OriginSwitch;
import com.originui.replica.OriginTheme;
import com.originui.replica.OriginTopBar;

/**
 * vivo-OriginUI 复刻库验收页：全部控件来自 com.originui.replica.*，
 * 顶栏/底栏走原生 setMaterial 模糊，列表滚动联动渐显。
 */
public class ReplicaActivity extends Activity {
    private OriginTopBar mTopBar;
    private ScrollView mScroll;
    private TextView mPopupValue;
    private int mChoice = 0;
    private final String[] mChoices = {"OS5 原子风", "Material You", "纯净风"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OriginTheme.applyWindow(this);

        boolean supported = OriginBlur.isSupported(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(OriginTheme.bgPage(this));

        // ---- 彩色内容（供模糊食用）----
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0F77FF, 0xFF7A6FF0, 0xFFFF8A3D});
        heroBg.setCornerRadius(OriginTheme.dp(this, 18));
        hero.setBackground(heroBg);
        TextView heroTitle = new TextView(this);
        OriginTheme.text(heroTitle, 22f, 0xFFFFFFFF, OriginTheme.W_BOLD);
        heroTitle.setText("OriginUI 复刻库");
        TextView heroSub = new TextView(this);
        OriginTheme.text(heroSub, 12f, 0xCCFFFFFF, OriginTheme.W_REGULAR);
        heroSub.setText("原生模糊 " + (supported ? "已启用 ✓ (setMaterial)" : "不可用（兜底模式）"));
        heroSub.setGravity(Gravity.CENTER);
        hero.addView(heroTitle);
        hero.addView(heroSub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, OriginTheme.dpi(this, 140));
        int margin = OriginTheme.dpi(this, OriginTheme.PAGE_MARGIN);
        heroLp.leftMargin = margin;
        heroLp.rightMargin = margin;
        heroLp.topMargin = OriginTheme.dpi(this, 76);
        page.addView(hero, heroLp);

        // ---- 大标题 ----
        TextView pageTitle = OriginRows.pageTitle(this, "组件验收");
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ptLp.leftMargin = margin;
        ptLp.rightMargin = margin;
        ptLp.topMargin = OriginTheme.dpi(this, 20);
        ptLp.bottomMargin = OriginTheme.dpi(this, 10);
        page.addView(pageTitle, ptLp);

        // ---- 分组卡：开关 / 滑杆 / 下拉 / 对话框 / 弹层 ----
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);

        // 1. 开关行（拖动跟手）
        final OriginSwitch sw = new OriginSwitch(this);
        sw.setChecked(true, false);
        View row1 = OriginRows.row(this, "模糊开关行", "拖动跟手 0.4/0.6 阈值 + Overshoot(1.8)", sw,
                OriginTheme.POSITION_TOP, null);
        group.addView(row1);
        group.addView(OriginRows.divider(this));

        // 2. 滑杆行（标题行 + 卡内滑杆行，同组）
        View sliderTitleRow = OriginRows.row(this, "滑杆", "蓝盘白心 thumb 18dp + 4dp 轨 + 拖动气泡", null,
                OriginTheme.POSITION_MID, null);
        group.addView(sliderTitleRow);
        group.addView(OriginRows.divider(this));
        final TextView sliderVal = new TextView(this);
        OriginTheme.text(sliderVal, 14f, OriginTheme.accent(this), OriginTheme.W_MEDIUM);
        sliderVal.setText("50");
        OriginSlider slider = new OriginSlider(this);
        slider.setProgress(50);
        slider.setListener(new OriginSlider.OnProgressChangedListener() {
            @Override
            public void onProgressChanged(OriginSlider view, float progress) {
                sliderVal.setText(String.valueOf(Math.round(progress)));
            }
        });
        LinearLayout sliderValRow = OriginRows.row(this, "当前值", null, sliderVal,
                OriginTheme.POSITION_MID, null);
        group.addView(sliderValRow);
        group.addView(OriginRows.divider(this));
        LinearLayout sliderBox = new LinearLayout(this);
        sliderBox.setOrientation(LinearLayout.VERTICAL);
        sliderBox.setBackground(OriginTheme.rowBg(this, OriginTheme.bgCard(this),
                OriginTheme.POSITION_BOTTOM));
        LinearLayout.LayoutParams slLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, OriginTheme.dpi(this, 48));
        slLp.leftMargin = OriginTheme.dpi(this, OriginTheme.ROW_PAD_H);
        slLp.rightMargin = OriginTheme.dpi(this, OriginTheme.ROW_PAD_H);
        slLp.topMargin = OriginTheme.dpi(this, 2);
        slLp.bottomMargin = OriginTheme.dpi(this, 6);
        sliderBox.addView(slider, slLp);
        group.addView(sliderBox);

        LinearLayout groupWrap = new LinearLayout(this);
        groupWrap.setOrientation(LinearLayout.VERTICAL);
        groupWrap.addView(group);
        LinearLayout.LayoutParams gwLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gwLp.leftMargin = margin;
        gwLp.rightMargin = margin;
        page.addView(groupWrap, gwLp);

        // 3. 第二组：点击行为
        LinearLayout group2 = new LinearLayout(this);
        group2.setOrientation(LinearLayout.VERTICAL);
        mPopupValue = new TextView(this);
        OriginTheme.text(mPopupValue, 14f, OriginTheme.accent(this), OriginTheme.W_MEDIUM);
        mPopupValue.setText(mChoices[0]);
        View rowPopup = OriginRows.row(this, "卡片样式", "行内下拉（ListPopup）", mPopupValue,
                OriginTheme.POSITION_TOP, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showPopup(v);
                    }
                });
        group2.addView(rowPopup);
        group2.addView(OriginRows.divider(this));
        View rowDialog = OriginRows.row(this, "确认对话框", "居中 16dp 圆角卡", null,
                OriginTheme.POSITION_MID, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDialog();
                    }
                });
        group2.addView(rowDialog);
        group2.addView(OriginRows.divider(this));
        View rowSheet = OriginRows.row(this, "底部弹层", "SHEET_DIALOG 材质", null,
                OriginTheme.POSITION_BOTTOM, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showSheet();
                    }
                });
        group2.addView(rowSheet);
        LinearLayout.LayoutParams g2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        g2Lp.leftMargin = margin;
        g2Lp.rightMargin = margin;
        g2Lp.topMargin = OriginTheme.dpi(this, 8);
        page.addView(group2, g2Lp);

        // ---- 底部说明 ----
        TextView footer = new TextView(this);
        OriginTheme.text(footer, 11f, OriginTheme.textTertiary(this), OriginTheme.W_REGULAR);
        footer.setText("com.originui.replica · 逆向自 OriginOS 5 (iQOO Z8)\n模糊=View.setMaterial(原生) · 材质表见 docs/01-blur.md");
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, OriginTheme.dpi(this, 24), 0, OriginTheme.dpi(this, 110));
        page.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 滚动容器 + 栏 ----
        mScroll = new ScrollView(this);
        mScroll.setFillViewport(true);
        mScroll.addView(page, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mTopBar = new OriginTopBar(this);
        mTopBar.setTitle("OriginUI 验收");
        root.addView(mTopBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));
        mTopBar.attach(mScroll);

        OriginBottomBar bar = new OriginBottomBar(this);
        CharSequence[] labels = {"首页", "组件", "模糊", "我的"};
        bar.setItems(labels, new OriginBottomBar.IconFactory() {
            @Override
            public View createIcon(int index, int sizeDp) {
                String name = new String[]{"apps", "palette", "blur", "info"}[index];
                android.widget.ImageView iv = new android.widget.ImageView(ReplicaActivity.this);
                iv.setImageDrawable(VivoIcons.of(name, OriginTheme.textSecondary(ReplicaActivity.this), 24));
                return iv;
            }
        }, new OriginBottomBar.OnItemSelectedListener() {
            @Override
            public void onItemSelected(int index) {
                // demo 无二级页
            }
        });
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);
    }

    private void showPopup(View anchor) {
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return mChoices.length;
            }

            @Override
            public Object getItem(int position) {
                return mChoices[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView item = convertView instanceof TextView ? (TextView) convertView
                        : new TextView(ReplicaActivity.this);
                int pad = OriginTheme.dpi(ReplicaActivity.this, 14);
                item.setPadding(pad * 2, pad, pad * 2, pad);
                OriginTheme.text(item, 15f,
                        position == mChoice ? OriginTheme.accent(ReplicaActivity.this)
                                : OriginTheme.textPrimary(ReplicaActivity.this),
                        OriginTheme.W_MEDIUM);
                item.setText((position == mChoice ? "✓ " : "   ") + mChoices[position]);
                return item;
            }
        };
        OriginOverlays.OriginListPopup popup = new OriginOverlays.OriginListPopup(this, anchor,
                adapter,
                new OriginOverlays.OriginListPopup.OnItemClickListener() {
                    @Override
                    public void onItemClick(android.widget.AdapterView<?> parent, View view,
                            int position, long id) {
                        mChoice = position;
                        mPopupValue.setText(mChoices[position]);
                    }
                });
        popup.show();
    }

    private void showDialog() {
        OriginOverlays.OriginDialog dialog = new OriginOverlays.OriginDialog(this);
        dialog.build("重启 SystemUI？", "应用当前配置需要重启系统界面，未保存的修改不会丢失。",
                "取消", null, "重启", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        android.widget.Toast.makeText(ReplicaActivity.this,
                                "已确认（demo 不真正重启）", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
        dialog.show();
    }

    private void showSheet() {
        OriginOverlays.OriginSheet sheet = new OriginOverlays.OriginSheet(this);
        TextView title = new TextView(this);
        OriginTheme.text(title, 17f, OriginTheme.textPrimary(this), OriginTheme.W_MEDIUM);
        title.setText("底部弹层");
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, OriginTheme.dpi(this, 8), 0, OriginTheme.dpi(this, 12));
        sheet.body().addView(title);

        TextView pill = OriginRows.pillButton(this, "确认", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.dismiss();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, OriginTheme.dpi(this, OriginTheme.PILL_HEIGHT));
        lp.bottomMargin = OriginTheme.dpi(this, 12);
        sheet.body().addView(pill, lp);
        sheet.show();
    }
}
