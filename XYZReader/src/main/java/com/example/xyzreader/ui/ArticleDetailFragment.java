package com.example.xyzreader.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.graphics.Palette;
import android.text.Html;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.google.android.gms.plus.PlusShare;

import java.util.List;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";
    private static final float PARALLAX_FACTOR = 1.25f;

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private int mMutedColor = 0xFF333333;
    private ObservableScrollView mScrollView;
    private DrawInsetsFrameLayout mDrawInsetsFrameLayout;
    private ColorDrawable mStatusBarColorDrawable;

    private int mTopInset;
    private View mPhotoContainerView;
    private ImageView mPhotoView;
    private int mScrollY;
    private boolean mIsCard = false;
    private int mStatusBarFullOpacityBottom;

    private FloatingActionButton fab;
    private android.support.v7.widget.Toolbar share_bar;
    private FrameLayout fab_container;
    private LinearLayout share_container;
    private Point animation_value;
    private ObjectAnimator animator;
    private boolean share_visible = false;
    private boolean fab_animation_started = false;

    private static int FAB_ANIM_TIME = 200;
    private static int CONTAINER_ANIM_TIME = 100;
    private static int CHILDREN_ANIM_TIME = 100;
    private static int CHILDREN_ANIM_DELAY = 50;

    private class AnimationProperties {
        public Point screen_size;
        public Point fab_size;
        public Point fab_position;
        public Point container_size;
    }

    private AnimationProperties orig;

    private ImageButton share_email;
    private ImageButton share_copy;
    private ImageButton share_google;
    private ImageButton share_facebook;
    private ImageButton share_twitter;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        mIsCard = getResources().getBoolean(R.bool.detail_is_card);
        mStatusBarFullOpacityBottom = getResources().getDimensionPixelSize(
                R.dimen.detail_card_top_margin);
        setHasOptionsMenu(true);
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);
        mDrawInsetsFrameLayout = (DrawInsetsFrameLayout)
                mRootView.findViewById(R.id.draw_insets_frame_layout);
        mDrawInsetsFrameLayout.setOnInsetsCallback(new DrawInsetsFrameLayout.OnInsetsCallback() {
            @Override
            public void onInsetsChanged(Rect insets) {
                mTopInset = insets.top;
            }
        });

        mScrollView = (ObservableScrollView) mRootView.findViewById(R.id.scrollview);
        mScrollView.setCallbacks(new ObservableScrollView.Callbacks() {
            @Override
            public void onScrollChanged() {
                mScrollY = mScrollView.getScrollY();
                getActivityCast().onUpButtonFloorChanged(mItemId, ArticleDetailFragment.this);
                mPhotoContainerView.setTranslationY((int) (mScrollY - mScrollY / PARALLAX_FACTOR));
                updateStatusBar();

                revertFabTransition();
            }
        });

        mPhotoView = (ImageView) mRootView.findViewById(R.id.photo);
        mPhotoContainerView = mRootView.findViewById(R.id.photo_container);

        mStatusBarColorDrawable = new ColorDrawable(0);

        fab = (FloatingActionButton) mRootView.findViewById(R.id.fab);
        share_bar = (android.support.v7.widget.Toolbar) mRootView.findViewById(R.id.share_bar);
        fab_container = (FrameLayout) mRootView.findViewById(R.id.fab_container);
        share_container = (LinearLayout) mRootView.findViewById(R.id.share_container);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startFabTransition();

            }
        });

        share_email = (ImageButton) mRootView.findViewById(R.id.share_email);
        share_copy = (ImageButton) mRootView.findViewById(R.id.share_copy);
        share_google = (ImageButton) mRootView.findViewById(R.id.share_google);
        share_facebook = (ImageButton) mRootView.findViewById(R.id.share_facebook);
        share_twitter = (ImageButton) mRootView.findViewById(R.id.share_twitter);

        bindViews();
        updateStatusBar();
        return mRootView;
    }

    private void revertFabTransition() {

        if(share_visible && !fab_animation_started) {

            Log.e("test", "Called revertFabTransition");

            fab_animation_started = true;

            AnimatorPath path = new AnimatorPath();
            path.moveTo(fab.getX(), orig.container_size.y - orig.fab_size.y);
            path.curveTo(
                    (orig.screen_size.x / 4) * 3 - orig.fab_size.y / 2, orig.container_size.y - orig.fab_size.y,
                    orig.fab_position.x, orig.container_size.y - orig.fab_size.y,
                    orig.fab_position.x, orig.fab_position.y
            );

            animator = ObjectAnimator.ofObject(this, null, new PathEvaluator(), path.getPoints().toArray());
            animator.setInterpolator(new DecelerateInterpolator());
            animator.setDuration(FAB_ANIM_TIME);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    PathPoint p = (PathPoint) animation.getAnimatedValue();

                    ViewGroup.LayoutParams params = fab_container.getLayoutParams();
                    params.height = (int) (orig.container_size.y - p.mY);
                    fab_container.setLayoutParams(params);

                    fab.setX(p.mX);
                    fab.setY(0);
                }
            });
            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    fab.setImageAlpha(255);
                    share_bar.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    fab_animation_started = false;
                    share_visible = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });

            for (int i = 0; i < share_container.getChildCount(); i++) {
                View v = share_container.getChildAt(i);
                v.animate().setInterpolator(new AccelerateDecelerateInterpolator()).scaleX(0).scaleY(0).setDuration(CHILDREN_ANIM_TIME).setStartDelay(i * CHILDREN_ANIM_DELAY/2);
            }

            fab.animate().setDuration(CONTAINER_ANIM_TIME).scaleX(1).scaleY(1).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    animator.start();
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });

        }

    }

    private void startFabTransition() {

        //Adapted this example https://github.com/saulmm/Curved-Fab-Reveal-Example
        //to replicate this animation (well, more or less): http://material-design.storage.googleapis.com/publish/material_v_4/material_ext_publish/0B8v7jImPsDi-clFldXpZMmNhM0U/components-buttons-fab-transition_toolbar_01.mp4

        if(!share_visible && !fab_animation_started) {

            fab_animation_started = true;
            share_visible = true;

            Display display = getActivity().getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            orig = new AnimationProperties();
            orig.screen_size = size;
            orig.fab_size = new Point(fab.getWidth(), fab.getHeight());
            orig.container_size = new Point(fab_container.getWidth(), fab_container.getHeight());
            orig.fab_position = new Point((int) fab.getX(), (int) fab.getY());
            AnimatorPath path = new AnimatorPath();
            path.moveTo(orig.fab_position.x, 0);
            path.curveTo(
                    orig.fab_position.x, orig.container_size.y - orig.fab_size.y,
                    (orig.screen_size.x / 4) * 3 - orig.fab_size.y / 2, orig.container_size.y - orig.fab_size.y,
                    orig.screen_size.x / 2 - orig.fab_size.y / 2, orig.container_size.y - orig.fab_size.y
            );

            animator = ObjectAnimator.ofObject(this, null, new PathEvaluator(), path.getPoints().toArray());

            animator.setInterpolator(new AccelerateInterpolator());
            animator.setDuration(FAB_ANIM_TIME);
            animator.start();

            fab.setImageAlpha(0);

            fab.animate().setStartDelay(FAB_ANIM_TIME).setDuration(CONTAINER_ANIM_TIME).setInterpolator(new AccelerateInterpolator()).scaleX(13f).scaleY(13f).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    share_bar.setScaleX(1);
                    share_bar.setScaleY(1);
                    share_bar.setVisibility(View.VISIBLE);
                    share_bar.setBackgroundColor(getResources().getColor(R.color.accent_material_light));

                    for (int i = 0; i < share_container.getChildCount(); i++) {
                        View v = share_container.getChildAt(i);
                        v.animate().setInterpolator(new AccelerateDecelerateInterpolator()).scaleX(1).scaleY(1).setDuration(CHILDREN_ANIM_TIME).setStartDelay(i * CHILDREN_ANIM_DELAY);
                    }

                    fab_animation_started = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });

            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {

                    PathPoint p = (PathPoint) animation.getAnimatedValue();

                    ViewGroup.LayoutParams params = fab_container.getLayoutParams();
                    params.height = (int) (orig.container_size.y - p.mY);
                    fab_container.setLayoutParams(params);

                    fab.setX(p.mX);
                    fab.setY(0);

                }
            });

        }

    }

    private void updateStatusBar() {
        int color = 0;
        if (mPhotoView != null && mTopInset != 0 && mScrollY > 0) {
            float f = progress(mScrollY,
                    mStatusBarFullOpacityBottom - mTopInset * 3,
                    mStatusBarFullOpacityBottom - mTopInset);
            color = Color.argb((int) (255 * f),
                    (int) (Color.red(mMutedColor) * 0.9),
                    (int) (Color.green(mMutedColor) * 0.9),
                    (int) (Color.blue(mMutedColor) * 0.9));
        }
        if(getActivity() != null) getActivity().getWindow().setStatusBarColor(color);
        mStatusBarColorDrawable.setColor(color);
        mDrawInsetsFrameLayout.setInsetBackground(mStatusBarColorDrawable);
    }

    static float progress(float v, float min, float max) {
        return constrain((v - min) / (max - min), 0, 1);
    }

    static float constrain(float val, float min, float max) {
        if (val < min) {
            return min;
        } else if (val > max) {
            return max;
        } else {
            return val;
        }
    }

    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        final TextView titleView = (TextView) mRootView.findViewById(R.id.article_title);
        final TextView bylineView = (TextView) mRootView.findViewById(R.id.article_byline);
        bylineView.setMovementMethod(new LinkMovementMethod());
        TextView bodyView = (TextView) mRootView.findViewById(R.id.article_body);

        if (mCursor != null) {
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1);
            titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            bylineView.setText(Html.fromHtml(
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString()
                            + " by "
                            + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            bodyView.setText(Html.fromHtml(mCursor.getString(ArticleLoader.Query.BODY)));
            ImageLoaderHelper.getInstance(getActivity()).getImageLoader()
                    .get(mCursor.getString(ArticleLoader.Query.PHOTO_URL), new ImageLoader.ImageListener() {
                        @Override
                        public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b) {
                            Bitmap bitmap = imageContainer.getBitmap();
                            if (bitmap != null) {
                                Palette p = Palette.generate(bitmap, 12);
                                mMutedColor = p.getDarkMutedColor(0xFF333333);
                                mPhotoView.setImageBitmap(imageContainer.getBitmap());
                                mRootView.findViewById(R.id.meta_bar)
                                        .setBackgroundColor(mMutedColor);
                                titleView.setTextColor(p.getDarkMutedSwatch().getTitleTextColor());
                                bylineView.setTextColor(p.getDarkMutedSwatch().getTitleTextColor());
                                updateStatusBar();
                            }
                        }

                        @Override
                        public void onErrorResponse(VolleyError volleyError) {

                        }
                    });

            share_email.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("message/rfc822");
                    i.putExtra(Intent.EXTRA_SUBJECT, mCursor.getString(ArticleLoader.Query.TITLE));
                    i.putExtra(Intent.EXTRA_TEXT, "Check out this cool article [insert generic deep link]");
                    startActivity(Intent.createChooser(i, "Send Email"));
                    revertFabTransition();
                }
            });

            share_copy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData data = ClipData.newPlainText(mCursor.getString(ArticleLoader.Query.TITLE), "generic deep link");
                    clipboard.setPrimaryClip(data);
                    revertFabTransition();
                    Toast.makeText(getActivity(), "Copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            });

            share_google.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent shareIntent = new PlusShare.Builder(getActivity())
                            .setType("text/plain")
                            .setText("Check out this cool article")
                            .setContentUrl(Uri.parse("https://wwww.google.com"))
                            .getIntent();
                    startActivity(shareIntent);
                    revertFabTransition();
                }
            });

            share_facebook.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(getActivity(), "Too lazy to implement this", Toast.LENGTH_SHORT).show();
                    revertFabTransition();
                }
            });

            share_twitter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    //Credit http://stackoverflow.com/a/14317559

                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.putExtra(Intent.EXTRA_TEXT, "Check out this cool article [insert generic deep link]");
                    i.setType("text/plain");

                    PackageManager packManager = getActivity().getPackageManager();
                    List<ResolveInfo> resolvedInfoList = packManager.queryIntentActivities(i,  PackageManager.MATCH_DEFAULT_ONLY);

                    boolean resolved = false;
                    for(ResolveInfo resolveInfo: resolvedInfoList) {
                        if(resolveInfo.activityInfo.packageName.startsWith("com.twitter.android")){
                            i.setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name );
                            resolved = true;
                            break;
                        }
                    }
                    if(resolved) {
                        startActivity(i);
                    } else {
                        i = new Intent();
                        i.putExtra(Intent.EXTRA_TEXT, "Check out this cool article [insert generic deep link]");
                        i.setAction(Intent.ACTION_VIEW);
                        i.setData(Uri.parse("https://twitter.com/intent/tweet?text=message&via=profileName"));
                        startActivity(i);
                    }

                    revertFabTransition();
                }
            });

        } else {
            mRootView.setVisibility(View.GONE);
            titleView.setText("N/A");
            bylineView.setText("N/A" );
            bodyView.setText("N/A");
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    public int getUpButtonFloor() {
        if (mPhotoContainerView == null || mPhotoView.getHeight() == 0) {
            return Integer.MAX_VALUE;
        }

        // account for parallax
        return mIsCard
                ? (int) mPhotoContainerView.getTranslationY() + mPhotoView.getHeight() - mScrollY
                : mPhotoView.getHeight() - mScrollY;
    }
}
