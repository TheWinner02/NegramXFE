package xyz.nextalone.nagram.ui.fluid;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.ViewPagerActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity;

/** Reacquires a settings entry after its resume-time rebind; never matches by position alone. */
final class FluidSettingsAnchor {
    private final WeakReference<RecyclerView> recycler;
    private final WeakReference<RecyclerView.Adapter> adapter;
    private final int position, viewType;
    private final Class<?> rowClass, sourceClass;
    private final String label;
    private final int[] sourcePath;

    static boolean isRoot(BaseFragment fragment) {
        if (fragment instanceof ViewPagerActivity) {
            BaseFragment visible = ((ViewPagerActivity) fragment).getCurrentVisibleFragment();
            return visible != fragment && isRoot(visible);
        }
        return fragment instanceof SettingsActivity || fragment instanceof BaseNekoSettingsActivity
                || fragment instanceof BaseNekoXSettingsActivity;
    }

    private FluidSettingsAnchor(RecyclerView list, View row, View source,
                                RecyclerView.ViewHolder holder, String label, int[] path) {
        recycler = new WeakReference<>(list);
        adapter = new WeakReference<>(list.getAdapter());
        position = holder.getAdapterPosition();
        viewType = holder.getItemViewType();
        rowClass = row.getClass();
        sourceClass = source.getClass();
        this.label = label;
        sourcePath = path;
    }

    static FluidSettingsAnchor capture(View source) {
        if (source == null) return null;
        ArrayList<Integer> path = new ArrayList<>();
        View row = source;
        while (row.getParent() instanceof ViewGroup && !(row.getParent() instanceof RecyclerView)) {
            ViewGroup parent = (ViewGroup) row.getParent();
            if (path.size() >= 24) return null;
            path.add(parent.indexOfChild(row));
            row = parent;
        }
        if (!(row.getParent() instanceof RecyclerView)) return null;
        RecyclerView list = (RecyclerView) row.getParent();
        RecyclerView.ViewHolder holder = list.getChildViewHolder(row);
        String label = primaryLabel(row, 0);
        if (holder == null || holder.getAdapterPosition() == RecyclerView.NO_POSITION
                || list.getAdapter() == null || TextUtils.isEmpty(label)) return null;
        Collections.reverse(path);
        int[] indices = new int[path.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = path.get(i);
        return new FluidSettingsAnchor(list, row, source, holder, label, indices);
    }

    View resolve() {
        RecyclerView list = recycler.get();
        if (list == null || !list.isAttachedToWindow() || adapter.get() != list.getAdapter()) return null;
        View match = null;
        for (int i = 0; i < list.getChildCount(); i++) {
            View row = list.getChildAt(i);
            RecyclerView.ViewHolder holder = list.getChildViewHolder(row);
            if (holder == null || holder.getAdapterPosition() != position) continue;
            // A different label, cell kind or adapter is a different navigation entry.
            // Values/subtitles may change, but the entry's title must still match.
            if (holder.getItemViewType() != viewType || row.getClass() != rowClass
                    || !TextUtils.equals(label, primaryLabel(row, 0))) return null;
            if (match != null) return null;
            match = row;
        }
        if (match == null) return null;
        View source = match;
        for (int index : sourcePath) {
            if (!(source instanceof ViewGroup) || index < 0
                    || index >= ((ViewGroup) source).getChildCount()) return null;
            source = ((ViewGroup) source).getChildAt(index);
        }
        return source.getClass() == sourceClass && source.isAttachedToWindow() && source.isShown()
                ? source : null;
    }

    private static String primaryLabel(View view, int depth) {
        if (depth > 24) return null;
        CharSequence text = view instanceof TextView ? ((TextView) view).getText()
                : view instanceof SimpleTextView ? ((SimpleTextView) view).getText() : null;
        if (!TextUtils.isEmpty(text)) return text.toString();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String label = primaryLabel(group.getChildAt(i), depth + 1);
                if (!TextUtils.isEmpty(label)) return label;
            }
        }
        return null;
    }
}
