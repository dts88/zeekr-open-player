package io.github.dts88.openplayer.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class Rows {
    private Rows() {
    }

    static final class Item {
        String icon = "";
        String number = "";
        String title = "";
        String sub = "";
        String trailing = "";
        boolean current;
        float progress = -1;
        Runnable click;

        Item(String title) {
            this.title = title;
        }

        Item sub(String s) {
            sub = s == null ? "" : s;
            return this;
        }

        Item icon(String name) {
            icon = name;
            return this;
        }

        Item number(String n) {
            number = n;
            return this;
        }

        Item trailing(String s) {
            trailing = s == null ? "" : s;
            return this;
        }

        Item current(boolean on) {
            current = on;
            return this;
        }

        Item progress(float f) {
            progress = f;
            return this;
        }

        Item onClick(Runnable r) {
            click = r;
            return this;
        }
    }

    static final class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<Item> items = new ArrayList<>();

        void set(List<Item> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(parent.getContext());
        }

        @Override
        public void onBindViewHolder(Holder h, int position) {
            h.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final LinearLayout row;
        private final ImageView icon;
        private final TextView number;
        private final TextView title;
        private final TextView sub;
        private final TextView trailing;
        private final View progress;

        Holder(Context c) {
            super(new LinearLayout(c));
            row = (LinearLayout) itemView;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(c, 76));
            row.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 24), Ui.dp(c, 8));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(c, 6);
            row.setLayoutParams(lp);
            row.setBackground(Ui.pressable(c, Ui.SUNKEN, Ui.RADIUS));

            icon = Ui.icon(c, "film", 28, Ui.TEXT3);
            row.addView(icon);
            number = Ui.bold(c, "", Ui.BODY, Ui.TEXT3);
            number.setGravity(Gravity.CENTER);
            row.addView(number, new LinearLayout.LayoutParams(Ui.dp(c, 44), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout texts = Ui.column(c);
            texts.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
            title = Ui.oneLine(Ui.text(c, "", Ui.BODY, Ui.TEXT));
            sub = Ui.oneLine(Ui.text(c, "", Ui.MICRO, Ui.TEXT3));
            sub.setPadding(0, Ui.dp(c, 4), 0, 0);
            texts.addView(title);
            texts.addView(sub);
            progress = new View(c);
            progress.setBackgroundColor(Ui.ACCENT);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, Ui.dp(c, 4));
            pl.topMargin = Ui.dp(c, 6);
            texts.addView(progress, pl);
            row.addView(texts, Ui.weight(1));
            trailing = Ui.text(c, "", Ui.CAPTION, Ui.TEXT3);
            row.addView(trailing);
        }

        void bind(Item it) {
            Context c = row.getContext();
            boolean hasNumber = !it.number.isEmpty();
            icon.setVisibility(hasNumber || it.icon.isEmpty() ? View.GONE : View.VISIBLE);
            if (!it.icon.isEmpty()) icon.setImageDrawable(new Icons(it.icon, it.current ? Ui.ACCENT : Ui.TEXT3, c));
            number.setVisibility(hasNumber ? View.VISIBLE : View.GONE);
            number.setText(it.number);
            number.setTextColor(it.current ? Ui.ENERGY_TEXT : Ui.TEXT3);
            title.setText(it.title);
            title.setTextColor(it.current ? Ui.ENERGY_TEXT : Ui.TEXT);
            sub.setText(it.sub);
            sub.setVisibility(it.sub.isEmpty() ? View.GONE : View.VISIBLE);
            trailing.setText(it.trailing);
            boolean showProgress = it.progress >= 0;
            progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            if (showProgress) {
                LinearLayout.LayoutParams pl = (LinearLayout.LayoutParams) progress.getLayoutParams();
                pl.width = Math.max(Ui.dp(c, 6), Math.round(Ui.dp(c, 360) * Math.min(1f, it.progress)));
                progress.setLayoutParams(pl);
            }
            row.setOnClickListener(it.click == null ? null : v -> it.click.run());
            row.setClickable(it.click != null);
        }
    }
}
