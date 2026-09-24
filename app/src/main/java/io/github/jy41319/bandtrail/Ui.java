package io.github.jy41319.bandtrail;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG = Color.rgb(245,246,240), INK = Color.rgb(26,43,36), MUTED = Color.rgb(88,105,96), GREEN = Color.rgb(35,91,71), PALE = Color.rgb(225,235,214);
    static int dp(Activity a, int value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
    static GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    static LinearLayout page(Activity a) {
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,24), dp(a,16), dp(a,24), dp(a,32));
        scroll.addView(root); a.setContentView(scroll);
        if (android.os.Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        // getInsets(Type) is API 30; Android 10 uses legacy stable insets.
        if (android.os.Build.VERSION.SDK_INT < 30) scroll.setOnApplyWindowInsetsListener((v, i) -> {
            v.setPadding(i.getSystemWindowInsetLeft(), i.getSystemWindowInsetTop(), i.getSystemWindowInsetRight(), i.getSystemWindowInsetBottom()); return i;
        });
        return root;
    }
    static TextView text(Activity a, LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView t = new TextView(a); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(a,3), 1); t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin = dp(a,10);
        parent.addView(t,p); return t;
    }
    static LinearLayout card(Activity a, LinearLayout parent, int color) {
        LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a,20),dp(a,22),dp(a,20),dp(a,18)); box.setBackground(shape(color,dp(a,24)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(a,16); parent.addView(box,p); return box;
    }
    static Button button(Activity a, LinearLayout parent, String label, boolean primary, Runnable action) {
        Button b = new Button(a); b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setMinHeight(dp(a,52));
        b.setTextColor(primary ? Color.WHITE : GREEN);
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x223A7255),
            shape(primary ? GREEN : PALE, dp(a,16)), null));
        b.setStateListAnimator(null); b.setElevation(0);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(a,8); parent.addView(b,p); return b;
    }
    static void note(Activity a, LinearLayout p, String value) { text(a,p,value,13,MUTED,false); }
    static void toast(Activity a, String value) { Toast.makeText(a,value,Toast.LENGTH_LONG).show(); }
}
