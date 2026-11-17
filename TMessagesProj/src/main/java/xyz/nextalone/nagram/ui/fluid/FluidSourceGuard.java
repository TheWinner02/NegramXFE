package xyz.nextalone.nagram.ui.fluid;

import android.view.View;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import androidx.recyclerview.widget.RecyclerView;

/** A recycled row must not become the destination of an unrelated item's return. */
final class FluidSourceGuard {
    // One observer per adapter, containing no View or adapter reference. Weak keys let an
    // abandoned adapter and its observer be collected together. RecyclerView updates run on UI.
    private static final WeakHashMap<RecyclerView.Adapter, AdapterEpoch> epochs = new WeakHashMap<>();
    private static final class AdapterEpoch extends RecyclerView.AdapterDataObserver {
        long version;
        @Override public void onChanged() { version++; }
        @Override public void onItemRangeChanged(int start, int count) { version++; }
        @Override public void onItemRangeChanged(int start, int count, Object payload) { version++; }
        @Override public void onItemRangeInserted(int start, int count) { version++; }
        @Override public void onItemRangeRemoved(int start, int count) { version++; }
        @Override public void onItemRangeMoved(int from, int to, int count) { version++; }
    }
    private final AdapterEpoch epoch;
    private final long version;
    private final WeakReference<androidx.recyclerview.widget.RecyclerView> recycler;
    private final WeakReference<View> row;
    private final WeakReference<androidx.recyclerview.widget.RecyclerView.Adapter> adapter;
    private final long itemId;
    private final int position;
    private final boolean stable;
    private final boolean inRecycler;
    FluidSourceGuard(View source) {
        View child = source;
        while (child != null && child.getParent() instanceof View
                && !(child.getParent() instanceof androidx.recyclerview.widget.RecyclerView)) child = (View) child.getParent();
        androidx.recyclerview.widget.RecyclerView list = child != null && child.getParent() instanceof androidx.recyclerview.widget.RecyclerView
                ? (androidx.recyclerview.widget.RecyclerView) child.getParent() : null;
        inRecycler = list != null;
        recycler = new WeakReference<>(list); row = new WeakReference<>(list == null ? null : child);
        androidx.recyclerview.widget.RecyclerView.Adapter data = list == null ? null : list.getAdapter();
        adapter = new WeakReference<>(data); stable = data != null && data.hasStableIds();
        if (data != null && !stable) {
            AdapterEpoch observed = epochs.get(data);
            if (observed == null) {
                observed = new AdapterEpoch();
                data.registerAdapterDataObserver(observed);
                epochs.put(data, observed);
            }
            epoch = observed;
            version = observed.version;
        } else {
            epoch = null;
            version = 0;
        }
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list == null ? null : list.getChildViewHolder(child);
        itemId = holder == null ? -1L : holder.getItemId();
        position = holder == null ? -1 : holder.getAdapterPosition();
    }
    boolean valid() {
        return invalidReason() == null;
    }

    /** Diagnostic codes only; never include row text or account data. */
    String invalidReason() {
        View child = row.get();
        androidx.recyclerview.widget.RecyclerView list = recycler.get();
        if (!inRecycler) return null;
        if (epoch != null && epoch.version != version) return "adapter_updated";
        if ((position == -1 && itemId == -1L) || (stable && itemId == -1L)) return "unbound_origin";
        if (list == null || child == null) return "origin_collected";
        if (child.getParent() != list) return "row_detached";
        if (adapter.get() != list.getAdapter()) return "adapter_replaced";
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list.getChildViewHolder(child);
        if (holder == null) return "holder_missing";
        int currentPosition = holder.getAdapterPosition();
        if (currentPosition == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return "position_pending";
        return (stable ? holder.getItemId() == itemId : currentPosition == position) ? null : "row_rebound";
    }
}
