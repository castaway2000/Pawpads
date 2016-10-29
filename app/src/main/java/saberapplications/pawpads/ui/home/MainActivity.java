package saberapplications.pawpads.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBPrivateChat;
import com.quickblox.chat.QBPrivateChatManager;
import com.quickblox.chat.exception.QBChatException;
import com.quickblox.chat.listeners.QBMessageListener;
import com.quickblox.chat.listeners.QBPrivateChatManagerListener;
import com.quickblox.chat.model.QBChatMessage;
import com.quickblox.chat.model.QBDialog;
import com.quickblox.core.QBEntityCallback;
import com.quickblox.core.QBEntityCallbackImpl;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.core.request.QBRequestGetBuilder;
import com.quickblox.location.model.QBLocation;
import com.quickblox.messages.QBMessages;
import com.quickblox.messages.model.QBEnvironment;
import com.quickblox.messages.model.QBSubscription;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;

import java.io.IOException;
import java.util.ArrayList;

import saberapplications.pawpads.C;
import saberapplications.pawpads.GPS;
import saberapplications.pawpads.R;
import saberapplications.pawpads.UserLocalStore;
import saberapplications.pawpads.Util;
import saberapplications.pawpads.databinding.ActivityMainBinding;
import saberapplications.pawpads.ui.AboutActivity;
import saberapplications.pawpads.ui.BaseActivity;
import saberapplications.pawpads.ui.PrefrenceActivity;
import saberapplications.pawpads.ui.chat.ChatActivity;
import saberapplications.pawpads.ui.login.LoginActivity;
import saberapplications.pawpads.ui.profile.ProfileActivity;
import saberapplications.pawpads.ui.profile.ProfileEditActivity;
import saberapplications.pawpads.util.AvatarLoaderHelper;


public class MainActivity extends BaseActivity {

    String TAG = "MAIN";
    GoogleCloudMessaging gcm;
    SharedPreferences prefs;
    Context context;
    String regid;
    String msg;
    int range;
    ActivityMainBinding binding;
    NearByFragment nearByFragment;
    ChatsFragment chatsFragment;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ListView listView;
    UserLocalStore userLocalStore;
    private UserListAdapter adapter;
    private Location lastListUpdatedLocation;
    public GPS gps;

    private QBPrivateChatManagerListener chatListener = new QBPrivateChatManagerListener() {
        @Override
        public void chatCreated(QBPrivateChat qbPrivateChat, final boolean createdLocally) {
            if (!createdLocally) {
                qbPrivateChat.addMessageListener(new QBMessageListener<QBPrivateChat>() {
                    @Override
                    public void processMessage(QBPrivateChat qbPrivateChat, final QBChatMessage qbChatMessage) {
                        if (qbChatMessage.getProperties().containsKey("blocked")) {
                            return;
                        }
                        MainActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (Util.IM_ALERT == true) {
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("New Chat Message")
                                            .setMessage(qbChatMessage.getBody())
                                            .setPositiveButton("Open chat", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                                                    intent.putExtra(ChatActivity.DIALOG_ID, qbChatMessage.getDialogId().toString());
                                                    intent.putExtra(ChatActivity.RECIPIENT_ID, qbChatMessage.getSenderId().toString());
                                                    startActivity(intent);
                                                }
                                            })
                                            .setNegativeButton("Cancel", null)
                                            .show();
                                }
                            }
                        });
                    }

                    @Override
                    public void processError(QBPrivateChat qbPrivateChat, QBChatException e, QBChatMessage qbChatMessage) {
                        Util.onError(e, MainActivity.this);
                    }

                });
            }
        }
    };
    private ActionBarDrawerToggle mDrawerToggle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setActivity(this);
        setSupportActionBar(binding.toolbar);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                binding.navigationDrawer,
                binding.toolbar,
                R.string.open,
                R.string.close
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);

            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

            }
        };
        binding.navigationDrawer.addDrawerListener(mDrawerToggle);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setTitle(null);
        gps = new GPS(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //  mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipelayout);
        //  mSwipeRefreshLayout.setOnRefreshListener(this);
        context = getApplicationContext();
        //listView = (ListView) findViewById(R.id.listView);
        userLocalStore = new UserLocalStore(this);
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        Util.UNIT_OF_MEASURE = defaultSharedPreferences.getString("unit", "MI");
        range = Util.getRange();
        Util.PUSH_NOTIFICIATIONS = defaultSharedPreferences.getBoolean("push", true);
        Util.IM_ALERT = defaultSharedPreferences.getBoolean("alert", true);

        nearByFragment = new NearByFragment();
        chatsFragment = new ChatsFragment();

        binding.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (position == 1 && (chatsFragment.adapter==null || chatsFragment.adapter.getItemCount()==1)) {
                    chatsFragment.loadData();
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        Util.PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(Util.PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        int registeredVersion = prefs.getInt(Util.PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    private boolean isUserRegistered(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String User_name = prefs.getString(Util.USER_NAME, "");
        if (User_name.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return false;
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void performClickAction(int position) {

        //occupants_ids
        QBRequestGetBuilder builder = new QBRequestGetBuilder();
        final QBLocation qbLocation = adapter.getItem(position);
        builder.eq("occupants_ids", qbLocation.getUser().getId());


        QBPrivateChatManager chatManager = QBChatService.getInstance().getPrivateChatManager();
        if (chatManager == null) return;
        chatManager.createDialog(qbLocation.getUser().getId(), new QBEntityCallback<QBDialog>() {

            @Override
            public void onSuccess(QBDialog result, Bundle params) {
                openProfile(result, qbLocation.getUser());
            }

            @Override
            public void onError(QBResponseException e) {
                Util.onError(e, MainActivity.this);
            }

        });


    }

    private void openProfile(QBDialog dialog, QBUser user) {

        Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
        intent.putExtra(ChatActivity.DIALOG, dialog);
        intent.putExtra(C.QB_USERID, user.getId());
        startActivity(intent);
    }

    @Override
    protected void onStop() {
        if (QBChatService.isInitialized()) {
            if (QBChatService.getInstance() != null && QBChatService.getInstance().getPrivateChatManager() != null && chatListener != null) {
                QBChatService.getInstance().getPrivateChatManager().removePrivateChatManagerListener(chatListener);
            }
        }
        super.onStop();

    }

    @Override
    public void onQBConnect(boolean isActivityReopened) throws Exception {
//        QBChatService.getInstance().getPrivateChatManager().addPrivateChatManagerListener(chatListener);
        //  loadAndSetNearUsers();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final int currentUserId = prefs.getInt(C.QB_USERID, 0);
        QBUsers.getUser(currentUserId, new QBEntityCallback<QBUser>() {
            @Override
            public void onSuccess(QBUser user, Bundle bundle) {
                if (user.getFileId() != null) {
                    float d = getResources().getDisplayMetrics().density;
                    int size = Math.round(d * 80);
                    AvatarLoaderHelper.loadImage(user.getFileId(), binding.currentUserAvatar, size, size);
                    binding.setUsername(Util.getUserName(user));
                    currentQBUser=user;
                }

            }

            @Override
            public void onError(QBResponseException e) {
                Util.onError(e, MainActivity.this);
            }
        });

        binding.viewPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));
        binding.tabLayout.setupWithViewPager(binding.viewPager);

        if (!isUserRegistered(context)) {
            if (checkPlayServices()) {
                gcm = GoogleCloudMessaging.getInstance(this);
                regid = getRegistrationId(context);

                if (regid.isEmpty()) {
                    registerInBackground();
                } else {
                    sendRegistrationIdToBackend(regid);
                }


            } else {
                Log.i("MAIN", "No valid Google Play Services APK found.");
            }
        }


    }


    /**
     * Substitute you own sender ID here. This is the project number you got
     * from the API Console, as described in "Getting Started."
     */

    private void registerInBackground() {
        new AsyncTask<Void, Void, String>() {


            @Override
            protected String doInBackground(Void[] params) {


                try {

                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(MainActivity.this);
                    }
                    regid = gcm.register(Util.SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;


                    // You should send the registration ID to your server over HTTP,
                    //GoogleCloudMessaging gcm;/ so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    // sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device
                    // will send upstream messages to a server that echo back the
                    // message using the 'from' address in the message.

                    // Persist the registration ID - no need to register again.
                    storeRegistrationId(context, regid);

                    return regid;

                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;


            }

            @Override
            protected void onPostExecute(String s) {
                sendRegistrationIdToBackend(regid);
            }
        }.execute();

    }


    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            Util.APP_VERSION = String.valueOf("PawPads Version: " + packageInfo.versionName + "." + packageInfo.versionCode);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Util.PROPERTY_REG_ID, regId);
        editor.putInt(Util.PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }


    private SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the registration ID in your app is up to you.
        return getSharedPreferences(MainActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }


    //  private RequestQueue mRequestQueue;
    private void sendRegistrationIdToBackend(String registrationID) {
        String deviceId = Build.MANUFACTURER + " " + Build.MODEL;

        QBMessages.subscribeToPushNotificationsTask(registrationID, deviceId, QBEnvironment.DEVELOPMENT, new QBEntityCallbackImpl<ArrayList<QBSubscription>>() {
            @Override
            public void onSuccess(ArrayList<QBSubscription> subscriptions, Bundle args) {
                //Log.d(LOG_TAG, "subscribed");
            }

            @Override
            public void onError(QBResponseException responseException) {
                Util.onError(responseException, MainActivity.this);
            }


        });
    }


    /**
     * Handle the result of a request for permissions.
     * <p/>
     * Watches for the result of a request for permission to use fine location (GPS) data.
     * If the request was granted, continue processing.
     * If the request was denied, stop; the application needs location data to work and cannot be
     * used without permission to use location data.
     *
     * @param requestCode  The ID of the permissions request.
     * @param permissions  The permissions that were requested.
     * @param grantResults The grant or denial for each requested permission.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case GPS.PermissionRequestId:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // TODO continue processing
                    android.util.Log.i(this.toString(), "ACCESS_FINE_LOCATION was granted");
                } else {
                    // TODO stop login
                    android.util.Log.w(this.toString(), "ACCESS_FINE_LOCATION was denied");
                    showGPSDisabledAlertToUser();
                }
                break;
            default:
                break;
        }
    }

    private void showGPSDisabledAlertToUser() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage("GPS is disabled in your device. Would you like to enable it?")
                .setCancelable(false)
                .setPositiveButton("Goto Settings Page To Enable GPS",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(callGPSSettingIntent);
                            }
                        });
        alertDialogBuilder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }


    private class ViewPagerAdapter extends FragmentPagerAdapter {

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
                return nearByFragment;
            } else {
                return chatsFragment;
            }

        }

        @Override
        public int getCount() {
            return 2;
        }


        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) {
                return getString(R.string.near_by);
            } else {
                return getString(R.string.chats);
            }
        }

    }


    public void editProfile() {
        binding.navigationDrawer.closeDrawer(Gravity.LEFT);
        Intent intent = new Intent(this, ProfileEditActivity.class);
        startActivity(intent);

    }

    public void openSettings() {
        binding.navigationDrawer.closeDrawer(Gravity.LEFT);
        startActivity(new Intent(this, PrefrenceActivity.class));
    }

    public void openAbout() {
        binding.navigationDrawer.closeDrawer(Gravity.LEFT);
        startActivity(new Intent(this, AboutActivity.class));

    }

    public void logout() {
        userLocalStore = new UserLocalStore(this);
        userLocalStore.clearUserData();
        userLocalStore.setUserLoggedIn(false);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();

    }

}


