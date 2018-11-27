package com.githang.behavior;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.math.MathUtils;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * @author 黄浩杭 (msdx.android@qq.com)
 * @since 2017-10-25 4.2.3
 */
public class ScrollLayoutBehavior<V extends ViewGroup> extends CoordinatorLayout.Behavior<V> {

    public abstract static class ScrollLayoutCallback {
        public abstract void onStateChanged(@NonNull ViewGroup viewGroup, @State int oldState, @State int newState);

        public abstract void onSlide(@NonNull ViewGroup viewGroup, ScrollLayoutBehavior behavior, int position);
    }


    public static final int STATE_HIDDEN = 1;
    /**
     * 缩起状态（显示在底部）
     */
    public static final int STATE_COLLAPSED = 2;
    /**
     * 半展开状态
     */
    public static final int STATE_HALF_EXPANDED = 3;
    /**
     * 全展开状态
     */
    public static final int STATE_FULL_EXPANDED = 4;
    /**
     * 拖动状态
     */
    public static final int STATE_DRAGGING = 5;

    /**
     * 滑动松手后自然沉降的状态
     */
    public static final int STATE_SETTLING = 6;

    @IntDef({STATE_HIDDEN, STATE_COLLAPSED, STATE_HALF_EXPANDED, STATE_FULL_EXPANDED, STATE_DRAGGING, STATE_SETTLING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    @State
    private int mState = STATE_HIDDEN;

    /**
     * 收起时所显示的高度
     */
    private int mCollapsedHeight;
    /**
     * 半展开时所显示的高度
     */
    private int mHalfExpandedHeight;
    /**
     * 全展开时所显示的高度
     */
    private int mFullExpandedHeight;
    /**
     * 相对于收起时高度的偏移量
     */
    private int mCurrentOffset;

    private int mHalfExpandedSpace;

    private int mFullExpandedSpace;

    private int mCollapsedSpace;

    /**
     * 父布局的高度
     */
    private int mParentHeight;

    private WeakReference<V> mViewGroupRef;

    private WeakReference<View> mNestedScrollingChildRef;

    private WeakReference<View> mCollapsedChildRef;

    private WeakReference<View> mExpandedChildRef;

    private ScrollLayoutCallback mCallback;

    private VelocityTracker mVelocityTracker;

    int mActivePointerId;

    private int mInitialY;

    private int mMaxVelocity;

    private boolean mIgnoreEvents;

    private boolean mTouchingScrollingChild;

    private ViewDragHelper mViewDragHelper;

    private int mLastNestedScrollDy;

    private boolean mNestedScrolled = false;

    private int mBelowToId;
    private WeakReference<View> mBelowToViewRef;

    private final ViewDragHelper.Callback mDragCallback = new ViewDragHelper.Callback() {
        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (mState == STATE_DRAGGING) {
                return false;
            }
            if (mTouchingScrollingChild) {
                return false;
            }
            if (mState == STATE_FULL_EXPANDED && mActivePointerId == pointerId) {
                View scroll = mNestedScrollingChildRef.get();
                if (scroll != null && scroll.canScrollVertically(-1)) {
                    return false;
                }
            }
            return mViewGroupRef != null && mViewGroupRef.get() == child;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            dispatchOnSlide(top);
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING);
            }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int top;
            @State int targetState;
            int currentTop = releasedChild.getTop();
            if (yvel < 0) { // 上滑
                if (currentTop < mHalfExpandedSpace) { // 半展开上面
                    top = mFullExpandedSpace;
                    targetState = STATE_FULL_EXPANDED;
                } else {
                    top = mHalfExpandedSpace;
                    targetState = STATE_HALF_EXPANDED;
                }
            } else if (yvel == 0.f) {
                final int fullExpandedDistance = Math.abs(currentTop - mFullExpandedSpace);
                final int halfExpandedDistance = Math.abs(currentTop - mHalfExpandedSpace);
                if (fullExpandedDistance <= halfExpandedDistance) {
                    top = mFullExpandedSpace;
                    targetState = STATE_FULL_EXPANDED;
                } else {
                    final int collapsedDistance = Math.abs(currentTop - mCollapsedSpace);
                    if (halfExpandedDistance <= collapsedDistance) {
                        top = mHalfExpandedSpace;
                        targetState = STATE_HALF_EXPANDED;
                    } else {
                        top = mCollapsedSpace;
                        targetState = STATE_COLLAPSED;
                    }
                }
            } else { // 下滑
                if (currentTop < mHalfExpandedSpace) { // 半展开之上
                    top = mHalfExpandedSpace;
                    targetState = STATE_HALF_EXPANDED;
                } else {
                    top = mCollapsedSpace;
                    targetState = STATE_COLLAPSED;
                }
            }

            if (mViewDragHelper.settleCapturedViewAt(releasedChild.getLeft(), top)) {
                setStateInternal(STATE_SETTLING);
                ViewCompat.postOnAnimation(releasedChild,
                        new SettleRunnable(releasedChild, targetState));
            } else {
                setStateInternal(targetState);
            }
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return MathUtils.clamp(top, mFullExpandedSpace, mCollapsedSpace);
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return child.getLeft();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return mCollapsedSpace - mFullExpandedSpace;
        }
    };

    public ScrollLayoutBehavior() {
    }

    public ScrollLayoutBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray ta = context.obtainStyledAttributes(attrs,
                R.styleable.ScrollLayoutBehavior_Layout);
        mHalfExpandedSpace = ta.getDimensionPixelSize(
                R.styleable.ScrollLayoutBehavior_Layout_behavior_halfExpandedSpace, 0);
        mFullExpandedSpace = ta.getDimensionPixelSize(
                R.styleable.ScrollLayoutBehavior_Layout_behavior_fullExpandedSpace, 0);
        mBelowToId = ta.getResourceId(R.styleable.ScrollLayoutBehavior_Layout_behavior_belowTo, View.NO_ID);
        ta.recycle();

        ViewConfiguration configuration = ViewConfiguration.get(context);
        mMaxVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, V child, int layoutDirection) {
        if (ViewCompat.getFitsSystemWindows(parent) && !ViewCompat.getFitsSystemWindows(child)) {
            ViewCompat.setFitsSystemWindows(child, true);
        }
        int savedTop = child.getTop();
        parent.onLayoutChild(child, layoutDirection);

        mCollapsedChildRef = new WeakReference<>(child.getChildAt(0));
        mExpandedChildRef = new WeakReference<>(child.getChildAt(1));

        if (mState == STATE_COLLAPSED ||
                (mState == STATE_DRAGGING && savedTop == getCollapsedSpace())) {
            mExpandedChildRef.get().setVisibility(View.GONE);
        } else {
            mExpandedChildRef.get().setVisibility(View.VISIBLE);
        }

        mParentHeight = parent.getHeight();
        if (mBelowToId != View.NO_ID) {
            if (mBelowToViewRef == null || mBelowToViewRef.get() == null) {
                final View anchorView = parent.findViewById(mBelowToId);
                anchorView.bringToFront();
                mBelowToViewRef = new WeakReference<>(anchorView);
            }
            mFullExpandedSpace = mBelowToViewRef.get().getBottom();
        }

        mFullExpandedHeight = mParentHeight - mFullExpandedSpace;
        mHalfExpandedHeight = mParentHeight - mHalfExpandedSpace;

        final int padding = child.getPaddingTop() + child.getPaddingBottom();
        mCollapsedHeight = mCollapsedChildRef.get().getHeight() + padding;
        mCollapsedSpace = mParentHeight - mCollapsedHeight;

        if (mState == STATE_FULL_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, mFullExpandedSpace);
        } else if (mState == STATE_HALF_EXPANDED) {
            ViewCompat.offsetTopAndBottom(child, mHalfExpandedSpace);
        } else if (mState == STATE_COLLAPSED) {
            ViewCompat.offsetTopAndBottom(child, mCollapsedSpace);
        } else if (mState == STATE_DRAGGING || mState == STATE_SETTLING) {
            // 子View显示或隐藏时会回调该方法重新进行layout，所以需要恢复到之前的位移量
            ViewCompat.offsetTopAndBottom(child, savedTop - child.getTop());
        } else if (mState == STATE_HIDDEN) {
            ViewCompat.offsetTopAndBottom(child, mParentHeight - child.getTop());
        }

        mViewGroupRef = new WeakReference<>(child);
        mNestedScrollingChildRef = new WeakReference<>(findScrollingChild(child));

        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback);
        }
        if (mNestedScrollingChildRef.get() != null) {
            mNestedScrollingChildRef.get().scrollTo(0, 0);
        }
        return true;
    }

    private View findScrollingChild(View view) {
        if (ViewCompat.isNestedScrollingEnabled(view)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                View scrollingChild = findScrollingChild(group.getChildAt(i));
                if (scrollingChild != null) {
                    return scrollingChild;
                }
            }
        }
        return null;
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            mIgnoreEvents = true;
            return false;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mTouchingScrollingChild = false;
                mActivePointerId = MotionEvent.INVALID_POINTER_ID;
                if (mIgnoreEvents) {
                    mIgnoreEvents = false;
                    return false;
                }
                break;
            case MotionEvent.ACTION_DOWN:
                int initialX = (int) event.getX();
                mInitialY = (int) event.getY();
                View scroll = mNestedScrollingChildRef == null
                        ? null : mNestedScrollingChildRef.get();
                if (scroll != null && parent.isPointInChildBounds(scroll, initialX, mInitialY)) {
                    mActivePointerId = event.getPointerId(event.getActionIndex());
                    mTouchingScrollingChild = true;
                }
                mIgnoreEvents = mActivePointerId == MotionEvent.INVALID_POINTER_ID
                        && !parent.isPointInChildBounds(child, initialX, mInitialY);
                break;
        }
        if (!mIgnoreEvents && mViewDragHelper.shouldInterceptTouchEvent(event)) {
            return true;
        }
        View scroll = mNestedScrollingChildRef.get();
        return action == MotionEvent.ACTION_MOVE && scroll != null
                && !mIgnoreEvents && mState != STATE_DRAGGING
                && !parent.isPointInChildBounds(scroll, (int) event.getX(), (int) event.getY())
                && Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop();
    }

    @Override
    public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!child.isShown()) {
            return false;
        }

        int action = event.getActionMasked();
        if (mState == STATE_DRAGGING && action == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (mViewDragHelper == null) {
            mViewDragHelper = ViewDragHelper.create(parent, mDragCallback);
        }
        mViewDragHelper.processTouchEvent(event);

        if (action == MotionEvent.ACTION_DOWN) {
            reset();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        if (action == MotionEvent.ACTION_MOVE && !mIgnoreEvents) {
            if (Math.abs(mInitialY - event.getY()) > mViewDragHelper.getTouchSlop()) {
                mViewDragHelper.captureChildView(child, event.getPointerId(event.getActionIndex()));
            }
        }
        return !mIgnoreEvents;
    }

    private void reset() {
        mActivePointerId = ViewDragHelper.INVALID_POINTER;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child, @NonNull View directTargetChild, @NonNull View target, int axes) {
        mLastNestedScrollDy = 0;
        mNestedScrolled = false;
        return (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedPreScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child, @NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        View scrollingChild = mNestedScrollingChildRef.get();
        if (target != scrollingChild) {
            return;
        }
        int currentTop = child.getTop();
        int newTop = currentTop - dy;
        if (dy > 0) { //上滑
            if (newTop < mFullExpandedSpace) {
                consumed[1] = currentTop - mFullExpandedSpace;
                ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                setStateInternal(STATE_FULL_EXPANDED);
            } else {
                consumed[1] = dy;
                ViewCompat.offsetTopAndBottom(child, -dy);
                setStateInternal(STATE_DRAGGING);
            }
        } else if (dy < 0) { // 下滑
            if (!target.canScrollVertically(-1)) {
                if (newTop <= mCollapsedSpace) {
                    consumed[1] = dy;
                    ViewCompat.offsetTopAndBottom(child, -dy);
                    setStateInternal(STATE_DRAGGING);
                } else {
                    consumed[1] = currentTop - mCollapsedSpace;
                    ViewCompat.offsetTopAndBottom(child, -consumed[1]);
                    setStateInternal(STATE_COLLAPSED);
                }
            }
        }
        dispatchOnSlide(child.getTop());
        mLastNestedScrollDy = dy;
        mNestedScrolled = true;
    }

    private void dispatchOnSlide(int top) {
        if (top == getCollapsedSpace()) {
            mExpandedChildRef.get().setVisibility(View.GONE);
        } else {
            mExpandedChildRef.get().setVisibility(View.VISIBLE);
        }
        ViewGroup scrollLayout = mViewGroupRef.get();
        if (scrollLayout != null && mCallback != null) {
            mCallback.onSlide(scrollLayout, this, top);
        }
    }

    @Override
    public void onStopNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child, @NonNull View target) {
        if (child.getTop() == mFullExpandedSpace) {
            setStateInternal(STATE_FULL_EXPANDED);
            return;
        }

        if (mNestedScrollingChildRef == null || target != mNestedScrollingChildRef.get()
                || !mNestedScrolled) {
            return;
        }

        int top;
        int targetState;
        if (mLastNestedScrollDy > 0) { // 上滑
            if (child.getTop() < mHalfExpandedSpace) { // 半展开之上
                top = mFullExpandedSpace;
                targetState = STATE_FULL_EXPANDED;
            } else { //收起到半展开之间
                top = mHalfExpandedSpace;
                targetState = STATE_HALF_EXPANDED;
            }
        } else if (mLastNestedScrollDy == 0) {
            final int currentTop = child.getTop();
            final int fullExpandedDistance = Math.abs(currentTop - mFullExpandedSpace);
            final int halfExpandedDistance = Math.abs(currentTop - mHalfExpandedSpace);
            if (fullExpandedDistance <= halfExpandedDistance) {
                top = mFullExpandedSpace;
                targetState = STATE_FULL_EXPANDED;
            } else {
                final int collapsedDistance = Math.abs(currentTop - mCollapsedSpace);
                if (halfExpandedDistance <= collapsedDistance) {
                    top = mHalfExpandedSpace;
                    targetState = STATE_HALF_EXPANDED;
                } else {
                    top = mCollapsedSpace;
                    targetState = STATE_COLLAPSED;
                }
            }
        } else { // 下滑
            if (child.getTop() < mHalfExpandedSpace) { // 半展开之上
                top = mHalfExpandedSpace;
                targetState = STATE_HALF_EXPANDED;
            } else {
                top = mCollapsedSpace;
                targetState = STATE_COLLAPSED;
            }
        }

        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, targetState));
        } else {
            setStateInternal(targetState);
        }
        mNestedScrolled = false;
    }

    @Override
    public boolean onNestedPreFling(@NonNull CoordinatorLayout coordinatorLayout, @NonNull V child,
                                    @NonNull View target, float velocityX, float velocityY) {
        return target == mNestedScrollingChildRef.get()
                && (mState != STATE_FULL_EXPANDED
                || super.onNestedPreFling(coordinatorLayout, child, target, velocityX, velocityY));
    }

    private void setStateInternal(@State int state) {
        if (mState == state) {
            return;
        }
        if (state == STATE_COLLAPSED || state == STATE_HIDDEN) {
            mExpandedChildRef.get().setVisibility(View.GONE);
        } else {
            mExpandedChildRef.get().setVisibility(View.VISIBLE);
        }
        @State final int oldState = mState;
        mState = state;
        ViewGroup scrollLayout = mViewGroupRef.get();
        if (scrollLayout != null && mCallback != null) {
            mCallback.onStateChanged(scrollLayout, oldState, state);
        }
    }

    public void hide() {
        final int targetTop = mParentHeight;
        if (mViewGroupRef != null && mViewGroupRef.get() != null) {
            final ViewGroup child = mViewGroupRef.get();
            ViewCompat.offsetTopAndBottom(child, targetTop - child.getTop());
            setStateInternal(STATE_HIDDEN);
        }
    }

    public void showCollapsed() {
        final ViewGroup child = mViewGroupRef.get();
        ViewCompat.offsetTopAndBottom(child, mCollapsedSpace - child.getTop());
        setStateInternal(STATE_COLLAPSED);
    }

    public boolean isExpanded() {
        return mState == STATE_HALF_EXPANDED || mState == STATE_FULL_EXPANDED;
    }

    @State
    public int getState() {
        return mState;
    }

    public void setState(final @State int state) {
        if (state == mState) {
            return;
        }
        if (mViewGroupRef == null) {
            // 还未layout，先修改状态
            if (state == STATE_HIDDEN || state == STATE_COLLAPSED
                    || state == STATE_HALF_EXPANDED || state == STATE_FULL_EXPANDED) {
                mState = state;
            }
            return;
        }
        final V child = mViewGroupRef.get();
        if (child == null) {
            return;
        }
        ViewParent parent = child.getParent();
        if (parent != null && parent.isLayoutRequested() && ViewCompat.isAttachedToWindow(child)) {
            child.post(new Runnable() {
                @Override
                public void run() {
                    startSettlingAnimation(child, state);
                }
            });
        } else {
            startSettlingAnimation(child, state);
        }
    }

    void startSettlingAnimation(View child, int state) {
        int top;
        if (state == STATE_COLLAPSED) {
            top = mCollapsedSpace;
        } else if (state == STATE_HALF_EXPANDED) {
            top = mHalfExpandedSpace;
        } else if (state == STATE_FULL_EXPANDED) {
            top = mFullExpandedSpace;
        } else if (state == STATE_HIDDEN) {
            top = mParentHeight;
        } else {
            throw new IllegalArgumentException("Illegal state argument: " + state);
        }
        if (mViewDragHelper.smoothSlideViewTo(child, child.getLeft(), top)) {
            setStateInternal(STATE_SETTLING);
            ViewCompat.postOnAnimation(child, new SettleRunnable(child, state));
        } else {
            setStateInternal(state);
        }
    }

    public boolean isHidden() {
        return mState == STATE_HIDDEN;
    }

    public int getFullExpandedSpace() {
        return mFullExpandedSpace;
    }

    public void setFullExpandedSpace(int fullExpandedSpace) {
        mFullExpandedSpace = fullExpandedSpace;
    }

    public int getHalfExpandedSpace() {
        return mHalfExpandedSpace;
    }

    public int getCollapsedSpace() {
        return mCollapsedSpace;
    }

    public void setScrollLayoutCallback(ScrollLayoutCallback callback) {
        mCallback = callback;
    }

    private class SettleRunnable implements Runnable {

        private final View mView;

        @State
        private final int mTargetState;

        SettleRunnable(View view, @State int targetState) {
            mView = view;
            mTargetState = targetState;
        }

        @Override
        public void run() {
            if (mViewDragHelper != null && mViewDragHelper.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this);
            } else {
                setStateInternal(mTargetState);
            }
        }
    }


    @SuppressWarnings("unchecked")
    public static <V extends ViewGroup> ScrollLayoutBehavior<V> from(V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params)
                .getBehavior();
        if (!(behavior instanceof ScrollLayoutBehavior)) {
            throw new IllegalArgumentException(
                    "The view is not associated with ScrollLayoutBehavior");
        }
        return (ScrollLayoutBehavior<V>) behavior;
    }
}
