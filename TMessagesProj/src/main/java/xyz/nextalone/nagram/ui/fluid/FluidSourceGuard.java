package xyz.nextalone.nagram.ui.fluid;

import android.view.View;
import java.lang.ref.WeakReference;

/** A recycled row must not become the destination of an unrelated item's return. */
final class FluidSourceGuard {
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
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list == null ? null : list.getChildViewHolder(child);
        itemId = holder == null ? -1L : holder.getItemId();
        position = holder == null ? -1 : holder.getAdapterPosition();
    }
    boolean valid() {
        View child = row.get();
        androidx.recyclerview.widget.RecyclerView list = recycler.get();
        if (!inRecycler) return true;
        if (position == -1 && itemId == -1L) return false;
        if (list == null || child == null || child.getParent() != list || adapter.get() != list.getAdapter()) return false;
        androidx.recyclerview.widget.RecyclerView.ViewHolder holder = list.getChildViewHolder(child);
        if (holder == null) return false;
        int currentPosition = holder.getAdapterPosition();
        return currentPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION
                && (stable ? holder.getItemId() == itemId : currentPosition == position);
    }
}

