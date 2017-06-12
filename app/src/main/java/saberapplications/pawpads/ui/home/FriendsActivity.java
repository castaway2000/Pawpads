package saberapplications.pawpads.ui.home;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBRoster;
import com.quickblox.chat.listeners.QBRosterListener;
import com.quickblox.chat.listeners.QBSubscriptionListener;
import com.quickblox.chat.model.QBPresence;
import com.quickblox.chat.model.QBRosterEntry;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.core.request.QBPagedRequestBuilder;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.roster.packet.RosterPacket;

import java.util.ArrayList;
import java.util.Collection;

import saberapplications.pawpads.C;
import saberapplications.pawpads.R;
import saberapplications.pawpads.Util;
import saberapplications.pawpads.databinding.ActivityFriendsBinding;
import saberapplications.pawpads.ui.BaseActivity;
import saberapplications.pawpads.ui.chat.ChatActivity;
import saberapplications.pawpads.ui.dialogs.DialogsListActivity;
import saberapplications.pawpads.ui.profile.ProfileActivity;
import saberapplications.pawpads.util.AvatarLoaderHelper;
import saberapplications.pawpads.util.ChatRosterHelper;
import saberapplications.pawpads.views.BaseListAdapter;

public class FriendsActivity extends BaseActivity implements BaseListAdapter.Callback<QBUser> {
    public static final String TAG = FriendsActivity.class.getSimpleName();
    ActivityFriendsBinding binding;
    FriendsAdapter adapter;
    int currentPage = 1;
    private int currentUserId;
    QBRoster chatRoster;
    AlertDialog requestDialog;
    private boolean isLoading;
    private int newUserInviteId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_friends);
        binding.setActivity(this);
        adapter = new FriendsAdapter();
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        currentUserId = prefs.getInt(C.QB_USERID, 0);
        adapter.setCurrentUserId(currentUserId);
        binding.friendsListView.setAdapter(adapter);
        adapter.setCallback(this);
        binding.swipelayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if(isLoading) {
                    binding.swipelayout.setRefreshing(false);
                    return;
                }
                adapter.clear();
                currentPage = 0;
                loadData();
                binding.swipelayout.setRefreshing(false);
            }
        });

        chatRoster = getChatRoster();

        Handler h = new Handler();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(adapter.isShowInitialLoad()) {
                    adapter.clear();
                    currentPage = 0;
                    loadData();
                }
            }
        }, 500);
    }

    private void showAddToFriendsRequestDialog(final QBUser user) {
        if(user == null) return;

        if(requestDialog != null && requestDialog.isShowing()) return;
        final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this)
                .setCancelable(true);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_to_friends, null);
        ImageView dialogAvatar = (ImageView) view.findViewById(R.id.dialog_avatar);
        if(user.getFileId() != null) {
            float density = getResources().getDisplayMetrics().density;
            AvatarLoaderHelper.loadImage(user.getFileId(), dialogAvatar,
                    Math.round(density * 60), Math.round(density * 60));
        }

        TextView messageText = (TextView) view.findViewById(R.id.message_text);
        String userName = user.getFullName() == null ? user.getLogin() : user.getFullName();
        String sourceString = getString(R.string.user) + " <b>" + userName + "</b> " + getString(R.string.wants_to_add_you_to_friendlist);
        messageText.setText(Html.fromHtml(sourceString));

        builder.setView(view);

        requestDialog = builder.create();
        requestDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        requestDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        requestDialog.show();

        TextView acceptButton = (TextView) view.findViewById(R.id.send_friend_request);
        acceptButton.setText(getString(R.string.accept_invite));
        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acceptRequest(user.getId());
                requestDialog.dismiss();
            }
        });

        TextView rejectButton = (TextView) view.findViewById(R.id.cancel_button);
        rejectButton.setText(getString(R.string.reject_invite));
        rejectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rejectRequest(user.getId());
                requestDialog.dismiss();
            }
        });
    }

    private void loadDataAfterChanges() {
        adapter.clear();
        currentPage = 0;
        loadData();
        binding.swipelayout.setRefreshing(false);
    }

    private void acceptRequest(int userId) {
        try {
            chatRoster.confirmSubscription(userId);
            loadDataAfterChanges();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        } catch (SmackException.NotLoggedInException e) {
            e.printStackTrace();
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NoResponseException e) {
            e.printStackTrace();
        }
    }

    private void rejectRequest(int userId) {
        try {
            chatRoster.reject(userId);
            chatRoster.unsubscribe(userId);
            if (chatRoster.getEntry(userId) != null && chatRoster.contains(userId)) {
                chatRoster.removeEntry(chatRoster.getEntry(userId));
            }
            if(newUserInviteId == userId) newUserInviteId = 0;
            loadDataAfterChanges();
        } catch (SmackException.NotConnectedException e) {
            e.printStackTrace();
        } catch (XMPPException e) {
            e.printStackTrace();
        } catch (SmackException.NotLoggedInException e) {
            e.printStackTrace();
        } catch (SmackException.NoResponseException e) {
            e.printStackTrace();
        }
    }

    public void loadData() {
        isLoading = true;
        ArrayList<Integer> usersIds = new ArrayList<>();
        if(chatRoster != null) {
            Collection<QBRosterEntry> entries;
            if(chatRoster.getUnfiledEntries() != null && chatRoster.getUnfiledEntries().size()>0) {
                entries = chatRoster.getUnfiledEntries();
            } else {
                entries = chatRoster.getEntries();
            }
            Log.d(TAG, "Collection<QBRosterEntry> entries " + entries.toString());
            for(QBRosterEntry entry : entries) {
                usersIds.add(entry.getUserId());
            }
        }
        if(newUserInviteId != 0 && !usersIds.contains(newUserInviteId)) getUserById(newUserInviteId);
        if(usersIds.size() > 0) {
            QBPagedRequestBuilder pagedRequestBuilder = new QBPagedRequestBuilder();
            pagedRequestBuilder.setPage(currentPage);
            pagedRequestBuilder.setPerPage(10);
            QBUsers.getUsersByIDs(usersIds, pagedRequestBuilder, new QBEntityCallback<ArrayList<QBUser>>() {
                @Override
                public void onSuccess(ArrayList<QBUser> users, Bundle params) {
                    if (adapter==null) return;
                    if (users.size() > 0) {
                        adapter.addItems(users);
                        currentPage++;
                    }

                    if (users.size() == 0 || users.size() < 10) {
                        adapter.disableLoadMore();
                    }
                    binding.swipelayout.setRefreshing(false);
                    isLoading = false;
                }

                @Override
                public void onError(QBResponseException errors) {
                    if (getApplicationContext()==null) return;
                    adapter.disableLoadMore();
                    Util.onError(errors, getApplicationContext());
                    isLoading = false;
                }
            });
        } else {
            adapter.disableLoadMore();
            binding.swipelayout.setRefreshing(false);
            isLoading = false;
        }
    }

    @Override
    public void onLoadMore() {
        loadData();
    }

    @Override
    public void onItemClick(final QBUser user) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (user != null) {
                    if(chatRoster != null && chatRoster.getEntry(user.getId()) != null &&
                            chatRoster.getEntry(user.getId()).getType() == RosterPacket.ItemType.none &&
                            chatRoster.getEntry(user.getId()).getStatus() == null) {
                        showAddToFriendsRequestDialog(user);
                    } else {
                        Intent intent = new Intent(FriendsActivity.this, ProfileActivity.class);
                        intent.putExtra(C.QB_USERID, user.getId());
                        intent.putExtra(C.QB_USER, user);
                        startActivity(intent);
                    }
                }
            }
        }, 50);
    }

    public QBRoster getChatRoster() {
        QBSubscriptionListener subscriptionListener = new QBSubscriptionListener() {
            @Override
            public void subscriptionRequested(int userId) {
                Log.d(TAG, "subscriptionRequested " + userId);
                newUserInviteId = userId;
                try {
                    chatRoster.reload();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadDataAfterChanges();
                        }
                    });
                } catch (SmackException.NotLoggedInException e) {
                    e.printStackTrace();
                } catch (SmackException.NotConnectedException e) {
                    e.printStackTrace();
                }
            }
        };

        QBRosterListener rosterListener = new QBRosterListener() {
            @Override
            public void entriesDeleted(Collection<Integer> userIds) {
                Log.d(TAG, "entriesDeleted " + userIds.toString());
                if(chatRoster != null) {
                    for (Integer userId : userIds) {
                        try {
                            if (chatRoster.getEntry(userId) != null && chatRoster.contains(userId)) {
                                chatRoster.removeEntry(chatRoster.getEntry(userId));
                            }
                        } catch (XMPPException e) {
                            e.printStackTrace();
                        } catch (SmackException.NotLoggedInException e) {
                            e.printStackTrace();
                        } catch (SmackException.NotConnectedException e) {
                            e.printStackTrace();
                        } catch (SmackException.NoResponseException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void entriesAdded(Collection<Integer> userIds) {
                Log.d(TAG, "entriesAdded " + userIds.toString());
            }

            @Override
            public void entriesUpdated(Collection<Integer> userIds) {
                Log.d(TAG, "entriesUpdated " + userIds.toString());
            }

            @Override
            public void presenceChanged(QBPresence presence) {
                Log.d(TAG, "presenceChanged " + presence.toString());
            }
        };

        // Do this after success Chat login
        QBRoster chatRoster = QBChatService.getInstance().getRoster(QBRoster.SubscriptionMode.mutual, subscriptionListener);
        if(chatRoster == null) return null;
        chatRoster.addRosterListener(rosterListener);

        return chatRoster;
    }

    private void getUserById(int userId) {
        QBUsers.getUser(userId, new QBEntityCallback<QBUser>() {
            @Override
            public void onSuccess(QBUser result, Bundle params) {
                adapter.addItem(result);
            }

            @Override
            public void onError(QBResponseException e) {
                Util.onError(e, FriendsActivity.this);
            }

        });
    }
}
