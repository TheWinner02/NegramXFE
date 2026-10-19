package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.radolyn.ayugram.controllers.AyuOnlineTrackerController;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.ContactsActivity;
import tw.nekomimi.nekogram.ui.cells.M3ConnectedButtonGroupCell;
import xyz.nextalone.nagram.ui.UIStyleEngine;

public class NekoAyuOnlineTrackerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AyuOnlineTrackerController.TrackedUsersChangeListener {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private int m3ScrollOffset;

    private int rowCount;
    private int headerGeneralRow;
    private int enableRow;
    private int enableDetailRow;

    private int eventsLogRow;
    private int eventsLogDetailRow;

    private int headerEventsRow;
    private int notifyOnlineRow;
    private int notifyOfflineRow;
    private int notifyDurationRow;
    private int notifyApproximateRow;
    private int eventsDetailRow;

    private int headerScopeRow;
    private int scopeButtonGroupRow;
    private int scopeAllContactsRow;
    private int scopeSelectedOnlyRow;
    private int scopeDetailRow;

    private int headerActionsRow;
    private int addContactRow;
    private int addUsernameRow;
    private int addPhoneRow;
    private int actionsDetailRow;

    private int headerUsersRow;
    private int usersStartRow;
    private int usersEndRow;
    private int usersDetailRow;

    private final ArrayList<Long> trackedUserIds = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().addObserver(this, NotificationCenter.didSetNewTheme);
        getNotificationCenter().addObserver(this, NotificationCenter.reloadInterface);
        AyuOnlineTrackerController.getInstance(currentAccount).addListener(this);
        loadTrackedUsers();
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        getNotificationCenter().removeObserver(this, NotificationCenter.didSetNewTheme);
        getNotificationCenter().removeObserver(this, NotificationCenter.reloadInterface);
        AyuOnlineTrackerController.getInstance(currentAccount).removeListener(this);
    }

    private void loadTrackedUsers() {
        trackedUserIds.clear();
        trackedUserIds.addAll(AyuOnlineTrackerController.getInstance(currentAccount).getTrackedUserIds());
    }

    @Override
    public void onTrackedUsersChanged() {
        loadTrackedUsers();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_STATUS) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_AVATAR) != 0) {
                if (listView != null) {
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof UserCell) {
                            ((UserCell) child).update(mask);
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.didSetNewTheme || id == NotificationCenter.reloadInterface) {
            if (fragmentView != null) {
                fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            }
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void updateRows() {
        rowCount = 0;
        headerGeneralRow = rowCount++;
        enableRow = rowCount++;
        enableDetailRow = rowCount++;

        boolean enabled = AyuOnlineTrackerController.getInstance(currentAccount).isEnabled();
        if (enabled) {
            eventsLogRow = rowCount++;
            eventsLogDetailRow = rowCount++;

            headerEventsRow = rowCount++;
            notifyOnlineRow = rowCount++;
            notifyOfflineRow = rowCount++;
            notifyDurationRow = rowCount++;
            notifyApproximateRow = rowCount++;
            eventsDetailRow = rowCount++;

            if (UIStyleEngine.isMaterial3Expressive()) {
                headerScopeRow = -1;
                scopeButtonGroupRow = rowCount++;
                scopeAllContactsRow = -1;
                scopeSelectedOnlyRow = -1;
            } else {
                headerScopeRow = rowCount++;
                scopeButtonGroupRow = -1;
                scopeAllContactsRow = rowCount++;
                scopeSelectedOnlyRow = rowCount++;
            }
            scopeDetailRow = rowCount++;

            headerActionsRow = rowCount++;
            addContactRow = rowCount++;
            addUsernameRow = rowCount++;
            addPhoneRow = rowCount++;
            actionsDetailRow = rowCount++;

            if (!trackedUserIds.isEmpty()) {
                headerUsersRow = rowCount++;
                usersStartRow = rowCount;
                rowCount += trackedUserIds.size();
                usersEndRow = rowCount;
                usersDetailRow = rowCount++;
            } else {
                headerUsersRow = -1;
                usersStartRow = -1;
                usersEndRow = -1;
                usersDetailRow = -1;
            }
        } else {
            eventsLogRow = -1;
            eventsLogDetailRow = -1;

            headerEventsRow = -1;
            notifyOnlineRow = -1;
            notifyOfflineRow = -1;
            notifyDurationRow = -1;
            notifyApproximateRow = -1;
            eventsDetailRow = -1;

            headerScopeRow = -1;
            scopeButtonGroupRow = -1;
            scopeAllContactsRow = -1;
            scopeSelectedOnlyRow = -1;
            scopeDetailRow = -1;

            headerActionsRow = -1;
            addContactRow = -1;
            addUsernameRow = -1;
            addPhoneRow = -1;
            actionsDetailRow = -1;

            headerUsersRow = -1;
            usersStartRow = -1;
            usersEndRow = -1;
            usersDetailRow = -1;
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AyuOnlineTracker", R.string.AyuOnlineTracker));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

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

        if (UIStyleEngine.isMaterial3Expressive()) {
            actionBar.setTitle(LocaleController.getString("AyuOnlineTracker", R.string.AyuOnlineTracker));
            actionBar.setM3LargeFlexible(true);
            m3ScrollOffset = 0;
            listView.setPadding(0, getM3HeaderTopPadding(), 0, AndroidUtilities.dp(80));
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (layoutManager != null && actionBar != null && actionBar.isM3LargeFlexible()) {
                        int maxScroll = actionBar.getM3ExpandedExtraHeight();
                        m3ScrollOffset = Math.max(0, Math.min(maxScroll, m3ScrollOffset + dy));
                        float progress = maxScroll > 0 ? (float) m3ScrollOffset / (float) maxScroll : 1.0f;
                        actionBar.setM3CollapseProgress(progress);
                    }
                }
            });
            actionBar.setM3CollapseProgress(0.0f);
            listView.setSections(
                this::isM3SettingsSectionView,
                this::isM3SettingsSectionViewType,
                AndroidUtilities.dp(12),
                AndroidUtilities.dp(16),
                listView::drawBackgroundRect,
                true
            );
        } else if (UIStyleEngine.isIosLiquidGlass()) {
            listView.setPadding(0, getIosGlassHeaderTopPadding(), 0, AndroidUtilities.dp(80));
            actionBar.setAdaptiveBackground(listView);
            listView.setSections(
                this::isM3SettingsSectionView,
                this::isM3SettingsSectionViewType,
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
        listView.setOnItemClickListener((view, position) -> {
            AyuOnlineTrackerController controller = AyuOnlineTrackerController.getInstance(currentAccount);
            if (position == enableRow) {
                controller.setEnabled(!controller.isEnabled());
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(controller.isEnabled());
                }
                updateRows();
                listAdapter.notifyDataSetChanged();
            } else if (position == eventsLogRow) {
                presentFragment(new NekoAyuOnlineTrackerEventsActivity());
            } else if (position == notifyOnlineRow) {
                controller.setNotifyOnline(!controller.isNotifyOnline());
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(controller.isNotifyOnline());
                }
            } else if (position == notifyOfflineRow) {
                controller.setNotifyOffline(!controller.isNotifyOffline());
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(controller.isNotifyOffline());
                }
            } else if (position == notifyDurationRow) {
                controller.setNotifyDuration(!controller.isNotifyDuration());
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(controller.isNotifyDuration());
                }
            } else if (position == notifyApproximateRow) {
                controller.setNotifyApproximate(!controller.isNotifyApproximate());
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(controller.isNotifyApproximate());
                }
            } else if (position == scopeAllContactsRow) {
                controller.setTargetMode(AyuOnlineTrackerController.TARGET_ALL_CONTACTS);
                listAdapter.notifyItemChanged(scopeAllContactsRow);
                listAdapter.notifyItemChanged(scopeSelectedOnlyRow);
            } else if (position == scopeSelectedOnlyRow) {
                controller.setTargetMode(AyuOnlineTrackerController.TARGET_SELECTED_ONLY);
                listAdapter.notifyItemChanged(scopeAllContactsRow);
                listAdapter.notifyItemChanged(scopeSelectedOnlyRow);
            } else if (position == addContactRow) {
                openContactsPicker();
            } else if (position == addUsernameRow) {
                openAddByUsernameDialog();
            } else if (position == addPhoneRow) {
                openAddByPhoneDialog();
            } else if (position >= usersStartRow && position < usersEndRow) {
                int index = position - usersStartRow;
                if (index >= 0 && index < trackedUserIds.size()) {
                    long userId = trackedUserIds.get(index);
                    openUserOptionsDialog(userId);
                }
            }
        });

        return fragmentView;
    }

    private boolean isM3SettingsSectionView(View view) {
        if (view == null || view.getParent() != listView) {
            return false;
        }
        RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
        return holder != null && isM3SettingsSectionViewType(holder.getItemViewType());
    }

    private boolean isM3SettingsSectionViewType(int viewType) {
        return viewType == 1 || viewType == 3 || viewType == 4 || viewType == 5 || viewType == 6;
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

    private void openContactsPicker() {
        Bundle args = new Bundle();
        args.putBoolean("onlyUsers", true);
        args.putBoolean("destroyAfterSelect", true);
        args.putBoolean("returnAsResult", true);
        ContactsActivity activity = new ContactsActivity(args);
        activity.setDelegate((user, param, act) -> {
            if (user != null) {
                boolean added = AyuOnlineTrackerController.getInstance(currentAccount).addTrackedUser(user.id);
                if (added) {
                    BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                        R.drawable.msg_contact_add_solar,
                        LocaleController.getString("AyuOnlineTrackerUserAdded", R.string.AyuOnlineTrackerUserAdded)
                    ).show();
                } else {
                    BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                        R.drawable.msg_block_solar,
                        LocaleController.getString("AyuOnlineTrackerUserAlreadyAdded", R.string.AyuOnlineTrackerUserAlreadyAdded)
                    ).show();
                }
            }
            if (act != null) {
                act.finishFragment();
            }
        });
        presentFragment(activity);
    }

    private void openAddByUsernameDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AyuOnlineTrackerAddByUsername", R.string.AyuOnlineTrackerAddByUsername));

        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHintText(LocaleController.getString("AyuOnlineTrackerEnterUsername", R.string.AyuOnlineTrackerEnterUsername));
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String username = editText.getText().toString().trim();
            if (TextUtils.isEmpty(username)) return;
            resolveAndAddUsername(username);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void resolveAndAddUsername(String username) {
        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setMessage(LocaleController.getString("AyuOnlineTrackerSearching", R.string.AyuOnlineTrackerSearching));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.showDelayed(300);

        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username.startsWith("@") ? username.substring(1) : username;

        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
            }
            if (error == null && response instanceof TLRPC.TL_contacts_resolvedPeer) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                if (!res.users.isEmpty()) {
                    TLRPC.User user = res.users.get(0);
                    getMessagesController().putUser(user, false);
                    boolean added = AyuOnlineTrackerController.getInstance(currentAccount).addTrackedUser(user.id);
                    if (added) {
                        BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                            R.drawable.msg_contact_add_solar,
                            LocaleController.getString("AyuOnlineTrackerUserAdded", R.string.AyuOnlineTrackerUserAdded)
                        ).show();
                    } else {
                        BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                            R.drawable.msg_block_solar,
                            LocaleController.getString("AyuOnlineTrackerUserAlreadyAdded", R.string.AyuOnlineTrackerUserAlreadyAdded)
                        ).show();
                    }
                    return;
                }
            }
            BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                R.drawable.msg_block_solar,
                LocaleController.getString("AyuOnlineTrackerUserNotFound", R.string.AyuOnlineTrackerUserNotFound)
            ).show();
        }));
    }

    private void openAddByPhoneDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("AyuOnlineTrackerAddByPhone", R.string.AyuOnlineTrackerAddByPhone));

        FrameLayout container = new FrameLayout(getParentActivity());
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), 0);

        final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        editText.setHintText(LocaleController.getString("AyuOnlineTrackerEnterPhone", R.string.AyuOnlineTrackerEnterPhone));
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_PHONE);
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String phone = editText.getText().toString().trim();
            if (TextUtils.isEmpty(phone)) return;
            resolveAndAddPhone(phone);
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void resolveAndAddPhone(String phone) {
        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setMessage(LocaleController.getString("AyuOnlineTrackerSearching", R.string.AyuOnlineTrackerSearching));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.showDelayed(300);

        TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
        TLRPC.TL_inputPhoneContact input = new TLRPC.TL_inputPhoneContact();
        input.phone = phone.replace(" ", "").replace("-", "");
        input.first_name = input.phone;
        input.last_name = "";
        input.client_id = Utilities.random.nextLong();
        req.contacts.add(input);

        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {
            }
            if (error == null && response instanceof TLRPC.TL_contacts_importedContacts) {
                TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts) response;
                if (!res.users.isEmpty()) {
                    TLRPC.User user = res.users.get(0);
                    getMessagesController().putUser(user, false);
                    boolean added = AyuOnlineTrackerController.getInstance(currentAccount).addTrackedUser(user.id);
                    if (added) {
                        BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                            R.drawable.msg_contact_add_solar,
                            LocaleController.getString("AyuOnlineTrackerUserAdded", R.string.AyuOnlineTrackerUserAdded)
                        ).show();
                    } else {
                        BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                            R.drawable.msg_block_solar,
                            LocaleController.getString("AyuOnlineTrackerUserAlreadyAdded", R.string.AyuOnlineTrackerUserAlreadyAdded)
                        ).show();
                    }
                    return;
                }
            }
            BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                R.drawable.msg_block_solar,
                LocaleController.getString("AyuOnlineTrackerUserNotFound", R.string.AyuOnlineTrackerUserNotFound)
            ).show();
        }));
    }

    private void openUserOptionsDialog(long userId) {
        if (getParentActivity() == null) return;
        TLRPC.User user = getMessagesController().getUser(userId);
        String name = user != null ? ContactsController.formatName(user.first_name, user.last_name) : "ID " + userId;

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(name);
        CharSequence[] items = new CharSequence[]{
            LocaleController.getString("SendMessage", R.string.SendMessage),
            LocaleController.getString("AyuOnlineTrackerRemoveUser", R.string.AyuOnlineTrackerRemoveUser)
        };
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                presentFragment(ChatActivity.of(userId));
            } else if (which == 1) {
                AyuOnlineTrackerController.getInstance(currentAccount).removeTrackedUser(userId);
                BulletinFactory.of(NekoAyuOnlineTrackerActivity.this).createSimpleBulletin(
                    R.drawable.msg_delete_solar,
                    LocaleController.getString("AyuOnlineTrackerRemoveUser", R.string.AyuOnlineTrackerRemoveUser)
                ).show();
            }
        });
        showDialog(builder.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == enableRow
                || position == eventsLogRow
                || position == notifyOnlineRow
                || position == notifyOfflineRow
                || position == notifyDurationRow
                || position == notifyApproximateRow
                || position == scopeAllContactsRow
                || position == scopeSelectedOnlyRow
                || position == addContactRow
                || position == addUsernameRow
                || position == addPhoneRow
                || (position >= usersStartRow && position < usersEndRow);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            int cellBg = (UIStyleEngine.isMaterial3Expressive() || UIStyleEngine.isIosLiquidGlass()) ? 0x00000000 : Theme.getColor(Theme.key_windowBackgroundWhite);
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    if (!UIStyleEngine.isMaterial3Expressive() && !UIStyleEngine.isIosLiquidGlass()) {
                        view.setBackgroundColor(cellBg);
                    }
                    break;
                case 1:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(cellBg);
                    break;
                case 2:
                    TextInfoPrivacyCell privacyCell = new TextInfoPrivacyCell(mContext);
                    if (UIStyleEngine.isIosLiquidGlass()) {
                        privacyCell.setBackground(null);
                    }
                    view = privacyCell;
                    break;
                case 3:
                    view = new RadioCell(mContext);
                    view.setBackgroundColor(cellBg);
                    break;
                case 4:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(cellBg);
                    break;
                case 5:
                    view = new UserCell(mContext, 6, 0, false);
                    view.setBackgroundColor(cellBg);
                    break;
                case 6:
                default:
                    view = new M3ConnectedButtonGroupCell(mContext);
                    view.setBackgroundColor(cellBg);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            AyuOnlineTrackerController controller = AyuOnlineTrackerController.getInstance(currentAccount);
            switch (holder.getItemViewType()) {
                case 0: {
                    HeaderCell header = (HeaderCell) holder.itemView;
                    if (position == headerGeneralRow) {
                        header.setText(LocaleController.getString("General", R.string.General));
                    } else if (position == headerEventsRow) {
                        header.setText(LocaleController.getString("AyuOnlineTrackerNotifyEventsHeader", R.string.AyuOnlineTrackerNotifyEventsHeader));
                    } else if (position == headerScopeRow) {
                        header.setText(LocaleController.getString("AyuOnlineTrackerTargetModeHeader", R.string.AyuOnlineTrackerTargetModeHeader));
                    } else if (position == headerActionsRow) {
                        header.setText(LocaleController.getString("AyuOnlineTrackerManageUsersHeader", R.string.AyuOnlineTrackerManageUsersHeader));
                    } else if (position == headerUsersRow) {
                        String count = String.valueOf(trackedUserIds.size());
                        header.setText(LocaleController.getString("AyuOnlineTrackerUserListHeader", R.string.AyuOnlineTrackerUserListHeader) + " (" + count + ")");
                    }
                    break;
                }
                case 1: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == enableRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AyuOnlineTrackerEnabled", R.string.AyuOnlineTrackerEnabled), controller.isEnabled(), false);
                    } else if (position == notifyOnlineRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AyuOnlineTrackerNotifyOnline", R.string.AyuOnlineTrackerNotifyOnline), controller.isNotifyOnline(), true);
                    } else if (position == notifyOfflineRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AyuOnlineTrackerNotifyOffline", R.string.AyuOnlineTrackerNotifyOffline), controller.isNotifyOffline(), true);
                    } else if (position == notifyDurationRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AyuOnlineTrackerNotifyDuration", R.string.AyuOnlineTrackerNotifyDuration), controller.isNotifyDuration(), true);
                    } else if (position == notifyApproximateRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("AyuOnlineTrackerNotifyApproximate", R.string.AyuOnlineTrackerNotifyApproximate), controller.isNotifyApproximate(), false);
                    }
                    break;
                }
                case 2: {
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == enableDetailRow) {
                        privacyCell.setText(LocaleController.getString("AyuOnlineTrackerNotice", R.string.AyuOnlineTrackerNotice));
                    } else if (position == eventsLogDetailRow) {
                        privacyCell.setText(LocaleController.getString("AyuOnlineTrackerEventsLogDetail", R.string.AyuOnlineTrackerEventsLogDetail));
                    } else if (position == eventsDetailRow) {
                        privacyCell.setText(LocaleController.getString("AyuOnlineTrackerNotifyDurationNotice", R.string.AyuOnlineTrackerNotifyDurationNotice));
                    } else if (position == scopeDetailRow) {
                        privacyCell.setText(null);
                        privacyCell.setFixedSize(8);
                    } else if (position == actionsDetailRow) {
                        privacyCell.setText(null);
                        privacyCell.setFixedSize(8);
                    } else if (position == usersDetailRow) {
                        privacyCell.setText(null);
                        privacyCell.setFixedSize(16);
                    }
                    break;
                }
                case 3: {
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    int targetMode = controller.getTargetMode();
                    if (position == scopeAllContactsRow) {
                        radioCell.setText(LocaleController.getString("AyuOnlineTrackerTargetAllContacts", R.string.AyuOnlineTrackerTargetAllContacts), targetMode == AyuOnlineTrackerController.TARGET_ALL_CONTACTS, true);
                    } else if (position == scopeSelectedOnlyRow) {
                        radioCell.setText(LocaleController.getString("AyuOnlineTrackerTargetSelectedOnly", R.string.AyuOnlineTrackerTargetSelectedOnly), targetMode == AyuOnlineTrackerController.TARGET_SELECTED_ONLY, false);
                    }
                    break;
                }
                case 4: {
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == eventsLogRow) {
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlackText);
                        int count = controller.getEventsDb().getEventsCount();
                        String countStr = count > 0 ? LocaleController.formatPluralString("AyuOnlineTrackerEventsCount", count) : null;
                        textCell.setTextAndValueAndIcon(LocaleController.getString("AyuOnlineTrackerEventsLog", R.string.AyuOnlineTrackerEventsLog), countStr, R.drawable.msg_calendar2_solar, false);
                    } else {
                        textCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        if (position == addContactRow) {
                            textCell.setTextAndIcon(LocaleController.getString("AyuOnlineTrackerAddContact", R.string.AyuOnlineTrackerAddContact), R.drawable.msg_contacts_solar, true);
                        } else if (position == addUsernameRow) {
                            textCell.setTextAndIcon(LocaleController.getString("AyuOnlineTrackerAddByUsername", R.string.AyuOnlineTrackerAddByUsername), R.drawable.msg_contacts_name_solar, true);
                        } else if (position == addPhoneRow) {
                            textCell.setTextAndIcon(LocaleController.getString("AyuOnlineTrackerAddByPhone", R.string.AyuOnlineTrackerAddByPhone), R.drawable.msg_contact_add_solar, false);
                        }
                    }
                    break;
                }
                case 5: {
                    UserCell userCell = (UserCell) holder.itemView;
                    int index = position - usersStartRow;
                    if (index >= 0 && index < trackedUserIds.size()) {
                        long userId = trackedUserIds.get(index);
                        TLRPC.User user = getMessagesController().getUser(userId);
                        boolean divider = (position != usersEndRow - 1);
                        if (user != null) {
                            userCell.setData(user, null, null, 0, divider);
                        } else {
                            userCell.setData(null, "User " + userId, null, 0, divider);
                        }
                    }
                    break;
                }
                case 6: {
                    M3ConnectedButtonGroupCell cell = (M3ConnectedButtonGroupCell) holder.itemView;
                    String[] items = new String[]{
                        LocaleController.getString("AyuOnlineTrackerTargetAllContacts", R.string.AyuOnlineTrackerTargetAllContacts),
                        LocaleController.getString("AyuOnlineTrackerTargetSelectedOnly", R.string.AyuOnlineTrackerTargetSelectedOnly)
                    };
                    int selectedIndex = controller.getTargetMode() == AyuOnlineTrackerController.TARGET_ALL_CONTACTS ? 0 : 1;
                    cell.setTextAndItems(
                        LocaleController.getString("AyuOnlineTrackerTargetModeHeader", R.string.AyuOnlineTrackerTargetModeHeader),
                        items,
                        selectedIndex,
                        getResourceProvider(),
                        index -> {
                            int newMode = (index == 0) ? AyuOnlineTrackerController.TARGET_ALL_CONTACTS : AyuOnlineTrackerController.TARGET_SELECTED_ONLY;
                            controller.setTargetMode(newMode);
                            if (listAdapter != null && scopeButtonGroupRow != -1) {
                                listAdapter.notifyItemChanged(scopeButtonGroupRow);
                            }
                        }
                    );
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerGeneralRow || position == headerEventsRow || position == headerScopeRow || position == headerActionsRow || position == headerUsersRow) {
                return 0;
            } else if (position == enableRow || position == notifyOnlineRow || position == notifyOfflineRow || position == notifyDurationRow || position == notifyApproximateRow) {
                return 1;
            } else if (position == enableDetailRow || position == eventsLogDetailRow || position == eventsDetailRow || position == scopeDetailRow || position == actionsDetailRow || position == usersDetailRow) {
                return 2;
            } else if (position == scopeAllContactsRow || position == scopeSelectedOnlyRow) {
                return 3;
            } else if (position == eventsLogRow || position == addContactRow || position == addUsernameRow || position == addPhoneRow) {
                return 4;
            } else if (position == scopeButtonGroupRow) {
                return 6;
            } else {
                return 5;
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
            themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCheckCell.class, RadioCell.class, TextCell.class, UserCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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
