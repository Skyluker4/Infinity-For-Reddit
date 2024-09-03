package ml.docilealligator.infinityforreddit.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Named;

import ml.docilealligator.infinityforreddit.ActivityToolbarInterface;
import ml.docilealligator.infinityforreddit.AnyAccountAccessTokenAuthenticator;
import ml.docilealligator.infinityforreddit.FetchSubscribedThing;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.SelectThingReturnKey;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.asynctasks.InsertSubscribedThings;
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper;
import ml.docilealligator.infinityforreddit.customviews.slidr.Slidr;
import ml.docilealligator.infinityforreddit.databinding.ActivitySubredditSelectionBinding;
import ml.docilealligator.infinityforreddit.events.SwitchAccountEvent;
import ml.docilealligator.infinityforreddit.fragments.SubscribedSubredditsListingFragment;
import ml.docilealligator.infinityforreddit.subreddit.SubredditData;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.subscribeduser.SubscribedUserData;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

public class SubredditSelectionActivity extends BaseActivity implements ActivityToolbarInterface {

    public static final String EXTRA_SPECIFIED_ACCOUNT = "ESA";
    public static final String EXTRA_EXTRA_CLEAR_SELECTION = "EECS";

    private static final int SUBREDDIT_SEARCH_REQUEST_CODE = 0;
    private static final String INSERT_SUBSCRIBED_SUBREDDIT_STATE = "ISSS";
    private static final String FRAGMENT_OUT_STATE = "FOS";

    @Inject
    @Named("no_oauth")
    Retrofit mRetrofit;
    @Inject
    @Named("oauth")
    Retrofit mOauthRetrofit;
    @Inject
    RedditDataRoomDatabase mRedditDataRoomDatabase;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    @Named("current_account")
    SharedPreferences mCurrentAccountSharedPreferences;
    @Inject
    CustomThemeWrapper mCustomThemeWrapper;
    @Inject
    Executor mExecutor;
    private String mAccountProfileImageUrl;
    private boolean mInsertSuccess = false;
    private Fragment mFragment;
    private ActivitySubredditSelectionBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Infinity) getApplication()).getAppComponent().inject(this);

        super.onCreate(savedInstanceState);

        binding = ActivitySubredditSelectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EventBus.getDefault().register(this);

        applyCustomTheme();

        if (mSharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_RIGHT_TO_GO_BACK, true)) {
            Slidr.attach(this);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Window window = getWindow();

            if (isChangeStatusBarIconColor()) {
                addOnOffsetChangedListener(binding.appbarLayoutSubredditSelectionActivity);
            }

            if (isImmersiveInterface()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                } else {
                    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                }
                adjustToolbar(binding.toolbarSubredditSelectionActivity);
            }
        }

        setSupportActionBar(binding.toolbarSubredditSelectionActivity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getIntent().hasExtra(EXTRA_SPECIFIED_ACCOUNT)) {
            Account specifiedAccount = getIntent().getParcelableExtra(EXTRA_SPECIFIED_ACCOUNT);
            if (specifiedAccount != null) {
                accessToken = specifiedAccount.getAccessToken();
                accountName = specifiedAccount.getAccountName();
                mAccountProfileImageUrl = specifiedAccount.getProfileImageUrl();

                mOauthRetrofit = mOauthRetrofit.newBuilder().client(new OkHttpClient.Builder().authenticator(new AnyAccountAccessTokenAuthenticator(mRetrofit, mRedditDataRoomDatabase, specifiedAccount, mCurrentAccountSharedPreferences))
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                        .build())
                        .build();
            } else {
                mAccountProfileImageUrl = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, null);
            }
        } else {
            mAccountProfileImageUrl = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_IMAGE_URL, null);
        }

        if (savedInstanceState == null) {
            bindView(true);
        } else {
            mInsertSuccess = savedInstanceState.getBoolean(INSERT_SUBSCRIBED_SUBREDDIT_STATE);

            mFragment = getSupportFragmentManager().getFragment(savedInstanceState, FRAGMENT_OUT_STATE);
            getSupportFragmentManager().beginTransaction().replace(R.id.frame_layout_subreddit_selection_activity, mFragment).commit();
            bindView(false);
        }
    }

    @Override
    public SharedPreferences getDefaultSharedPreferences() {
        return mSharedPreferences;
    }

    @Override
    public SharedPreferences getCurrentAccountSharedPreferences() {
        return mCurrentAccountSharedPreferences;
    }

    @Override
    public CustomThemeWrapper getCustomThemeWrapper() {
        return mCustomThemeWrapper;
    }

    @Override
    protected void applyCustomTheme() {
        binding.getRoot().setBackgroundColor(mCustomThemeWrapper.getBackgroundColor());
        applyAppBarLayoutAndCollapsingToolbarLayoutAndToolbarTheme(binding.appbarLayoutSubredditSelectionActivity,
                binding.collapsingToolbarLayoutSubredditSelectionActivity, binding.toolbarSubredditSelectionActivity);
    }

    private void bindView(boolean initializeFragment) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        loadSubscriptions();

        if (initializeFragment) {
            mFragment = new SubscribedSubredditsListingFragment();
            Bundle bundle = new Bundle();
            bundle.putString(SubscribedSubredditsListingFragment.EXTRA_ACCOUNT_PROFILE_IMAGE_URL, mAccountProfileImageUrl);
            bundle.putBoolean(SubscribedSubredditsListingFragment.EXTRA_IS_SUBREDDIT_SELECTION, true);
            if (getIntent().hasExtra(EXTRA_EXTRA_CLEAR_SELECTION)) {
                bundle.putBoolean(SubscribedSubredditsListingFragment.EXTRA_EXTRA_CLEAR_SELECTION,
                        getIntent().getExtras().getBoolean(EXTRA_EXTRA_CLEAR_SELECTION));
            }
            mFragment.setArguments(bundle);
            getSupportFragmentManager().beginTransaction().replace(R.id.frame_layout_subreddit_selection_activity, mFragment).commit();
        }
    }

    private void loadSubscriptions() {
        if (!mInsertSuccess) {
            FetchSubscribedThing.fetchSubscribedThing(mOauthRetrofit, accessToken, accountName, null,
                    new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>(),
                    new FetchSubscribedThing.FetchSubscribedThingListener() {
                        @Override
                        public void onFetchSubscribedThingSuccess(ArrayList<SubscribedSubredditData> subscribedSubredditData,
                                                                  ArrayList<SubscribedUserData> subscribedUserData,
                                                                  ArrayList<SubredditData> subredditData) {
                            InsertSubscribedThings.insertSubscribedThings(
                                    mExecutor,
                                    new Handler(),
                                    mRedditDataRoomDatabase,
                                    accountName,
                                    subscribedSubredditData,
                                    subscribedUserData,
                                    subredditData,
                                    () -> mInsertSuccess = true);
                        }

                        @Override
                        public void onFetchSubscribedThingFail() {
                            mInsertSuccess = false;
                        }
                    });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.subreddit_selection_activity, menu);
        applyMenuItemTheme(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_search_subreddit_selection_activity) {
            Intent intent = new Intent(this, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_SEARCH_ONLY_SUBREDDITS, true);
            startActivityForResult(intent, SUBREDDIT_SEARCH_REQUEST_CODE);
            return true;
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == SUBREDDIT_SEARCH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Intent returnIntent = new Intent();
                returnIntent.putExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME, data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_NAME));
                returnIntent.putExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_ICON, data.getStringExtra(SelectThingReturnKey.RETURN_EXTRA_SUBREDDIT_OR_USER_ICON));
                returnIntent.putExtra(SelectThingReturnKey.RETURN_EXTRA_THING_TYPE, SelectThingReturnKey.THING_TYPE.SUBREDDIT);
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        getSupportFragmentManager().putFragment(outState, FRAGMENT_OUT_STATE, mFragment);
        outState.putBoolean(INSERT_SUBSCRIBED_SUBREDDIT_STATE, mInsertSuccess);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onAccountSwitchEvent(SwitchAccountEvent event) {
        finish();
    }

    @Override
    public void onLongPress() {
        if (mFragment != null) {
            ((SubscribedSubredditsListingFragment) mFragment).goBackToTop();
        }
    }
}
