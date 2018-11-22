/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Modified Work: Copyright (c) 2018 fr4nk1
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.josedlpozo.galileo.picker.overlays;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import com.josedlpozo.galileo.R;
import com.josedlpozo.galileo.picker.qs.GridQuickSettingsTile;
import com.josedlpozo.galileo.picker.qs.OnOffTileState;
import com.josedlpozo.galileo.picker.ui.DesignerTools;
import com.josedlpozo.galileo.picker.utils.ColorUtils;
import com.josedlpozo.galileo.picker.utils.PreferenceUtils;

public class GridOverlay extends Service {

    private static final int NOTIFICATION_ID = GridOverlay.class.hashCode();
    private static final String ACTION_HIDE_OVERLAY = "hide_grid_overlay";
    private static final String ACTION_SHOW_OVERLAY = "show_grid_overlay";
    private static final String NOTIFICATION_CHANNEL_ID = "com.josedlpozo.galileo";
    private WindowManager windowManager;
    private GridOverlayView overlayView;
    private WindowManager.LayoutParams params;

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();
        setup();
        DesignerTools.INSTANCE.setGridOverlayOn(this, true);
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (overlayView != null) {
            hideOverlay(() -> {
                removeViewIfAttached(overlayView);
                overlayView = null;
            });
        }
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        DesignerTools.INSTANCE.setGridOverlayOn(this,false);
    }

    private void setup() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                                                layoutFlag,
                                                 WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                                PixelFormat.TRANSLUCENT);
        overlayView = new GridOverlayView(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(overlayView, params);
        overlayView.setAlpha(0f);
        overlayView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                overlayView.animate().alpha(1f);
                overlayView.getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
            }
        });
        IntentFilter filter = new IntentFilter(GridQuickSettingsTile.ACTION_TOGGLE_STATE);
        filter.addAction(GridQuickSettingsTile.ACTION_UNPUBLISH);
        filter.addAction(ACTION_HIDE_OVERLAY);
        filter.addAction(ACTION_SHOW_OVERLAY);
        registerReceiver(mReceiver, filter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            startOreoForeground();
        } else {
            startForeground(NOTIFICATION_ID, getPersistentNotification(true));
        }
    }

    private void startOreoForeground(){
        String NOTIFICATION_CHANNEL_ID = "com.josedlpozo.galileo";
        String channelName = "My Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        startForeground(NOTIFICATION_ID, getPersistentNotification(true));
    }

    private void removeViewIfAttached(View v) {
        if (v.isAttachedToWindow()) {
            windowManager.removeView(v);
        }
    }

    private void updateNotification(boolean actionIsHide) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, getPersistentNotification(actionIsHide));
    }

    private Notification getPersistentNotification(boolean actionIsHide) {
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, new Intent(actionIsHide ? ACTION_HIDE_OVERLAY : ACTION_SHOW_OVERLAY), 0);
        Notification.Builder builder = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        String text = getString(actionIsHide ? R.string.notif_content_hide_grid_overlay : R.string.notif_content_show_grid_overlay);
        builder.setPriority(Notification.PRIORITY_MIN)
               .setSmallIcon(actionIsHide ? R.drawable.ic_qs_grid_on : R.drawable.ic_qs_grid_off)
               .setContentTitle(getString(R.string.grid_qs_tile_label))
               .setContentText(text)
               .setStyle(new Notification.BigTextStyle().bigText(text))
               .setContentIntent(pi);
        return builder.build();
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (GridQuickSettingsTile.ACTION_UNPUBLISH.equals(action)) {
                stopSelf();
            } else if (GridQuickSettingsTile.ACTION_TOGGLE_STATE.equals(action)) {
                int state = intent.getIntExtra(OnOffTileState.EXTRA_STATE, OnOffTileState.STATE_OFF);
                if (state == OnOffTileState.STATE_ON) {
                    stopSelf();
                }
            } else if (ACTION_HIDE_OVERLAY.equals(action)) {
                hideOverlay(() -> updateNotification(false));
            } else if (ACTION_SHOW_OVERLAY.equals(action)) {
                showOverlay();
            }
        }
    };

    private void showOverlay() {
        windowManager.addView(overlayView, params);
        updateNotification(true);
        overlayView.animate().alpha(1f);
    }

    private void hideOverlay(final Runnable endAction) {
        overlayView.animate().alpha(0f).withEndAction(() -> {
            overlayView.setAlpha(0f);
            removeViewIfAttached(overlayView);
            if (endAction != null) { endAction.run(); }
        });
    }

    static class GridOverlayView extends View {

        private Paint mGridPaint;
        private Paint mKeylinePaint;
        private RectF mFirstKeylineRect;
        private RectF mSecondKeylineRect;
        private RectF mThirdKeylineRect;
        private Drawable mHorizontalGridMarkerLeft;
        private Drawable mHorizontalMarkerLeft;
        private Drawable mHorizontalMarkerRight;
        private Drawable mVerticalMarker;
        private Rect mVerticalGridMarkerBounds;
        private Rect mHorizontalGridMarkerLeftBounds;
        private Rect mHorizontalGridMarkerRightBounds;
        private Rect mFirstKeylineMarkerBounds;
        private Rect mSecondKeylineMarkerBounds;
        private Rect mThirdKeylineMarkerBounds;
        private boolean mShowGrid = false;
        private boolean mShowKeylines = false;
        private float mGridLineWidth;
        private float mColumnSize;
        private float mRowSize;
        private float mDensity;
        private float mKeylineWidth;

        public GridOverlayView(Context context) {
            super(context);

            mDensity = getResources().getDisplayMetrics().density;
            mGridLineWidth = mDensity;
            mGridPaint = new Paint();
            mGridPaint.setColor(ColorUtils.getGridLineColor(context));
            mGridPaint.setStrokeWidth(mGridLineWidth);
            mKeylinePaint = new Paint();
            mKeylinePaint.setColor(ColorUtils.getKeylineColor(context));

            mHorizontalGridMarkerLeft = context.getDrawable(R.drawable.ic_marker_horiz_left).mutate();
            mHorizontalMarkerLeft = context.getDrawable(R.drawable.ic_marker_horiz_left);
            mHorizontalMarkerRight = context.getDrawable(R.drawable.ic_marker_horiz_right);
            mVerticalMarker = context.getDrawable(R.drawable.ic_marker_vert);

            mShowGrid = PreferenceUtils.GridPreferences.getShowGrid(context, false);
            mShowKeylines = PreferenceUtils.GridPreferences.getShowKeylines(context, false);

            boolean useCustom = PreferenceUtils.GridPreferences.getUseCustomGridSize(getContext(), false);
            int defColumnSize = getResources().getInteger(R.integer.galileo_default_column_size);
            int defRowSize = getResources().getInteger(R.integer.galileo_default_row_size);
            mColumnSize = mDensity * ( !useCustom ? defColumnSize : PreferenceUtils.GridPreferences.getGridColumnSize(getContext(), defColumnSize) );
            mRowSize = mDensity * ( !useCustom ? defRowSize : PreferenceUtils.GridPreferences.getGridRowSize(getContext(), defRowSize) );
            mKeylineWidth = 1.5f * mDensity;
        }

        @Override protected void onDraw(Canvas canvas) {
            drawGridLines(canvas);
            if (mShowKeylines) { drawKeylines(canvas); }

            drawGridMarkers(canvas);
            if (mShowKeylines) { drawKeylineMarkers(canvas); }
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            DisplayMetrics dm = getResources().getDisplayMetrics();

            int width = (int) ( 10 * dm.density );
            int height = (int) ( 6 * dm.density );
            int x = (int) ( 24 * dm.density );
            int y = 0;
            mVerticalGridMarkerBounds = new Rect(x, y, x + width, y + height);
            int temp = height;
            height = width;
            width = temp;
            x = 0;
            y = (int) ( 8 * dm.density );
            mHorizontalGridMarkerLeftBounds = new Rect(x, y, x + width, y + height);
            x = dm.widthPixels - ( width - 1 );
            mHorizontalGridMarkerRightBounds = new Rect(x, y, x + width, y + height);

            x = (int) ( 16 * dm.density );
            mFirstKeylineMarkerBounds = new Rect(x, y, x + width, y + height);
            x = (int) ( 72 * dm.density );
            mSecondKeylineMarkerBounds = new Rect(x, y, x + width, y + height);
            x = dm.widthPixels - (int) ( 16 * dm.density );
            mThirdKeylineMarkerBounds = new Rect(x, y, x + width, y + height);

            mFirstKeylineRect = new RectF(0, 0, 16 * dm.density, dm.heightPixels);
            mSecondKeylineRect = new RectF(56 * dm.density, 0, 72 * dm.density, dm.heightPixels);
            mThirdKeylineRect = new RectF(dm.widthPixels - 16 * dm.density, 0, dm.widthPixels, dm.heightPixels);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            SharedPreferences prefs = PreferenceUtils.getShardedPreferences(getContext());
            prefs.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            SharedPreferences prefs = PreferenceUtils.getShardedPreferences(getContext());
            prefs.unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);
        }

        private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if (PreferenceUtils.GridPreferences.KEY_SHOW_GRID.equals(key)) {
                        boolean enabled = prefs.getBoolean(PreferenceUtils.GridPreferences.KEY_SHOW_GRID, false);
                        if (mShowGrid != enabled) {
                            mShowGrid = enabled;
                            invalidate();
                        }
                    } else if (PreferenceUtils.GridPreferences.KEY_SHOW_KEYLINES.equals(key)) {
                        boolean enabled = prefs.getBoolean(PreferenceUtils.GridPreferences.KEY_SHOW_KEYLINES, false);
                        if (enabled != mShowKeylines) {
                            mShowKeylines = enabled;
                            invalidate();
                        }
                    } else if (PreferenceUtils.GridPreferences.KEY_GRID_COLUMN_SIZE.equals(key)) {
                        mColumnSize = mDensity * PreferenceUtils.GridPreferences.getGridColumnSize(getContext(), getResources().getInteger(
                            R.integer.galileo_default_column_size));
                        invalidate();
                    } else if (PreferenceUtils.GridPreferences.KEY_GRID_ROW_SIZE.equals(key)) {
                        mRowSize = mDensity * PreferenceUtils.GridPreferences.getGridRowSize(getContext(),
                                                                                             getResources().getInteger(R.integer.galileo_default_row_size));
                        invalidate();
                    } else if (PreferenceUtils.GridPreferences.KEY_GRID_LINE_COLOR.equals(key)) {
                        mGridPaint.setColor(ColorUtils.getGridLineColor(getContext()));
                        invalidate();
                    } else if (PreferenceUtils.GridPreferences.KEY_KEYLINE_COLOR.equals(key)) {
                        mKeylinePaint.setColor(ColorUtils.getKeylineColor(getContext()));
                        invalidate();
                    } else if (PreferenceUtils.GridPreferences.KEY_USE_CUSTOM_GRID_SIZE.equals(key)) {
                        boolean useCustom = PreferenceUtils.GridPreferences.getUseCustomGridSize(getContext(), false);
                        int defColumnSize = getResources().getInteger(R.integer.galileo_default_column_size);
                        int defRowSize = getResources().getInteger(R.integer.galileo_default_row_size);
                        mColumnSize = mDensity * ( !useCustom ?
                                                   defColumnSize :
                                                   PreferenceUtils.GridPreferences.getGridColumnSize(getContext(), defColumnSize) );
                        mRowSize = mDensity * ( !useCustom ? defRowSize : PreferenceUtils.GridPreferences.getGridRowSize(getContext(), defRowSize) );
                        invalidate();
                    }
                }
            };

        private void drawGridLines(Canvas canvas) {
            final int width = getWidth();
            final int height = getHeight();

            for (float x = 0; x < width; x += mColumnSize) {
                canvas.drawLine(x, 0, x, height - 1, mGridPaint);
            }
            for (float y = 0; y < height; y += mRowSize) {
                canvas.drawLine(0, y, width - 1, y, mGridPaint);
            }
        }

        private void drawGridMarkers(Canvas canvas) {
            mVerticalMarker.setTint(mGridPaint.getColor());
            mVerticalMarker.setBounds(mVerticalGridMarkerBounds);
            mVerticalMarker.draw(canvas);
            mHorizontalGridMarkerLeft.setTint(mGridPaint.getColor());
            mHorizontalGridMarkerLeft.setBounds(mHorizontalGridMarkerLeftBounds);
            mHorizontalGridMarkerLeft.draw(canvas);
            mHorizontalMarkerRight.setTint(mGridPaint.getColor());
            mHorizontalMarkerRight.setBounds(mHorizontalGridMarkerRightBounds);
            mHorizontalMarkerRight.draw(canvas);
        }

        private void drawKeylines(Canvas canvas) {
            final int height = getHeight();

            int alpha = mKeylinePaint.getAlpha();
            // draw rects first
            mKeylinePaint.setAlpha((int) ( 0.5f * alpha ));
            canvas.drawRect(mFirstKeylineRect, mKeylinePaint);
            canvas.drawRect(mSecondKeylineRect, mKeylinePaint);
            canvas.drawRect(mThirdKeylineRect, mKeylinePaint);

            // draw lines next
            mKeylinePaint.setAlpha(alpha);
            float stroke = mKeylinePaint.getStrokeWidth();
            mKeylinePaint.setStrokeWidth(mKeylineWidth);
            canvas.drawLine(mFirstKeylineRect.right, 0, mFirstKeylineRect.right, height, mKeylinePaint);
            canvas.drawLine(mSecondKeylineRect.right, 0, mSecondKeylineRect.right, height, mKeylinePaint);
            canvas.drawLine(mThirdKeylineRect.left, 0, mThirdKeylineRect.left, height, mKeylinePaint);
            mKeylinePaint.setStrokeWidth(stroke);
        }

        private void drawKeylineMarkers(Canvas canvas) {
            mHorizontalMarkerLeft.setTint(mKeylinePaint.getColor());
            mHorizontalMarkerLeft.setBounds(mFirstKeylineMarkerBounds);
            mHorizontalMarkerLeft.draw(canvas);
            mHorizontalMarkerLeft.setBounds(mSecondKeylineMarkerBounds);
            mHorizontalMarkerLeft.draw(canvas);
            mHorizontalMarkerLeft.setBounds(mThirdKeylineMarkerBounds);
            mHorizontalMarkerLeft.draw(canvas);
        }
    }
}
