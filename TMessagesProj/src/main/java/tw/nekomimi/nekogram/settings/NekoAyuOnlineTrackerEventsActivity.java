package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.radolyn.ayugram.controllers.AyuOnlineTrackerController;
import com.radolyn.ayugram.database.AyuOnlineTrackerEventsDb;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import xyz.nextalone.nagram.ui.UIStyleEngine;

public class NekoAyuOnlineTrackerEventsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AyuOnlineTrackerController.EventsChangeListener {

    private static final int MENU_CLEAR = 1;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private LinearLayout emptyLayout;
    private ActionBarMenuItem clearMenuItem;
    private int m3ScrollOffset;

    private final ArrayList<AyuOnlineTrackerEventsDb.TrackerEvent> allEvents = new ArrayList<>();
    private final ArrayList<Item> displayItems = new ArrayList<>();

    private static class Item {
        int type; // 0 = header, 1 = event
        String dateHeader;
        AyuOnlineTrackerEventsDb.TrackerEvent event;

        static Item header(String title) {
            Item it = new Item();
            it.type = 0;
            it.dateHeader = title;
            return it;
        }

        static Item event(AyuOnlineTrackerEventsDb.TrackerEvent event) {
            Item it = new Item();
            it.type = 1;
            it.event = event;
            return it;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.didSetNewTheme);
        AyuOnlineTrackerController.getInstance(currentAccount).addEventsListener(this);
        loadEvents();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.didSetNewTheme);
        AyuOnlineTrackerController.getInstance(currentAccount).removeEventsListener(this);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            if (fragmentView != null) {
                fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onEventsChanged() {
        AndroidUtilities.runOnUIThread(this::loadEvents);
    }

    private void loadEvents() {
        allEvents.clear();
        allEvents.addAll(AyuOnlineTrackerController.getInstance(currentAccount).getEventsDb().getEvents(0, 1000, 0));
        buildDisplayItems();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    private void buildDisplayItems() {
        displayItems.clear();
        if (allEvents.isEmpty()) {
            return;
        }

        int lastDateKey = 0;
        Calendar calendar = Calendar.getInstance();
        for (AyuOnlineTrackerEventsDb.TrackerEvent ev : allEvents) {
            calendar.setTimeInMillis(ev.timestamp);
            int dateKey = calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100 + calendar.get(Calendar.DAY_OF_MONTH);
            if (lastDateKey != dateKey) {
                lastDateKey = dateKey;
                displayItems.add(Item.header(LocaleController.formatDateChat(ev.timestamp / 1000L)));
            }
            displayItems.add(Item.event(ev));
        }
    }

    private void updateEmptyState() {
        boolean isEmpty = displayItems.isEmpty();
        if (emptyLayout != null) {
            emptyLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (clearMenuItem != null) {
            clearMenuItem.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AyuOnlineTrackerEventsLog", R.string.AyuOnlineTrackerEventsLog));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_CLEAR) {
                    promptClearEvents();
                }
            }
        });

        clearMenuItem = actionBar.createMenu().addItem(MENU_CLEAR, R.drawable.msg_delete_solar);
        clearMenuItem.setContentDescription(LocaleController.getString("AyuOnlineTrackerClearEvents", R.string.AyuOnlineTrackerClearEvents));

        if (UIStyleEngine.isIosLiquidGlass()) {
            SizeNotifierFrameLayout sizeNotifier = new SizeNotifierFrameLayout(context);
            sizeNotifier.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            fragmentView = sizeNotifier;
            actionBar.setDrawBlurBackground(sizeNotifier);
        } else {
            fragmentView = new FrameLayout(context);
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < displayItems.size()) {
                Item item = displayItems.get(position);
                if (item.type == 1 && item.event != null) {
                    Bundle args = new Bundle();
                    args.putLong("user_id", item.event.userId);
                    presentFragment(new ChatActivity(args));
                }
            }
        });

        // Setup theme specific styling
        if (UIStyleEngine.isMaterial3Expressive()) {
            actionBar.setTitle(LocaleController.getString("AyuOnlineTrackerEventsLog", R.string.AyuOnlineTrackerEventsLog));
            actionBar.setM3LargeFlexible(true);
            m3ScrollOffset = 0;
            listView.setPadding(0, getM3HeaderTopPadding(), 0, AndroidUtilities.dp(80));
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (actionBar != null && actionBar.isM3LargeFlexible()) {
                        int maxScroll = actionBar.getM3ExpandedExtraHeight();
                        m3ScrollOffset = Math.max(0, Math.min(maxScroll, m3ScrollOffset + dy));
                        float progress = maxScroll > 0 ? (float) m3ScrollOffset / (float) maxScroll : 1.0f;
                        actionBar.setM3CollapseProgress(progress);
                    }
                }
            });
            actionBar.setM3CollapseProgress(0.0f);
            listView.setSections(
                this::isM3SectionView,
                this::isM3SectionViewType,
                AndroidUtilities.dp(12),
                AndroidUtilities.dp(16),
                listView::drawBackgroundRect,
                true
            );
        } else if (UIStyleEngine.isIosLiquidGlass()) {
            listView.setPadding(0, getIosGlassHeaderTopPadding(), 0, AndroidUtilities.dp(80));
            actionBar.setAdaptiveBackground(listView);
            listView.setSections(
                this::isM3SectionView,
                this::isM3SectionViewType,
                AndroidUtilities.dp(16),
                UIStyleEngine.getCardCornerRadius(),
                listView::drawBackgroundRect,
                true
            );
        } else {
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(80));
            actionBar.setAdaptiveBackground(listView);
            listView.setSections(true);
        }

        // Empty state view
        emptyLayout = new LinearLayout(context);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);

        ImageView emptyIcon = new ImageView(context);
        emptyIcon.setImageResource(R.drawable.msg_calendar2_solar);
        emptyIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
        emptyLayout.addView(emptyIcon, LayoutHelper.createLinear(64, 64, Gravity.CENTER, 0, 0, 0, 16));

        TextView emptyTitle = new TextView(context);
        emptyTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        emptyTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setText(LocaleController.getString("AyuOnlineTrackerEventsEmpty", R.string.AyuOnlineTrackerEventsEmpty));
        emptyLayout.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 8));

        TextView emptyDetail = new TextView(context);
        emptyDetail.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyDetail.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyDetail.setGravity(Gravity.CENTER);
        emptyDetail.setText(LocaleController.getString("AyuOnlineTrackerEventsEmptyDetail", R.string.AyuOnlineTrackerEventsEmptyDetail));
        emptyLayout.addView(emptyDetail, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        frameLayout.addView(emptyLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        updateEmptyState();
        return fragmentView;
    }

    private void promptClearEvents() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AyuOnlineTrackerClearEvents", R.string.AyuOnlineTrackerClearEvents));
        builder.setMessage(LocaleController.getString("AyuOnlineTrackerClearEventsAlert", R.string.AyuOnlineTrackerClearEventsAlert));
        builder.setPositiveButton(LocaleController.getString("AyuOnlineTrackerClear", R.string.AyuOnlineTrackerClear), (dialog, which) -> {
            AyuOnlineTrackerController.getInstance(currentAccount).getEventsDb().clearEvents(0);
            loadEvents();
            if (getParentActivity() != null) {
                BulletinFactory.of(this).createSimpleBulletin(
                    R.drawable.msg_delete_solar,
                    LocaleController.getString("AyuOnlineTrackerEventsCleared", R.string.AyuOnlineTrackerEventsCleared)
                ).show();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private boolean isM3SectionView(View view) {
        if (view == null || view.getParent() != listView) {
            return false;
        }
        RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
        return holder != null && isM3SectionViewType(holder.getItemViewType());
    }

    private boolean isM3SectionViewType(int viewType) {
        return viewType == 1;
    }

    private int getM3HeaderTopPadding() {
        int statusBar = actionBar != null && actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
        int extra = actionBar != null ? actionBar.getM3ExpandedExtraHeight() : AndroidUtilities.dp(76);
        return statusBar + ActionBar.getCurrentActionBarHeight() + extra;
    }

    private int getIosGlassHeaderTopPadding() {
        int statusBar = actionBar != null && actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0;
        return statusBar + ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(10);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = new HeaderCell(mContext);
                int cellBg = (UIStyleEngine.isMaterial3Expressive() || UIStyleEngine.isIosLiquidGlass()) ? 0x00000000 : Theme.getColor(Theme.key_windowBackgroundWhite);
                if (!UIStyleEngine.isMaterial3Expressive() && !UIStyleEngine.isIosLiquidGlass()) {
                    view.setBackgroundColor(cellBg);
                }
            } else {
                view = new EventCell(mContext);
                int cellBg = (UIStyleEngine.isMaterial3Expressive() || UIStyleEngine.isIosLiquidGlass()) ? 0x00000000 : Theme.getColor(Theme.key_windowBackgroundWhite);
                view.setBackgroundColor(cellBg);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = displayItems.get(position);
            if (holder.getItemViewType() == 0) {
                HeaderCell header = (HeaderCell) holder.itemView;
                header.setText(item.dateHeader);
            } else if (holder.getItemViewType() == 1) {
                EventCell cell = (EventCell) holder.itemView;
                TLRPC.User user = getMessagesController().getUser(item.event.userId);
                boolean isLastInGroup = (position == displayItems.size() - 1) || (displayItems.get(position + 1).type == 0);
                cell.setEvent(item.event, user, !isLastInGroup);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return displayItems.get(position).type;
        }
    }

    public static class EventCell extends FrameLayout {
        private final BackupImageView avatarImageView;
        private final AvatarDrawable avatarDrawable;
        private final TextView nameTextView;
        private final TextView statusTextView;
        private final TextView timeTextView;
        private final ImageView statusIconView;
        private boolean needDivider;

        public EventCell(Context context) {
            super(context);
            avatarDrawable = new AvatarDrawable();
            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(21));
            addView(avatarImageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 16, 0));

            nameTextView = new TextView(context);
            nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            nameTextView.setLines(1);
            nameTextView.setMaxLines(1);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 70, 9, 70, 0));

            timeTextView = new TextView(context);
            timeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            timeTextView.setLines(1);
            timeTextView.setMaxLines(1);
            timeTextView.setSingleLine(true);
            timeTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
            addView(timeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 16, 11, 16, 0));

            statusIconView = new ImageView(context);
            addView(statusIconView, LayoutHelper.createFrame(14, 14, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 70, 33, 0, 0));

            statusTextView = new TextView(context);
            statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            statusTextView.setLines(1);
            statusTextView.setMaxLines(1);
            statusTextView.setSingleLine(true);
            statusTextView.setEllipsize(TextUtils.TruncateAt.END);
            statusTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            addView(statusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 88, 30, 16, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        public void setEvent(AyuOnlineTrackerEventsDb.TrackerEvent event, TLRPC.User user, boolean divider) {
            needDivider = divider;
            setWillNotDraw(!divider);

            if (user != null) {
                avatarDrawable.setInfo(user);
                avatarImageView.setForUserOrChat(user, avatarDrawable);
                nameTextView.setText(UserObject.getUserName(user));
            } else {
                avatarDrawable.setInfo(event.userId, "U", null);
                avatarImageView.setImageDrawable(avatarDrawable);
                nameTextView.setText("User " + event.userId);
            }

            timeTextView.setText(LocaleController.getInstance().getFormatterDay().format(new Date(event.timestamp)));

            if (event.eventType == 1) { // Online
                statusIconView.setImageResource(R.drawable.msg_status_set_solar);
                statusIconView.setColorFilter(new PorterDuffColorFilter(0xFF4CAF50, PorterDuff.Mode.SRC_IN));
                statusTextView.setText(LocaleController.getString("AyuOnlineTrackerEventOnline", R.string.AyuOnlineTrackerEventOnline));
                statusTextView.setTextColor(0xFF4CAF50);
            } else if (event.eventType == 0) { // Offline
                statusIconView.setImageResource(R.drawable.msg_notifications_solar);
                statusIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
                if (event.durationMs >= 3000) {
                    String durationStr = LocaleController.formatDuration((int) (event.durationMs / 1000));
                    statusTextView.setText(LocaleController.getString("AyuOnlineTrackerEventOffline", R.string.AyuOnlineTrackerEventOffline) + " • " + LocaleController.formatString("AyuOnlineTrackerEventDuration", R.string.AyuOnlineTrackerEventDuration, durationStr));
                } else {
                    statusTextView.setText(LocaleController.getString("AyuOnlineTrackerEventOffline", R.string.AyuOnlineTrackerEventOffline));
                }
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            } else { // Approximate
                statusIconView.setImageResource(R.drawable.msg_calendar2_solar);
                statusIconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
                String name = getApproximateName(event.eventType);
                statusTextView.setText(name != null ? name : "Status change");
                statusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            }
        }

        private String getApproximateName(int code) {
            switch (code) {
                case -1: return LocaleController.getString("Lately", R.string.Lately);
                case -2: return LocaleController.getString("WithinAWeek", R.string.WithinAWeek);
                case -3: return LocaleController.getString("WithinAMonth", R.string.WithinAMonth);
                case -4: return LocaleController.getString("ALongTimeAgo", R.string.ALongTimeAgo);
                default: return null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(70), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(70) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (fragmentView != null) {
                fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        };

        if (!UIStyleEngine.isMaterial3Expressive() && !UIStyleEngine.isIosLiquidGlass()) {
            themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, EventCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        }
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundGray));
        return themeDescriptions;
    }
}
